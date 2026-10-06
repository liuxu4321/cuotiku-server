package com.yingying.cuotiku.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yingying.cuotiku.server.entity.User;
import com.yingying.cuotiku.server.repository.UserRepository;
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
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "BOOK_LOCAL_DIR=target/test-data-gate/storage",
        "app.jwt.secret=test-secret-key-for-integration-tests-0123456789abcdef",
        "app.admin.phone=13800000000",
        "app.admin.password=admin123456",
        "app.taxonomy.v2-enabled=false",
        "app.cos.bucket=",
        "app.ai.tencent.secret-id=",
        "app.ai.tencent.secret-key=",
        "app.cos.secret-id=",
        "app.cos.secret-key="
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TaxonomyGateIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private CaptchaService captchaService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;

    private static String token;

    private JsonNode exchange(String path, HttpMethod method, Object body, String tk) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (tk != null) headers.setBearerAuth(tk);
        ResponseEntity<String> response = rest.exchange(path, method,
                new HttpEntity<>(body == null ? null : objectMapper.writeValueAsString(body), headers), String.class);
        return objectMapper.readTree(response.getBody());
    }

    private String login(String phone, String password) throws Exception {
        JsonNode captcha = exchange("/api/auth/captcha", HttpMethod.GET, null, null);
        String captchaId = captcha.path("data").path("captchaId").asText();
        Field storeField = CaptchaService.class.getDeclaredField("store");
        storeField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> store = (Map<String, Object>) storeField.get(captchaService);
        Object entry = store.get(captchaId);
        Field codeField = entry.getClass().getDeclaredField("code");
        codeField.setAccessible(true);
        String code = (String) codeField.get(entry);
        JsonNode result = exchange("/api/auth/login", HttpMethod.POST,
                Map.of("phone", phone, "password", password, "captchaId", captchaId, "captchaCode", code), null);
        assertEquals(0, result.path("code").asInt());
        return result.path("data").path("token").asText();
    }

    @Test
    @Order(1)
    void switchClosedBlocksUnactivatedWrites() throws Exception {
        String adminToken = login("13800000000", "admin123456");
        exchange("/api/admin/users", HttpMethod.POST,
                Map.of("phone", "13970001111", "password", "test123456"), adminToken);
        token = login("13970001111", "test123456");

        JsonNode caps = exchange("/api/v2/meta/capabilities", HttpMethod.GET, null, token);
        assertTrue(caps.path("data").path("taxonomyV2Supported").asBoolean());
        assertFalse(caps.path("data").path("taxonomyV2EntryOpen").asBoolean());
        assertFalse(caps.path("data").path("taxonomyV2Activated").asBoolean());

        // 读可用（惰性初始化），写 403
        assertEquals(0, exchange("/api/v2/subjects", HttpMethod.GET, null, token).path("code").asInt());
        JsonNode write = exchange("/api/v2/subjects", HttpMethod.POST, Map.of("name", "美术"), token);
        assertEquals(403, write.path("code").asInt());
        assertEquals("分类功能未开放", write.path("message").asText());

        // 偏好：读可用且不激活，写 403
        assertEquals(0, exchange("/api/v2/preferences", HttpMethod.GET, null, token).path("code").asInt());
        assertEquals(403, exchange("/api/v2/preferences", HttpMethod.PUT, Map.of(), token).path("code").asInt());
        assertNull(userRepository.findByPhone("13970001111").orElseThrow().getTaxonomyV2ActivatedAt(),
                "偏好读写不触发激活");
    }

    @Test
    @Order(2)
    void activatedKeepsWritesWhenSwitchClosed() throws Exception {
        User user = userRepository.findByPhone("13970001111").orElseThrow();
        user.setTaxonomyV2ActivatedAt(Instant.now());
        userRepository.save(user);

        JsonNode caps = exchange("/api/v2/meta/capabilities", HttpMethod.GET, null, token);
        assertTrue(caps.path("data").path("taxonomyV2Activated").asBoolean());

        JsonNode write = exchange("/api/v2/subjects", HttpMethod.POST, Map.of("name", "美术"), token);
        assertEquals(0, write.path("code").asInt(), "已激活账号在开关关闭后仍保留 v2 写");

        // v1 保护不恢复：add 仍 426
        JsonNode v1Add = exchange("/api/book/entries", HttpMethod.POST,
                Map.of("grade", 7, "subject", "数学", "imageBase64", "iVBORw0KGgo="), token);
        assertEquals(4026, v1Add.path("code").asInt());
    }
}
