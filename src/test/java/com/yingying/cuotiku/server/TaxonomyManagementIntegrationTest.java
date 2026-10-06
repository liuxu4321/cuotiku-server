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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "BOOK_LOCAL_DIR=target/test-data-taxonomy2/storage",
        "app.jwt.secret=test-secret-key-for-integration-tests-0123456789abcdef",
        "app.admin.phone=13800000000",
        "app.admin.password=admin123456",
        "app.taxonomy.v2-enabled=true",
        "app.cos.bucket=",
        "app.ai.tencent.secret-id=",
        "app.ai.tencent.secret-key=",
        "app.cos.secret-id=",
        "app.cos.secret-key="
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TaxonomyManagementIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private CaptchaService captchaService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;

    private static String tokenA;
    private static String tokenB;
    private static Long subjectMathId;
    private static Long customSubjectId;
    private static Long topicId;
    private static int customRevision;

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

    private JsonNode subject(JsonNode list, String systemKey) {
        for (JsonNode s : list) {
            if (systemKey.equals(s.path("systemKey").asText())) return s;
        }
        return null;
    }

    @Test
    @Order(1)
    void lazyInitAndCreate() throws Exception {
        String adminToken = login("13800000000", "admin123456");
        exchange("/api/admin/users", HttpMethod.POST,
                Map.of("phone", "13910001111", "password", "test123456"), adminToken);
        exchange("/api/admin/users", HttpMethod.POST,
                Map.of("phone", "13910002222", "password", "test123456"), adminToken);
        tokenA = login("13910001111", "test123456");
        tokenB = login("13910002222", "test123456");

        // GET 惰性初始化六科，不激活
        JsonNode list = exchange("/api/v2/subjects", HttpMethod.GET, null, tokenA);
        assertEquals(0, list.path("code").asInt());
        assertEquals(6, list.path("data").size());
        assertNotNull(subject(list.path("data"), "math"));
        User userA = userRepository.findByPhone("13910001111").orElseThrow();
        assertNull(userA.getTaxonomyV2ActivatedAt(), "浏览/初始化不激活");

        // 创建自定义科目；重名（大小写/空白变体）拒绝
        JsonNode created = exchange("/api/v2/subjects", HttpMethod.POST,
                Map.of("name", "编程"), tokenA);
        assertEquals(0, created.path("code").asInt(), () -> "create failed: " + created);
        customSubjectId = created.path("data").path("id").asLong();
        customRevision = created.path("data").path("revision").asInt();
        assertEquals(70, created.path("data").path("sortOrder").asInt());
        assertEquals(4093, exchange("/api/v2/subjects", HttpMethod.POST,
                Map.of("name", "　编程  "), tokenA).path("code").asInt());

        // 双账号同名独立
        JsonNode createdB = exchange("/api/v2/subjects", HttpMethod.POST,
                Map.of("name", "编程"), tokenB);
        assertEquals(0, createdB.path("code").asInt());
        assertNotEquals(customSubjectId, createdB.path("data").path("id").asLong());

        // 跨账号访问 404
        assertEquals(404, exchange("/api/v2/subjects/" + customSubjectId, HttpMethod.PUT,
                Map.of("name", "黑客", "revision", 0), tokenB).path("code").asInt());

        // 激活：首次管理写成功后置位
        userA = userRepository.findByPhone("13910001111").orElseThrow();
        assertNotNull(userA.getTaxonomyV2ActivatedAt(), "管理写应激活账号");

        JsonNode list2 = exchange("/api/v2/subjects", HttpMethod.GET, null, tokenA);
        subjectMathId = subject(list2.path("data"), "math").path("id").asLong();
    }

    @Test
    @Order(2)
    void updateRevisionAndReorder() throws Exception {
        JsonNode list = exchange("/api/v2/subjects", HttpMethod.GET, null, tokenA);
        int revision = 0;
        for (JsonNode s : list.path("data")) {
            if (s.path("id").asLong() == customSubjectId) revision = s.path("revision").asInt();
        }
        assertEquals(customRevision, revision);

        // 旧 revision 冲突
        JsonNode conflict = exchange("/api/v2/subjects/" + customSubjectId, HttpMethod.PUT,
                Map.of("name", "程序设计", "revision", revision - 1), tokenA);
        assertEquals(4091, conflict.path("code").asInt());
        assertEquals(customSubjectId, conflict.path("data").path("id").asLong(), "冲突响应带最新实体");

        // 正常改名
        JsonNode renamed = exchange("/api/v2/subjects/" + customSubjectId, HttpMethod.PUT,
                Map.of("name", "程序设计", "revision", revision), tokenA);
        assertEquals(0, renamed.path("code").asInt());
        assertEquals("程序设计", renamed.path("data").path("name").asText());
        assertEquals(revision + 1, renamed.path("data").path("revision").asInt());

        // reorder 必须全量覆盖
        JsonNode partial = exchange("/api/v2/subjects/reorder", HttpMethod.PUT,
                Map.of("items", List.of(Map.of("id", customSubjectId, "revision", revision + 1))), tokenA);
        assertEquals(400, partial.path("code").asInt());

        // 全量 reorder 原子生效
        List<Map<String, Object>> items = new ArrayList<>();
        for (JsonNode s : list.path("data")) {
            long id = s.path("id").asLong();
            int rev = s.path("revision").asInt();
            if (id == customSubjectId) rev = revision + 1;
            items.add(Map.of("id", id, "revision", rev));
        }
        // 倒序提交
        List<Map<String, Object>> reversed = new ArrayList<>(items);
        java.util.Collections.reverse(reversed);
        JsonNode reordered = exchange("/api/v2/subjects/reorder", HttpMethod.PUT,
                Map.of("items", reversed), tokenA);
        assertEquals(0, reordered.path("code").asInt());
        assertEquals(reversed.get(0).get("id"), reordered.path("data").get(0).path("id").asLong());
    }

    @Test
    @Order(3)
    void topicsAndDeleteProtection() throws Exception {
        JsonNode topic = exchange("/api/v2/subjects/" + subjectMathId + "/topics", HttpMethod.POST,
                Map.of("name", "行程问题"), tokenA);
        assertEquals(0, topic.path("code").asInt(), () -> "topic create failed: " + topic);
        topicId = topic.path("data").path("id").asLong();
        assertEquals(4093, exchange("/api/v2/subjects/" + subjectMathId + "/topics", HttpMethod.POST,
                Map.of("name", "　行程问题 "), tokenA).path("code").asInt());

        // 有主题关联的科目不可删除
        JsonNode delSubject = exchange("/api/v2/subjects/" + subjectMathId, HttpMethod.DELETE, null, tokenA);
        assertEquals(4092, delSubject.path("code").asInt());
        assertEquals("存在关联数据，请改用停用", delSubject.path("message").asText());

        // 停用科目不改子主题状态
        JsonNode list = exchange("/api/v2/subjects", HttpMethod.GET, null, tokenA);
        int mathRev = 0;
        for (JsonNode s : list.path("data")) {
            if (s.path("id").asLong() == subjectMathId) mathRev = s.path("revision").asInt();
        }
        JsonNode disabled = exchange("/api/v2/subjects/" + subjectMathId, HttpMethod.PUT,
                Map.of("status", "DISABLED", "revision", mathRev), tokenA);
        assertEquals(0, disabled.path("code").asInt());
        JsonNode topics = exchange("/api/v2/subjects/" + subjectMathId + "/topics", HttpMethod.GET, null, tokenA);
        assertEquals("ACTIVE", topics.path("data").get(0).path("status").asText(), "科目停用不改写子主题状态");

        // 恢复科目后主题按原状态
        JsonNode enabled = exchange("/api/v2/subjects/" + subjectMathId, HttpMethod.PUT,
                Map.of("status", "ACTIVE", "revision", disabled.path("data").path("revision").asInt()), tokenA);
        assertEquals(0, enabled.path("code").asInt());

        // 无关联主题可删除；删除后科目仍可删（无题）
        JsonNode delTopic = exchange("/api/v2/topics/" + topicId, HttpMethod.DELETE, null, tokenA);
        assertEquals(0, delTopic.path("code").asInt());
    }

    @Test
    @Order(4)
    void managementNotBoundToAiMembership() throws Exception {
        // 普通无 AI 权益用户可管理个人分类（AC19）
        JsonNode list = exchange("/api/v2/subjects", HttpMethod.GET, null, tokenB);
        assertEquals(0, list.path("code").asInt());
        JsonNode created = exchange("/api/v2/subjects", HttpMethod.POST, Map.of("name", "美术"), tokenB);
        assertEquals(0, created.path("code").asInt());
    }
}
