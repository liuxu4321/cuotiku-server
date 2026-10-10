package com.yingying.cuotiku.server.mini;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yingying.cuotiku.server.web.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** 按微信实际text/plain响应验证协议适配，不依赖付费服务或真实凭据。 */
class WechatIdentityClientTest {
  @Test
  void acceptsOfficialTextPlainJsonWithoutExposingSessionKey() {
    RestClient.Builder builder = RestClient.builder().baseUrl("https://api.weixin.qq.com");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(
            requestTo(
                "https://api.weixin.qq.com/sns/jscode2session?appid=test-app&secret=test-secret&js_code=fresh-code&grant_type=authorization_code"))
        .andRespond(
            withSuccess(
                "{\"openid\":\"test-openid\",\"session_key\":\"private-value\"}",
                MediaType.TEXT_PLAIN));
    var client =
        new WechatIdentityClient("test-app", "test-secret", builder.build(), new ObjectMapper());
    var identity = client.exchange("fresh-code");
    assertEquals("test-openid", identity.openid());
    assertNull(identity.unionid());
    assertFalse(identity.toString().contains("private-value"));
    server.verify();
  }

  @Test
  void invalidCodeIsClientErrorAndMalformedBodyIsUpstreamError() {
    RestClient.Builder builder = RestClient.builder().baseUrl("https://api.weixin.qq.com");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(anything())
        .andRespond(
            withSuccess("{\"errcode\":40029,\"errmsg\":\"invalid code\"}", MediaType.TEXT_PLAIN));
    server.expect(anything()).andRespond(withSuccess("broken upstream body", MediaType.TEXT_PLAIN));
    var client =
        new WechatIdentityClient("test-app", "test-secret", builder.build(), new ObjectMapper());
    assertEquals(400, assertThrows(ApiException.class, () -> client.exchange("invalid")).getCode());
    assertEquals(502, assertThrows(ApiException.class, () -> client.exchange("broken")).getCode());
    server.verify();
  }
}
