package com.yingying.cuotiku.server.mini;

import static com.yingying.cuotiku.server.mini.MiniSupport.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.yingying.cuotiku.server.config.AppProperties;
import com.yingying.cuotiku.server.entity.*;
import com.yingying.cuotiku.server.security.JwtService;
import com.yingying.cuotiku.server.service.CaptchaService;
import com.yingying.cuotiku.server.web.ApiException;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 微信身份交换、会员绑定与会话发放。会员到期不删除账号或学生数据。 */
@Service
@Transactional
public class MiniAccountService {
  private final MiniSupport s;
  private final WechatIdentityClient wechat;
  private final JwtService jwt;
  private final CaptchaService captcha;
  private final PasswordEncoder passwords;
  private final AppProperties properties;
  private final boolean workerEnabled;

  public MiniAccountService(
      MiniSupport s,
      WechatIdentityClient wechat,
      JwtService jwt,
      CaptchaService captcha,
      PasswordEncoder passwords,
      AppProperties properties,
      @org.springframework.beans.factory.annotation.Value("${app.mini.worker-enabled:true}")
          boolean workerEnabled) {
    this.s = s;
    this.wechat = wechat;
    this.jwt = jwt;
    this.captcha = captcha;
    this.passwords = passwords;
    this.properties = properties;
    this.workerEnabled = workerEnabled;
  }

  @Transactional(readOnly = true)
  public Object capabilities() {
    return map(
        "contractVersion",
        "1.0.1",
        "serverVersion",
        properties.serverVersion(),
        "miniappApiSupported",
        true,
        "studentScopeSupported",
        true,
        "features",
        map(
            "capture",
            true,
            "processing",
            workerEnabled
                && properties.ai() != null
                && properties.ai().tencent() != null
                && properties.ai().tencent().configured(),
            "printing",
            workerEnabled,
            "paperDraft",
            true,
            "feedback",
            true,
            "wechatLogin",
            wechat.configured()),
        "limits",
        map(
            "maxUploadBytes",
            20971520,
            "maxPhotosPerBatch",
            30,
            "maxBatchItems",
            100,
            "maxPrintCopies",
            20));
  }

  public Object exchange(JsonNode body) {
    WechatIdentityClient.Identity identity = wechat.exchange(required(body, "code", 256));
    List<UserIdentity> rows =
        s.find(
            UserIdentity.class,
            map(
                "provider",
                "WECHAT_MINIAPP",
                "appId",
                wechat.appId(),
                "openid",
                identity.openid()));
    UserIdentity row = rows.isEmpty() ? new UserIdentity() : rows.get(0);
    if (row.getId() == null) {
      row.setProvider("WECHAT_MINIAPP");
      row.setAppId(wechat.appId());
      row.setOpenid(identity.openid());
      row.setUnionid(identity.unionid());
      s.save(row);
    }
    if ("DISABLED".equals(row.getStatus())) throw ApiException.forbidden("微信身份已停用");
    row.setLastLoginAt(Instant.now());
    String token = jwt.issueMiniIdentity(row.getId()).token();
    return map(
        "identityToken",
        token,
        "identityExpiresIn",
        300,
        "bound",
        row.getUserId() != null,
        "account",
        row.getUserId() == null ? null : account(s.get(User.class, row.getUserId())));
  }

  private UserIdentity identity(JsonNode body) {
    try {
      io.jsonwebtoken.Claims claims = jwt.parse(required(body, "identityToken", 8192));
      if (!"mini_identity".equals(claims.get("typ", String.class)))
        throw ApiException.unauthorized("微信身份凭证类型错误");
      UserIdentity row =
          s.em.find(UserIdentity.class, claims.getSubject(), LockModeType.PESSIMISTIC_WRITE);
      if (row == null || "DISABLED".equals(row.getStatus()))
        throw ApiException.unauthorized("微信身份无效");
      return row;
    } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
      throw ApiException.unauthorized("微信身份过期，请重新登录");
    }
  }

  public Object bind(JsonNode body) {
    UserIdentity identity = identity(body);
    if (!captcha.verifyAndConsume(
        required(body, "captchaId", 128), required(body, "captchaCode", 32)))
      throw ApiException.badRequest("验证码错误或已过期，请重新获取");
    String phone = required(body, "phone", 20);
    if (!phone.matches("1[0-9]{10}")) throw ApiException.badRequest("手机号格式错误");
    List<User> rows = s.find(User.class, map("phone", phone));
    if (rows.isEmpty()
        || !passwords.matches(required(body, "password", 128), rows.get(0).getPassword()))
      throw ApiException.unauthorized("手机号或密码错误");
    User user = rows.get(0);
    s.em.lock(user, LockModeType.PESSIMISTIC_WRITE);
    check(user);
    if (identity.getUserId() != null && !identity.getUserId().equals(user.getId()))
      throw ApiException.conflict("微信身份已绑定其他账号");
    identity.setUserId(user.getId());
    identity.setStatus("BOUND");
    if (identity.getBoundAt() == null) identity.setBoundAt(Instant.now());
    return tokens(user, body);
  }

  public Object login(JsonNode body) {
    UserIdentity identity = identity(body);
    if (identity.getUserId() == null || !"BOUND".equals(identity.getStatus()))
      throw ApiException.conflict("请先绑定会员账号");
    User user = s.em.find(User.class, identity.getUserId(), LockModeType.PESSIMISTIC_WRITE);
    if (user == null) throw ApiException.unauthorized("账号不存在");
    check(user);
    return tokens(user, body);
  }

  private void check(User user) {
    if (user.isCancelled()) throw ApiException.unauthorized("账号已注销");
    if (!user.isEnabled()) throw ApiException.forbidden("账号已停用");
  }

  private Object tokens(User user, JsonNode body) {
    JwtService.IssuedToken access = jwt.issueAccess(user.getPhone(), user.getRole().name()),
        refresh = jwt.issueRefresh(user.getPhone());
    user.setPrevSessionJti(user.getSessionJti());
    user.setPrevRefreshJti(user.getRefreshJti());
    user.setRotateReason("LOGIN");
    user.setRotatedAt(Instant.now());
    user.setSessionJti(access.jti());
    user.setRefreshJti(refresh.jti());
    user.setLastLoginAt(Instant.now());
    String label = text(body, "clientLabel");
    if (label != null) {
      if (label.length() > 100) throw ApiException.badRequest("clientLabel过长");
      user.setClientLabel(label);
    }
    return map(
        "token",
        access.token(),
        "expiresIn",
        jwt.ttlSeconds(),
        "refreshToken",
        refresh.token(),
        "refreshExpiresIn",
        jwt.refreshTtlSeconds(),
        "account",
        account(user));
  }

  @Transactional(readOnly = true)
  public Object me(User user) {
    Map<String, Object> account = account(user);
    return map(
        "account", account,
        "capabilities", map("aiEnabled", user.isAiEnabled(), "canReadOwnData", true),
        "lastStudentId", account.get("lastStudentId"));
  }

  public Map<String, Object> account(User user) {
    UserProfile profile = s.em.find(UserProfile.class, user.getId());
    return map(
        "id",
        user.getId().toString(),
        "memberName",
        user.getMemberName(),
        "memberNo",
        user.getMemberNo(),
        "memberExpireAt",
        user.getMemberExpireAt(),
        "memberActive",
        user.isMemberActive(),
        "role",
        user.getRole().name(),
        "aiEnabled",
        user.isAiEnabled(),
        "lastStudentId",
        profile == null ? null : profile.getLastStudentId());
  }

  @Transactional(readOnly = true)
  public Object content(String key) {
    oneOf(key, "help", "about");
    return map(
        "key",
        key,
        "title",
        key.equals("help") ? "使用帮助" : "关于拾星",
        "content",
        key.equals("help") ? "选择学生，拍照或导入照片，处理后保存错题或选择模板打印。" : "拾星错题本，记录错题，持续练习。",
        "version",
        "1");
  }
}
