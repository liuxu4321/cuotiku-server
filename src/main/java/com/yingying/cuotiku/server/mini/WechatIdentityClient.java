package com.yingying.cuotiku.server.mini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yingying.cuotiku.server.web.ApiException;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** 微信code仅发送到官方服务；session_key不存储、不返回客户端、不落日志。 */
@Component
public class WechatIdentityClient {
  private final String appId, secret;
  private final RestClient client;
  private final ObjectMapper json;

  @Autowired
  public WechatIdentityClient(
      @Value("${app.mini.wechat-app-id:}") String appId,
      @Value("${app.mini.wechat-secret:}") String secret,
      ObjectMapper json) {
    this(appId, secret, officialClient(), json);
  }

  WechatIdentityClient(String appId, String secret, RestClient client, ObjectMapper json) {
    this.appId = appId;
    this.secret = secret;
    this.client = client;
    this.json = json;
  }

  private static RestClient officialClient() {
    JdkClientHttpRequestFactory factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    factory.setReadTimeout(Duration.ofSeconds(15));
    return RestClient.builder()
        .baseUrl("https://api.weixin.qq.com")
        .requestFactory(factory)
        .build();
  }

  public boolean configured() {
    return !appId.isBlank() && !secret.isBlank();
  }

  public String appId() {
    return appId;
  }

  public record Identity(String openid, String unionid) {}

  public Identity exchange(String code) {
    if (!configured()) throw new ApiException(503, "后台尚未配置微信小程序登录");
    try {
      String payload =
          client
              .get()
              .uri(
                  builder ->
                      builder
                          .path("/sns/jscode2session")
                          .queryParam("appid", appId)
                          .queryParam("secret", secret)
                          .queryParam("js_code", code)
                          .queryParam("grant_type", "authorization_code")
                          .build())
              .retrieve()
              .body(String.class);
      // 微信官方返回text/plain JSON，先读取文本再解析，避免媒体类型匹配失败。
      JsonNode response = payload == null ? null : json.readTree(payload);
      if (response == null
          || response.path("errcode").asInt() != 0
          || response.path("openid").asText().isBlank())
        throw ApiException.badRequest("微信登录凭证无效，请重新登录");
      return new Identity(
          response.path("openid").asText(),
          response.has("unionid") ? response.path("unionid").asText() : null);
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw new ApiException(502, "微信身份服务暂不可用");
    }
  }
}
