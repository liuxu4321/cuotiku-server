package com.yingying.cuotiku.server.mini;

import static com.yingying.cuotiku.server.mini.MiniSupport.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.yingying.cuotiku.server.entity.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用户反馈的提交与本人可见状态；内部备注不会返回客户端。 */
@Service
@Transactional
public class MiniFeedbackService {
  private final MiniSupport s;
  private final MiniIdempotency receipts;

  public MiniFeedbackService(MiniSupport s, MiniIdempotency receipts) {
    this.s = s;
    this.receipts = receipts;
  }

  public Object create(User user, JsonNode body) {
    String key = required(body, "clientRequestId", 64);
    return receipts.once(
        user,
        "FEEDBACK",
        key,
        body,
        () -> {
          UserFeedback row = new UserFeedback();
          row.setUserId(user.getId());
          row.setClientRequestId(key);
          row.setContent(required(body, "content", 2000));
          String version = text(body, "appVersion"), platform = text(body, "platform");
          if ((version != null && version.length() > 32)
              || (platform != null && platform.length() > 32))
            throw com.yingying.cuotiku.server.web.ApiException.badRequest("版本或平台字段最长32字符");
          row.setAppVersion(version);
          row.setPlatform(platform);
          s.save(row);
          return dto(row);
        });
  }

  @Transactional(readOnly = true)
  public Object list(User user, Map<String, String> query) {
    return s.page(
        UserFeedback.class, map("userId", user.getId()), query, "createdAt", true, this::dto);
  }

  public Object dto(UserFeedback row) {
    return map(
        "id",
        row.getId(),
        "content",
        row.getContent(),
        "status",
        row.getStatus(),
        "createdAt",
        row.getCreatedAt());
  }
}
