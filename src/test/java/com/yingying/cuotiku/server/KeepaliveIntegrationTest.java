package com.yingying.cuotiku.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yingying.cuotiku.server.service.CaptchaService;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.TestPropertySource;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "BOOK_LOCAL_DIR=target/test-data-keepalive/storage",
        "app.jwt.secret=test-secret-key-for-integration-tests-0123456789abcdef",
        "app.admin.phone=13800000000",
        "app.admin.password=admin123456",
        "app.cos.bucket=",
        "app.ai.tencent.secret-id=",
        "app.ai.tencent.secret-key=",
        "app.cos.secret-id=",
        "app.cos.secret-key="
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KeepaliveIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private CaptchaService captchaService;
    @Autowired
    private ObjectMapper objectMapper;

    private static String userToken;
    private static String adminToken;
    private static final String clientId = UUID.randomUUID().toString();

    private JsonNode exchange(String path, HttpMethod method, Object body, String token) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        ResponseEntity<String> response = rest.exchange(path, method,
                new HttpEntity<>(body == null ? null : objectMapper.writeValueAsString(body), headers), String.class);
        return objectMapper.readTree(response.getBody());
    }

    private String login(String phone, String password) throws Exception {
        JsonNode captcha = exchange("/api/auth/captcha", HttpMethod.GET, null, null);
        String captchaId = captcha.path("data").path("captchaId").asText();
        String code = peekCaptchaCode(captchaId);
        JsonNode result = exchange("/api/auth/login", HttpMethod.POST,
                Map.of("phone", phone, "password", password, "captchaId", captchaId, "captchaCode", code), null);
        assertEquals(0, result.path("code").asInt(), () -> "login failed: " + result);
        return result.path("data").path("token").asText();
    }

    @SuppressWarnings("unchecked")
    private String peekCaptchaCode(String captchaId) throws Exception {
        Field storeField = CaptchaService.class.getDeclaredField("store");
        storeField.setAccessible(true);
        Map<String, Object> store = (Map<String, Object>) storeField.get(captchaService);
        Object entry = store.get(captchaId);
        Field codeField = entry.getClass().getDeclaredField("code");
        codeField.setAccessible(true);
        return (String) codeField.get(entry);
    }

    private JsonNode keepalive(String token, String cid) throws Exception {
        return exchange("/api/client/keepalive", HttpMethod.POST, Map.of(
                "clientId", cid,
                "appVersion", "1.2.3",
                "platform", "windows",
                "osVersion", "10.0.19045",
                "state", "idle",
                "detail", "workbench"), token);
    }

    @Test
    @Order(1)
    void anonymousReportCreatesTempRow() throws Exception {
        JsonNode result = keepalive(null, clientId);
        assertEquals(0, result.path("code").asInt(), () -> "keepalive failed: " + result);

        adminToken = login("13800000000", "admin123456");
        JsonNode list = exchange("/api/admin/keepalives", HttpMethod.GET, null, adminToken);
        assertEquals(0, list.path("code").asInt());
        JsonNode row = findRow(list, clientId);
        assertNotNull(row);
        assertFalse(row.path("loggedIn").asBoolean());
        assertTrue(row.path("phone").isNull());
        assertTrue(row.path("online").asBoolean());
        assertEquals(1, row.path("reportCount").asLong());
        assertEquals("windows", row.path("platform").asText());
    }

    @Test
    @Order(2)
    void loggedInReportUpdatesSameRow() throws Exception {
        adminToken = login("13800000000", "admin123456");
        String admin = adminToken;
        exchange("/api/admin/users", HttpMethod.POST,
                Map.of("phone", "13912345678", "password", "test123456"), admin);
        userToken = login("13912345678", "test123456");

        JsonNode result = keepalive(userToken, clientId);
        assertEquals(0, result.path("code").asInt());

        JsonNode list = exchange("/api/admin/keepalives", HttpMethod.GET, null, adminToken);
        JsonNode row = findRow(list, clientId);
        assertNotNull(row);
        assertTrue(row.path("loggedIn").asBoolean());
        assertEquals("13912345678", row.path("phone").asText());
        assertEquals(2, row.path("reportCount").asLong(), "同一客户端应只保留一行并累加计数");
        assertEquals(1, list.path("data").path("total").asLong());
    }

    @Test
    @Order(3)
    void invalidTokenFallsBackToAnonymous() throws Exception {
        JsonNode result = keepalive("invalid.token.value", clientId);
        assertEquals(0, result.path("code").asInt(), "心跳接口令牌无效时应降级匿名而非报错");

        JsonNode list = exchange("/api/admin/keepalives", HttpMethod.GET, null, adminToken);
        JsonNode row = findRow(list, clientId);
        assertFalse(row.path("loggedIn").asBoolean());
        assertEquals(3, row.path("reportCount").asLong());
    }

    @Test
    @Order(4)
    void adminQueryGuards() throws Exception {
        JsonNode anonymous = exchange("/api/admin/keepalives", HttpMethod.GET, null, null);
        assertEquals(401, anonymous.path("code").asInt());

        JsonNode asUser = exchange("/api/admin/keepalives", HttpMethod.GET, null, userToken);
        assertEquals(403, asUser.path("code").asInt());

        // 仅在线过滤：新客户端离线不存在 → 在线列表仍含刚上报的 clientId
        JsonNode online = exchange("/api/admin/keepalives?onlineOnly=true", HttpMethod.GET, null, adminToken);
        assertNotNull(findRow(online, clientId));

        JsonNode bad = exchange("/api/client/keepalive", HttpMethod.POST,
                Map.of("appVersion", "1.0"), null);
        assertEquals(400, bad.path("code").asInt());

        // 关键字查询：手机号 / 客户端ID / 无命中（先恢复登录态上报，单行记录以最近一次为准）
        assertEquals(0, keepalive(userToken, clientId).path("code").asInt());
        JsonNode byPhone = exchange("/api/admin/keepalives?keyword=13912345678", HttpMethod.GET, null, adminToken);
        assertNotNull(findRow(byPhone, clientId));
        JsonNode byCid = exchange("/api/admin/keepalives?keyword=" + clientId.substring(0, 8),
                HttpMethod.GET, null, adminToken);
        assertNotNull(findRow(byCid, clientId));
        JsonNode noHit = exchange("/api/admin/keepalives?keyword=no-such-client", HttpMethod.GET, null, adminToken);
        assertEquals(0, noHit.path("data").path("total").asLong());
    }

    @Test
    @Order(5)
    void adminPageHasKeepaliveMenu() {
        ResponseEntity<String> page = rest.getForEntity("/admin/index.html", String.class);
        assertEquals(HttpStatus.OK, page.getStatusCode());
        assertTrue(page.getBody().contains("客户端管理"));
    }

    private JsonNode findRow(JsonNode listResponse, String cid) {
        for (JsonNode row : listResponse.path("data").path("items")) {
            if (cid.equals(row.path("clientId").asText())) return row;
        }
        return null;
    }
}
