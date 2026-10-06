package com.yingying.cuotiku.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yingying.cuotiku.server.entity.BookEntry;
import com.yingying.cuotiku.server.repository.BookEntryRepository;
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

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "BOOK_LOCAL_DIR=target/test-data-bookv2/storage",
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
class BookV2IntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private CaptchaService captchaService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private BookEntryRepository entryRepository;
    @Autowired
    private UserRepository userRepository;

    private static String tokenA;
    private static String tokenB;
    private static Long mathId;
    private static Long englishId;
    private static Long topicId;
    private static Long disabledTopicId;
    private static final List<String> okEntryIds = new ArrayList<>();

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

    private static String pngBase64() throws Exception {
        BufferedImage image = new BufferedImage(200, 120, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 200, 120);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    private Map<String, Object> item(String clientId, Long subjectId, Long topicId) throws Exception {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("clientId", clientId);
        map.put("grade", 7);
        map.put("term", 1);
        map.put("subjectId", subjectId);
        map.put("topicId", topicId);
        map.put("imageBase64", pngBase64());
        map.put("errorType", "马虎");
        return map;
    }

    @Test
    @Order(1)
    void setup() throws Exception {
        String adminToken = login("13800000000", "admin123456");
        exchange("/api/admin/users", HttpMethod.POST,
                Map.of("phone", "13920001111", "password", "test123456"), adminToken);
        exchange("/api/admin/users", HttpMethod.POST,
                Map.of("phone", "13920002222", "password", "test123456"), adminToken);
        tokenA = login("13920001111", "test123456");
        tokenB = login("13920002222", "test123456");

        JsonNode subjects = exchange("/api/v2/subjects", HttpMethod.GET, null, tokenA);
        for (JsonNode s : subjects.path("data")) {
            if ("math".equals(s.path("systemKey").asText())) mathId = s.path("id").asLong();
            if ("english".equals(s.path("systemKey").asText())) englishId = s.path("id").asLong();
        }
        JsonNode topic = exchange("/api/v2/subjects/" + mathId + "/topics", HttpMethod.POST,
                Map.of("name", "行程问题"), tokenA);
        topicId = topic.path("data").path("id").asLong();
        JsonNode disabled = exchange("/api/v2/subjects/" + mathId + "/topics", HttpMethod.POST,
                Map.of("name", "停用主题"), tokenA);
        disabledTopicId = disabled.path("data").path("id").asLong();
        exchange("/api/v2/topics/" + disabledTopicId, HttpMethod.PUT,
                Map.of("status", "DISABLED", "revision", 0), tokenA);

        JsonNode subjectsB = exchange("/api/v2/subjects", HttpMethod.GET, null, tokenB);
        assertNotNull(subjectsB);
    }

    @Test
    @Order(2)
    void batchAddWithIdempotency() throws Exception {
        String requestId = UUID.randomUUID().toString();
        List<Map<String, Object>> items = List.of(
                item(UUID.randomUUID().toString(), mathId, topicId),
                item(UUID.randomUUID().toString(), mathId, topicId),
                item(UUID.randomUUID().toString(), englishId, null));
        JsonNode result = exchange("/api/v2/book/entries/batch", HttpMethod.POST,
                Map.of("requestId", requestId, "items", items), tokenA);
        assertEquals(0, result.path("code").asInt(), () -> "batch failed: " + result);
        JsonNode results = result.path("data").path("results");
        assertEquals(3, results.size());
        for (JsonNode r : results) {
            assertEquals("SUCCESS", r.path("status").asText());
            okEntryIds.add(r.path("entryId").asText());
        }

        // 双写旧名称列 + subjectId/topicId 落库
        BookEntry first = entryRepository.findById(okEntryIds.get(0)).orElseThrow();
        assertEquals("数学", first.getSubject());
        assertEquals(mathId, first.getSubjectId());
        assertEquals(topicId, first.getTopicId());
        BookEntry third = entryRepository.findById(okEntryIds.get(2)).orElseThrow();
        assertNull(third.getTopicId());

        // 激活
        assertNotNull(userRepository.findByPhone("13920001111").orElseThrow().getTaxonomyV2ActivatedAt());

        // 同 requestId+clientId 重试 → DUPLICATE，不重复落库
        JsonNode retry = exchange("/api/v2/book/entries/batch", HttpMethod.POST,
                Map.of("requestId", requestId, "items", items), tokenA);
        for (JsonNode r : retry.path("data").path("results")) {
            assertEquals("DUPLICATE", r.path("status").asText());
        }
        assertEquals(3, entryRepository.countByUserId(
                userRepository.findByPhone("13920001111").orElseThrow().getId()));
    }

    @Test
    @Order(3)
    void batchPartialFailureAndLimits() throws Exception {
        JsonNode subjectsB = exchange("/api/v2/subjects", HttpMethod.GET, null, tokenB);
        Long bMath = null;
        for (JsonNode s : subjectsB.path("data")) {
            if ("math".equals(s.path("systemKey").asText())) bMath = s.path("id").asLong();
        }
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(item(UUID.randomUUID().toString(), mathId, topicId));
        items.add(item(UUID.randomUUID().toString(), bMath, null));       // 他人科目 → 404
        items.add(item(UUID.randomUUID().toString(), mathId, disabledTopicId)); // 停用主题 → 400
        JsonNode result = exchange("/api/v2/book/entries/batch", HttpMethod.POST,
                Map.of("requestId", UUID.randomUUID().toString(), "items", items), tokenA);
        assertEquals(0, result.path("code").asInt());
        JsonNode results = result.path("data").path("results");
        assertEquals("SUCCESS", results.get(0).path("status").asText());
        assertEquals("FAILED", results.get(1).path("status").asText());
        assertEquals(404, results.get(1).path("code").asInt());
        assertEquals("FAILED", results.get(2).path("status").asText());
        assertEquals(400, results.get(2).path("code").asInt());
        okEntryIds.add(results.get(0).path("entryId").asText());

        // 超 20 题分块上限
        List<Map<String, Object>> tooMany = new ArrayList<>();
        for (int i = 0; i < 21; i += 1) {
            tooMany.add(item(UUID.randomUUID().toString(), mathId, null));
        }
        assertEquals(400, exchange("/api/v2/book/entries/batch", HttpMethod.POST,
                Map.of("requestId", UUID.randomUUID().toString(), "items", tooMany), tokenA)
                .path("code").asInt());
    }

    @Test
    @Order(4)
    void editThreeStateTopic() throws Exception {
        String entryId = okEntryIds.get(0);

        // 不传 topicId：保留原主题
        JsonNode keep = exchange("/api/v2/book/entries/" + entryId, HttpMethod.PUT,
                Map.of("remark", "仅改备注"), tokenA);
        assertEquals(0, keep.path("code").asInt());
        assertEquals(topicId.longValue(), keep.path("data").path("topicId").asLong());
        assertEquals("仅改备注", keep.path("data").path("remark").asText());

        // 显式 null：清空为未分类
        Map<String, Object> clear = new LinkedHashMap<>();
        clear.put("topicId", null);
        JsonNode cleared = exchange("/api/v2/book/entries/" + entryId, HttpMethod.PUT, clear, tokenA);
        assertEquals(0, cleared.path("code").asInt());
        assertTrue(cleared.path("data").path("topicId").isNull());

        // 换科目未显式 topicId → 400 冻结文案
        JsonNode noTopic = exchange("/api/v2/book/entries/" + entryId, HttpMethod.PUT,
                Map.of("subjectId", englishId), tokenA);
        assertEquals(400, noTopic.path("code").asInt());
        assertEquals("更换科目时必须显式指定主题或清空", noTopic.path("message").asText());

        // 换科目 + 显式 null → 成功且旧名称列同步
        Map<String, Object> movedBody = new LinkedHashMap<>();
        movedBody.put("subjectId", englishId);
        movedBody.put("topicId", null);
        JsonNode moved = exchange("/api/v2/book/entries/" + entryId, HttpMethod.PUT, movedBody, tokenA);
        assertEquals(0, moved.path("code").asInt());
        assertEquals("英语", moved.path("data").path("subject").asText());
        assertEquals(englishId.longValue(), moved.path("data").path("subjectId").asLong());

        // 跨科目主题 → 400
        JsonNode cross = exchange("/api/v2/book/entries/" + entryId, HttpMethod.PUT,
                Map.of("subjectId", mathId, "topicId", topicId), tokenA);
        assertEquals(0, cross.path("code").asInt());
        JsonNode crossBad = exchange("/api/v2/book/entries/" + okEntryIds.get(2), HttpMethod.PUT,
                Map.of("subjectId", englishId, "topicId", topicId), tokenA);
        assertEquals(400, crossBad.path("code").asInt());

        // grade 边界：12 通过、13 拒绝
        assertEquals(0, exchange("/api/v2/book/entries/" + entryId, HttpMethod.PUT,
                Map.of("grade", 12), tokenA).path("code").asInt());
        assertEquals(400, exchange("/api/v2/book/entries/" + entryId, HttpMethod.PUT,
                Map.of("grade", 13), tokenA).path("code").asInt());
    }

    private Map<String, Object> nullTopicItem(String entryId, Long subjectId) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("entryId", entryId);
        map.put("subjectId", subjectId);
        map.put("topicId", null);
        return map;
    }

    @Test
    @Order(5)
    void reclassifyAndOwnership() throws Exception {
        String entryId = okEntryIds.get(1);
        int practiceBefore = entryRepository.findById(entryId).orElseThrow().getPracticeCount();

        JsonNode result = exchange("/api/v2/book/entries/reclassify", HttpMethod.POST,
                Map.of("requestId", UUID.randomUUID().toString(),
                        "items", List.of(
                                nullTopicItem(entryId, englishId),
                                nullTopicItem("no-such-entry", englishId))),
                tokenA);
        assertEquals(0, result.path("code").asInt());
        assertEquals("SUCCESS", result.path("data").path("results").get(0).path("status").asText());
        assertEquals("FAILED", result.path("data").path("results").get(1).path("status").asText());
        assertEquals(404, result.path("data").path("results").get(1).path("code").asInt());

        // 归类变更不重置刷题次数
        assertEquals(practiceBefore, entryRepository.findById(entryId).orElseThrow().getPracticeCount());

        // 他人错题不可重新归类
        JsonNode mine = exchange("/api/v2/book/entries/reclassify", HttpMethod.POST,
                Map.of("requestId", UUID.randomUUID().toString(),
                        "items", List.of(nullTopicItem(entryId, mathId))),
                tokenB);
        assertEquals("FAILED", mine.path("data").path("results").get(0).path("status").asText());
        assertEquals(404, mine.path("data").path("results").get(0).path("code").asInt());
    }
}
