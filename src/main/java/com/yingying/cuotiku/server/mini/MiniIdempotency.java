package com.yingying.cuotiku.server.mini;

import static com.yingying.cuotiku.server.mini.MiniSupport.*;

import com.yingying.cuotiku.server.entity.*;
import com.yingying.cuotiku.server.web.ApiException;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/** 写接口在同一事务内锁定账号并写回执，保证跨学生重复请求键也被识别为冲突。 */
@Component
public class MiniIdempotency {
  private final MiniSupport s;

  public MiniIdempotency(MiniSupport s) {
    this.s = s;
  }

  public Object once(
      User user, String operation, String key, Object input, Supplier<Object> action) {
    if (key == null || key.isBlank() || key.length() > 128)
      throw ApiException.badRequest("幂等请求键不能为空且最长128字符");
    s.em.find(User.class, user.getId(), LockModeType.PESSIMISTIC_WRITE);
    String hash = s.hash(input);
    List<MiniRequestReceipt> rows =
        s.find(
            MiniRequestReceipt.class,
            map("userId", user.getId(), "operation", operation, "requestKey", key));
    if (!rows.isEmpty()) {
      MiniRequestReceipt row = rows.get(0);
      if (!hash.equals(row.getRequestHash())) throw ApiException.conflict("同一请求键对应不同输入");
      if (row.getExpiresAt().isBefore(Instant.now())) throw new ApiException(410, "幂等结果已过期，请重新操作");
      return s.decode(row.getResponseJson());
    }
    Object result = action.get();
    MiniRequestReceipt row = new MiniRequestReceipt();
    row.setId(UUID.randomUUID().toString());
    row.setUserId(user.getId());
    row.setOperation(operation);
    row.setRequestKey(key);
    row.setRequestHash(hash);
    row.setResponseJson(s.encode(result));
    row.setCreatedAt(Instant.now());
    row.setExpiresAt(Instant.now().plusSeconds(86400));
    s.save(row);
    return result;
  }
}
