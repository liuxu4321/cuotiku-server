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

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "BOOK_LOCAL_DIR=target/test-data/storage",
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
class ApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private CaptchaService captchaService;
    @Autowired
    private ObjectMapper objectMapper;

    private static String adminToken;
    private static Long createdUserId;

    private JsonNode post(String path, Object body, String token) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        ResponseEntity<String> response = rest.exchange(path, HttpMethod.POST,
                new HttpEntity<>(objectMapper.writeValueAsString(body), headers), String.class);
        return objectMapper.readTree(response.getBody());
    }

    private JsonNode get(String path, String token) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) headers.setBearerAuth(token);
        ResponseEntity<String> response = rest.exchange(path, HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        return objectMapper.readTree(response.getBody());
    }

    private JsonNode exchange(String path, HttpMethod method, Object body, String token) throws Exception {
        return exchangeInternal(path, method, body, token);
    }

    private JsonNode exchangeInternal(String path, HttpMethod method, Object body, String token) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        ResponseEntity<String> response = rest.exchange(path, method,
                new HttpEntity<>(body == null ? null : objectMapper.writeValueAsString(body), headers), String.class);
        return objectMapper.readTree(response.getBody());
    }

    private String login(String phone, String password) throws Exception {
        return loginFull(phone, password).path("token").asText();
    }

    private JsonNode loginFull(String phone, String password) throws Exception {
        JsonNode captcha = get("/api/auth/captcha", null);
        assertEquals(0, captcha.path("code").asInt());
        String captchaId = captcha.path("data").path("captchaId").asText();
        String code = peekCaptchaCode(captchaId);
        JsonNode result = post("/api/auth/login",
                Map.of("phone", phone, "password", password,
                        "captchaId", captchaId, "captchaCode", code, "clientLabel", "test"),
                null);
        assertEquals(0, result.path("code").asInt(), () -> "login failed: " + result);
        return result.path("data");
    }

    @SuppressWarnings("unchecked")
    private String peekCaptchaCode(String captchaId) throws Exception {
        Field storeField = CaptchaService.class.getDeclaredField("store");
        storeField.setAccessible(true);
        Map<String, Object> store = (Map<String, Object>) storeField.get(captchaService);
        Object entry = store.get(captchaId);
        assertNotNull(entry, "captcha entry missing");
        Field codeField = entry.getClass().getDeclaredField("code");
        codeField.setAccessible(true);
        return (String) codeField.get(entry);
    }

    @Test
    @Order(1)
    void captchaEndpointWorks() throws Exception {
        JsonNode captcha = get("/api/auth/captcha", null);
        assertEquals(0, captcha.path("code").asInt());
        assertFalse(captcha.path("data").path("captchaId").asText().isBlank());
        assertTrue(captcha.path("data").path("imageBase64").asText().startsWith("data:image/png;base64,"));
    }

    @Test
    @Order(2)
    void loginRejectsWrongCaptcha() throws Exception {
        JsonNode captcha = get("/api/auth/captcha", null);
        String captchaId = captcha.path("data").path("captchaId").asText();
        JsonNode result = post("/api/auth/login",
                Map.of("phone", "13800000000", "password", "admin123456",
                        "captchaId", captchaId, "captchaCode", "ZZZZ"),
                null);
        assertEquals(400, result.path("code").asInt());
    }

    @Test
    @Order(3)
    void adminLoginAndMe() throws Exception {
        JsonNode loginData = loginFull("13800000000", "admin123456");
        adminToken = loginData.path("token").asText();
        assertFalse(adminToken.isBlank());
        assertFalse(loginData.path("refreshToken").asText().isBlank());
        assertTrue(loginData.path("expiresIn").asLong() > 0);
        assertTrue(loginData.path("refreshExpiresIn").asLong() > loginData.path("expiresIn").asLong());
        JsonNode me = get("/api/auth/me", adminToken);
        assertEquals(0, me.path("code").asInt());
        assertEquals("13800000000", me.path("data").path("phone").asText());
        assertEquals("ADMIN", me.path("data").path("role").asText());
        assertTrue(me.path("data").path("aiEnabled").asBoolean());
        assertFalse(me.path("data").path("memberActive").asBoolean());
    }

    @Test
    @Order(4)
    void meRequiresToken() throws Exception {
        JsonNode me = get("/api/auth/me", null);
        assertEquals(401, me.path("code").asInt());
    }

    @Test
    @Order(5)
    void adminCreatesAndQueriesUser() throws Exception {
        JsonNode created = post("/api/admin/users",
                Map.of("phone", "13911112222", "memberNo", "VIP001",
                        "password", "test123456", "aiEnabled", true),
                adminToken);
        assertEquals(0, created.path("code").asInt(), () -> "create failed: " + created);
        createdUserId = created.path("data").path("id").asLong();
        assertEquals("13911112222", created.path("data").path("phone").asText());
        assertTrue(created.path("data").path("aiEnabled").asBoolean());
        // 未传有效期时默认一年
        String expireAt = created.path("data").path("memberExpireAt").asText();
        assertFalse(expireAt.isBlank());
        assertTrue(created.path("data").path("memberActive").asBoolean());
        assertEquals(java.time.LocalDate.now().plusYears(1).toString(), expireAt);

        JsonNode duplicate = post("/api/admin/users",
                Map.of("phone", "13911112222", "password", "test123456"), adminToken);
        assertEquals(409, duplicate.path("code").asInt());

        JsonNode list = get("/api/admin/users?keyword=139&page=0&size=10", adminToken);
        assertEquals(0, list.path("code").asInt());
        assertTrue(list.path("data").path("total").asLong() >= 1);
    }

    @Test
    @Order(6)
    void regularUserCannotAccessAdminApi() throws Exception {
        String userToken = login("13911112222", "test123456");
        JsonNode list = get("/api/admin/users", userToken);
        assertEquals(403, list.path("code").asInt());
    }

    @Test
    @Order(7)
    void singleSessionKickout() throws Exception {
        String firstToken = login("13911112222", "test123456");
        JsonNode me1 = get("/api/auth/me", firstToken);
        assertEquals(0, me1.path("code").asInt());

        String secondToken = login("13911112222", "test123456");
        assertNotEquals(firstToken, secondToken);

        JsonNode me2 = get("/api/auth/me", secondToken);
        assertEquals(0, me2.path("code").asInt());

        JsonNode kicked = get("/api/auth/me", firstToken);
        assertEquals(4011, kicked.path("code").asInt(), "old session should be kicked: " + kicked);
    }

    @Test
    @Order(8)
    void refreshTokenFlow() throws Exception {
        JsonNode loginData = loginFull("13911112222", "test123456");
        String oldAccess = loginData.path("token").asText();
        String refreshToken = loginData.path("refreshToken").asText();

        // refresh token 不能当 access token 用
        JsonNode misuse = get("/api/auth/me", refreshToken);
        assertEquals(401, misuse.path("code").asInt());

        // 正常刷新
        JsonNode refreshed = post("/api/auth/refresh", Map.of("refreshToken", refreshToken), null);
        assertEquals(0, refreshed.path("code").asInt(), () -> "refresh failed: " + refreshed);
        String newAccess = refreshed.path("data").path("token").asText();
        String newRefresh = refreshed.path("data").path("refreshToken").asText();
        assertNotEquals(refreshToken, newRefresh);
        assertEquals(0, get("/api/auth/me", newAccess).path("code").asInt());

        // 刷新轮换宽限期内：并发在途的旧访问令牌仍可用（不再误报"其他设备登录"）
        assertEquals(0, get("/api/auth/me", oldAccess).path("code").asInt());

        // 并发刷新竞态：旧 refresh token 在宽限期内重发当前会话令牌（不旋转）
        JsonNode reuse = post("/api/auth/refresh", Map.of("refreshToken", refreshToken), null);
        assertEquals(0, reuse.path("code").asInt(), () -> "grace refresh failed: " + reuse);
        assertEquals(0, get("/api/auth/me", reuse.path("data").path("token").asText()).path("code").asInt());
        // 首次刷新得到的令牌依然有效（未发生二次旋转）
        assertEquals(0, get("/api/auth/me", newAccess).path("code").asInt());

        // 他端登录后，旧客户端无法刷新（被踢出）
        login("13911112222", "test123456");
        JsonNode kickedRefresh = post("/api/auth/refresh", Map.of("refreshToken", newRefresh), null);
        assertEquals(401, kickedRefresh.path("code").asInt());
        JsonNode kickedAccess = get("/api/auth/me", oldAccess);
        assertEquals(4011, kickedAccess.path("code").asInt());

        // 伪造/无效 refresh token
        JsonNode bogus = post("/api/auth/refresh", Map.of("refreshToken", "abc.def.ghi"), null);
        assertEquals(401, bogus.path("code").asInt());
    }

    @Test
    @Order(9)
    void adminPasswordResetInvalidatesRefresh() throws Exception {
        JsonNode loginData = loginFull("13911112222", "test123456");
        String refreshToken = loginData.path("refreshToken").asText();
        exchange("/api/admin/users/" + createdUserId + "/password", HttpMethod.PUT,
                Map.of("password", "test123456"), adminToken);
        JsonNode result = post("/api/auth/refresh", Map.of("refreshToken", refreshToken), null);
        assertEquals(401, result.path("code").asInt());
    }

    @Test
    @Order(10)
    void aiEraseRequiresPermissionAndConfig() throws Exception {
        String userToken = login("13911112222", "test123456");
        JsonNode result = post("/api/ai/erase", Map.of("imageBase64", "aGVsbG8="), userToken);
        assertEquals(503, result.path("code").asInt(), "tencent credentials not configured in test");

        // 切边增强：有权限但未配置凭据 → 503；非法增强类型 → 400
        JsonNode crop = post("/api/ai/crop-enhance", Map.of("imageBase64", "aGVsbG8="), userToken);
        assertEquals(503, crop.path("code").asInt());
        JsonNode badEnhance = post("/api/ai/crop-enhance",
                Map.of("imageBase64", "aGVsbG8=", "enhanceType", 9), userToken);
        assertEquals(400, badEnhance.path("code").asInt());

        // 切题检测：有权限但未配置凭据 → 503 并记流水
        JsonNode split = post("/api/ai/split-questions", Map.of("imageBase64", "aGVsbG8="), userToken);
        assertEquals(503, split.path("code").asInt());

        // 试卷处理（三合一）：切边增强失败回退原图，切题上游 503 → 整体 503
        JsonNode paper = post("/api/ai/paper-process", Map.of("imageBase64", "aGVsbG8="), userToken);
        assertEquals(503, paper.path("code").asInt());
        // 关闭增强与切题 → 去手写 503
        JsonNode paperEraseOnly = post("/api/ai/paper-process",
                Map.of("imageBase64", "aGVsbG8=", "enhance", false, "split", false), userToken);
        assertEquals(503, paperEraseOnly.path("code").asInt());

        JsonNode update = exchange("/api/admin/users/" + createdUserId, HttpMethod.PUT,
                Map.of("aiEnabled", false), adminToken);
        assertEquals(0, update.path("code").asInt());

        String userToken2 = login("13911112222", "test123456");
        JsonNode forbidden = post("/api/ai/erase", Map.of("imageBase64", "aGVsbG8="), userToken2);
        assertEquals(403, forbidden.path("code").asInt());
        JsonNode cropForbidden = post("/api/ai/crop-enhance", Map.of("imageBase64", "aGVsbG8="), userToken2);
        assertEquals(403, cropForbidden.path("code").asInt());
        JsonNode splitForbidden = post("/api/ai/split-questions", Map.of("imageBase64", "aGVsbG8="), userToken2);
        assertEquals(403, splitForbidden.path("code").asInt());
        JsonNode paperForbidden = post("/api/ai/paper-process", Map.of("imageBase64", "aGVsbG8="), userToken2);
        assertEquals(403, paperForbidden.path("code").asInt());
    }

    @Test
    @Order(11)
    void adminUpdatesAndDeletesUser() throws Exception {
        JsonNode update = exchange("/api/admin/users/" + createdUserId, HttpMethod.PUT,
                Map.of("memberNo", "VIP002", "aiEnabled", true, "memberExpireAt", "2030-06-30"), adminToken);
        assertEquals(0, update.path("code").asInt());
        assertEquals("VIP002", update.path("data").path("memberNo").asText());
        assertEquals("2030-06-30", update.path("data").path("memberExpireAt").asText());
        assertTrue(update.path("data").path("memberActive").asBoolean());

        // 过期日期 → memberActive=false；非法格式 → 400
        JsonNode expired = exchange("/api/admin/users/" + createdUserId, HttpMethod.PUT,
                Map.of("memberExpireAt", "2020-01-01"), adminToken);
        assertEquals(0, expired.path("code").asInt());
        assertFalse(expired.path("data").path("memberActive").asBoolean());
        JsonNode badDate = exchange("/api/admin/users/" + createdUserId, HttpMethod.PUT,
                Map.of("memberExpireAt", "2030/06/30"), adminToken);
        assertEquals(400, badDate.path("code").asInt());
        exchange("/api/admin/users/" + createdUserId, HttpMethod.PUT,
                Map.of("memberExpireAt", "2030-06-30"), adminToken);

        JsonNode reset = exchange("/api/admin/users/" + createdUserId + "/password", HttpMethod.PUT,
                Map.of("password", "newpass123"), adminToken);
        assertEquals(0, reset.path("code").asInt());

        String token = login("13911112222", "newpass123");
        assertFalse(token.isBlank());

        JsonNode deleted = exchange("/api/admin/users/" + createdUserId, HttpMethod.DELETE, null, adminToken);
        assertEquals(0, deleted.path("code").asInt());

        JsonNode afterDelete = get("/api/auth/me", token);
        assertEquals(401, afterDelete.path("code").asInt());
    }

    @Test
    @Order(12)
    void adminCannotDeleteSelf() throws Exception {
        JsonNode me = get("/api/auth/me", adminToken);
        long adminId = 0;
        JsonNode list = get("/api/admin/users?keyword=13800000000", adminToken);
        for (JsonNode item : list.path("data").path("items")) {
            if ("13800000000".equals(item.path("phone").asText())) adminId = item.path("id").asLong();
        }
        assertTrue(adminId > 0);
        JsonNode result = exchange("/api/admin/users/" + adminId, HttpMethod.DELETE, null, adminToken);
        assertEquals(400, result.path("code").asInt());
        assertNotNull(me);
    }

    @Test
    @Order(13)
    void cancelMembershipClearsMemberInfo() throws Exception {
        JsonNode created = post("/api/admin/users",
                Map.of("phone", "13933334444", "memberNo", "VIP999",
                        "password", "test123456", "aiEnabled", true),
                adminToken);
        assertEquals(0, created.path("code").asInt());
        long uid = created.path("data").path("id").asLong();
        assertTrue(created.path("data").path("memberActive").asBoolean());

        String userToken = login("13933334444", "test123456");

        JsonNode cancelled = exchange("/api/admin/users/" + uid + "/membership", HttpMethod.DELETE, null, adminToken);
        assertEquals(0, cancelled.path("code").asInt());
        assertTrue(cancelled.path("data").path("memberNo").isNull());
        assertTrue(cancelled.path("data").path("memberExpireAt").isNull());
        assertFalse(cancelled.path("data").path("memberActive").asBoolean());
        assertFalse(cancelled.path("data").path("aiEnabled").asBoolean());
        assertTrue(cancelled.path("data").path("cancelled").asBoolean());

        // 注销立即踢下线：已有令牌 401
        JsonNode me = get("/api/auth/me", userToken);
        assertEquals(401, me.path("code").asInt());

        // 已注销用户登录失败，且与密码错误同响应（防探测）
        JsonNode captcha = get("/api/auth/captcha", null);
        String captchaId = captcha.path("data").path("captchaId").asText();
        JsonNode loginResult = post("/api/auth/login",
                Map.of("phone", "13933334444", "password", "test123456",
                        "captchaId", captchaId, "captchaCode", peekCaptchaCode(captchaId)),
                null);
        assertEquals(401, loginResult.path("code").asInt());
        assertEquals("手机号或密码错误", loginResult.path("message").asText());

        // 管理员重新设置会员信息 → 自动恢复，可再次登录
        JsonNode restored = exchange("/api/admin/users/" + uid, HttpMethod.PUT,
                Map.of("memberNo", "VIP888", "memberExpireAt", "2030-01-01"), adminToken);
        assertEquals(0, restored.path("code").asInt());
        assertFalse(restored.path("data").path("cancelled").asBoolean());
        String tokenAgain = login("13933334444", "test123456");
        assertFalse(tokenAgain.isBlank());

        // 不能注销管理员自己
        JsonNode adminList = get("/api/admin/users?keyword=13800000000", adminToken);
        long adminId = 0;
        for (JsonNode item : adminList.path("data").path("items")) {
            if ("13800000000".equals(item.path("phone").asText())) adminId = item.path("id").asLong();
        }
        assertTrue(adminId > 0);
        JsonNode selfCancel = exchange("/api/admin/users/" + adminId + "/membership", HttpMethod.DELETE, null, adminToken);
        assertEquals(400, selfCancel.path("code").asInt());

        exchange("/api/admin/users/" + uid, HttpMethod.DELETE, null, adminToken);
    }

    @Test
    @Order(15)
    void aiStatsQuery() throws Exception {
        String admin = login("13800000000", "admin123456");

        // Order(10) 中 13911112222 产生过 ERASE 503 与 CROP_ENHANCE 503 各一条流水
        JsonNode all = get("/api/admin/ai-stats", admin);
        assertEquals(0, all.path("code").asInt());
        assertTrue(all.path("data").path("summary").path("total").asLong() >= 2);
        assertTrue(all.path("data").path("summary").path("failed").asLong() >= 2);

        JsonNode erase = get("/api/admin/ai-stats?phone=13911112222&aiType=ERASE", admin);
        assertEquals(0, erase.path("code").asInt());
        assertTrue(erase.path("data").path("total").asLong() >= 1);
        assertEquals(0, erase.path("data").path("summary").path("success").asLong());
        assertEquals(erase.path("data").path("total").asLong(),
                erase.path("data").path("summary").path("failed").asLong());
        JsonNode first = erase.path("data").path("items").get(0);
        assertEquals(503, first.path("errorCode").asInt());
        assertFalse(first.path("traceId").asText().isBlank());
        assertEquals("13911112222", first.path("phone").asText());

        JsonNode crop = get("/api/admin/ai-stats?phone=13911112222&aiType=CROP_ENHANCE", admin);
        assertTrue(crop.path("data").path("total").asLong() >= 1);

        JsonNode splitStats = get("/api/admin/ai-stats?phone=13911112222&aiType=SPLIT_QUESTIONS", admin);
        assertTrue(splitStats.path("data").path("total").asLong() >= 1);
        assertEquals("SPLIT_QUESTIONS",
                splitStats.path("data").path("items").get(0).path("aiType").asText());

        // 时间范围过滤
        String today = java.time.LocalDate.now().toString();
        JsonNode ranged = get("/api/admin/ai-stats?start=" + today + "&end=" + today, admin);
        assertTrue(ranged.path("data").path("summary").path("total").asLong() >= 2);
        JsonNode old = get("/api/admin/ai-stats?end=2020-01-01", admin);
        assertEquals(0, old.path("data").path("summary").path("total").asLong());

        // 参数校验
        assertEquals(400, get("/api/admin/ai-stats?aiType=FOO", admin).path("code").asInt());
        assertEquals(400, get("/api/admin/ai-stats?start=2026/01/01", admin).path("code").asInt());

        // 普通用户无权访问
        JsonNode tmpUser = post("/api/admin/users",
                Map.of("phone", "13922223333", "password", "test123456"), admin);
        long tmpId = tmpUser.path("data").path("id").asLong();
        String userToken = login("13922223333", "test123456");
        assertEquals(403, get("/api/admin/ai-stats", userToken).path("code").asInt());
        exchange("/api/admin/users/" + tmpId, HttpMethod.DELETE, null, admin);
    }

    @Test
    @Order(15)
    void changeOwnPassword() throws Exception {
        adminToken = login("13800000000", "admin123456");
        JsonNode created = exchange("/api/admin/users", HttpMethod.POST,
                Map.of("phone", "13966660000", "password", "oldpass123"), adminToken);
        assertEquals(0, created.path("code").asInt(), () -> "create failed: " + created);
        String token = login("13966660000", "oldpass123");

        // 原密码错误
        JsonNode wrongOld = post("/api/auth/password",
                Map.of("oldPassword", "wrongpass", "newPassword", "newpass123"), token);
        assertEquals(400, wrongOld.path("code").asInt());
        // 新密码过短
        JsonNode shortNew = post("/api/auth/password",
                Map.of("oldPassword", "oldpass123", "newPassword", "123"), token);
        assertEquals(400, shortNew.path("code").asInt());
        // 新旧相同
        JsonNode same = post("/api/auth/password",
                Map.of("oldPassword", "oldpass123", "newPassword", "oldpass123"), token);
        assertEquals(400, same.path("code").asInt());

        // 成功修改：当前令牌失效，需重新登录
        JsonNode ok = post("/api/auth/password",
                Map.of("oldPassword", "oldpass123", "newPassword", "newpass123"), token);
        assertEquals(0, ok.path("code").asInt());
        assertEquals(401, get("/api/auth/me", token).path("code").asInt());
        String newToken = login("13966660000", "newpass123");
        assertFalse(newToken.isBlank());
        assertEquals(0, get("/api/auth/me", newToken).path("code").asInt());

        // 未登录不可改密
        assertEquals(401, post("/api/auth/password",
                Map.of("oldPassword", "x123456", "newPassword", "y123456"), null).path("code").asInt());
    }

    @Test
    @Order(14)
    void adminPageIsServed() {
        ResponseEntity<String> page = rest.getForEntity("/admin/index.html", String.class);
        assertEquals(HttpStatus.OK, page.getStatusCode());
        assertTrue(page.getBody().contains("拾星错题本后台管理"));
    }
}
