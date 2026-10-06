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
        "BOOK_LOCAL_DIR=target/test-data-bookv2q/storage",
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
class BookV2QueryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private CaptchaService captchaService;
    @Autowired
    private ObjectMapper objectMapper;

    private static String token;
    private static Long mathId;
    private static Long englishId;
    private static Long topicTrip;
    private static Long topicDisabled;
    private static final List<String> mathTopicEntryIds = new ArrayList<>();
    private static String unclassifiedEntryId;

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
        BufferedImage image = new BufferedImage(160, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 160, 100);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    private Map<String, Object> item(Long subjectId, Long topicId, String errorType) throws Exception {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("clientId", UUID.randomUUID().toString());
        map.put("grade", 8);
        map.put("term", 2);
        map.put("subjectId", subjectId);
        map.put("topicId", topicId);
        map.put("imageBase64", pngBase64());
        map.put("errorType", errorType);
        return map;
    }

    @Test
    @Order(1)
    void setup() throws Exception {
        String adminToken = login("13800000000", "admin123456");
        exchange("/api/admin/users", HttpMethod.POST,
                Map.of("phone", "13930001111", "password", "test123456"), adminToken);
        token = login("13930001111", "test123456");

        JsonNode subjects = exchange("/api/v2/subjects", HttpMethod.GET, null, token);
        for (JsonNode s : subjects.path("data")) {
            if ("math".equals(s.path("systemKey").asText())) mathId = s.path("id").asLong();
            if ("english".equals(s.path("systemKey").asText())) englishId = s.path("id").asLong();
        }
        topicTrip = exchange("/api/v2/subjects/" + mathId + "/topics", HttpMethod.POST,
                Map.of("name", "行程问题"), token).path("data").path("id").asLong();
        topicDisabled = exchange("/api/v2/subjects/" + mathId + "/topics", HttpMethod.POST,
                Map.of("name", "停用主题"), token).path("data").path("id").asLong();

        List<Map<String, Object>> items = new ArrayList<>();
        items.add(item(mathId, topicTrip, "马虎"));
        items.add(item(mathId, topicTrip, "马虎"));
        items.add(item(mathId, null, "不会"));
        items.add(item(englishId, null, "马虎"));
        items.add(item(mathId, topicDisabled, "其他"));
        JsonNode batch = exchange("/api/v2/book/entries/batch", HttpMethod.POST,
                Map.of("requestId", UUID.randomUUID().toString(), "items", items), token);
        assertEquals(0, batch.path("code").asInt());
        JsonNode results = batch.path("data").path("results");
        mathTopicEntryIds.add(results.get(0).path("entryId").asText());
        mathTopicEntryIds.add(results.get(1).path("entryId").asText());
        unclassifiedEntryId = results.get(2).path("entryId").asText();

        // 停用主题（历史题保留）
        exchange("/api/v2/topics/" + topicDisabled, HttpMethod.PUT,
                Map.of("status", "DISABLED", "revision", 0), token);
        // 制造练习次数差异：第一条刷 2 次
        exchange("/api/book/entries/practice", HttpMethod.POST,
                Map.of("ids", List.of(mathTopicEntryIds.get(0))), token);
        exchange("/api/book/entries/practice", HttpMethod.POST,
                Map.of("ids", List.of(mathTopicEntryIds.get(0))), token);
    }

    @Test
    @Order(2)
    void listFiltersAndThreeState() throws Exception {
        JsonNode all = exchange("/api/v2/book/entries", HttpMethod.GET, null, token);
        assertEquals(5, all.path("data").path("total").asLong());

        JsonNode bySubject = exchange("/api/v2/book/entries?subjectId=" + mathId, HttpMethod.GET, null, token);
        assertEquals(4, bySubject.path("data").path("total").asLong());

        JsonNode byTopic = exchange("/api/v2/book/entries?topicId=" + topicTrip, HttpMethod.GET, null, token);
        assertEquals(2, byTopic.path("data").path("total").asLong());
        assertEquals("行程问题", byTopic.path("data").path("items").get(0).path("topicName").asText());
        assertEquals("ACTIVE", byTopic.path("data").path("items").get(0).path("topicStatus").asText());

        JsonNode unclassified = exchange("/api/v2/book/entries?unclassified=true", HttpMethod.GET, null, token);
        assertEquals(2, unclassified.path("data").path("total").asLong());
        for (JsonNode item : unclassified.path("data").path("items")) {
            assertTrue(item.path("topicId").isNull());
        }

        // 停用主题的历史题可查且带状态标识
        JsonNode disabled = exchange("/api/v2/book/entries?topicId=" + topicDisabled, HttpMethod.GET, null, token);
        assertEquals(1, disabled.path("data").path("total").asLong());
        assertEquals("DISABLED", disabled.path("data").path("items").get(0).path("topicStatus").asText());

        // 组合筛选 + 排序稳定（createdAt DESC, id ASC）
        JsonNode combo = exchange("/api/v2/book/entries?subjectId=" + mathId + "&errorType=马虎&term=2",
                HttpMethod.GET, null, token);
        assertEquals(2, combo.path("data").path("total").asLong());

        // 分页不漏项
        JsonNode page0 = exchange("/api/v2/book/entries?size=2&page=0", HttpMethod.GET, null, token);
        JsonNode page1 = exchange("/api/v2/book/entries?size=2&page=1", HttpMethod.GET, null, token);
        JsonNode page2 = exchange("/api/v2/book/entries?size=2&page=2", HttpMethod.GET, null, token);
        Set<String> seen = new HashSet<>();
        for (JsonNode p : List.of(page0, page1, page2)) {
            for (JsonNode item : p.path("data").path("items")) {
                assertTrue(seen.add(item.path("id").asText()), "分页出现重复项");
            }
        }
        assertEquals(5, seen.size());
    }

    @Test
    @Order(3)
    void randomRespectsScopeAndFairness() throws Exception {
        // 限定主题：只抽该主题题；最少练习优先
        JsonNode scoped = exchange("/api/v2/book/entries/random", HttpMethod.POST,
                Map.of("topicId", topicTrip, "counts", Map.of("马虎", 1)), token);
        assertEquals(0, scoped.path("code").asInt());
        assertEquals(1, scoped.path("data").path("selected").asInt());
        assertEquals(mathTopicEntryIds.get(1), scoped.path("data").path("items").get(0).path("id").asText(),
                "应抽练习次数最少的一条");

        // 未分类范围
        JsonNode unclassified = exchange("/api/v2/book/entries/random", HttpMethod.POST,
                Map.of("unclassified", true, "counts", Map.of("不会", 5)), token);
        assertEquals(1, unclassified.path("data").path("selected").asInt());
        assertEquals(unclassifiedEntryId, unclassified.path("data").path("items").get(0).path("id").asText());

        // 空池
        JsonNode empty = exchange("/api/v2/book/entries/random", HttpMethod.POST,
                Map.of("subjectId", englishId, "counts", Map.of("概念不清", 3)), token);
        assertEquals(0, empty.path("data").path("selected").asInt());
        assertEquals(0, empty.path("data").path("byType").path("概念不清").path("poolSize").asInt());
    }

    @Test
    @Order(4)
    void abilityGroupedBySubjectId() throws Exception {
        JsonNode ability = exchange("/api/v2/user/ability", HttpMethod.GET, null, token);
        assertEquals(0, ability.path("code").asInt());
        JsonNode subjects = ability.path("data").path("subjects");
        assertEquals(6, subjects.size(), "全部科目（含停用主题所属科目）");
        JsonNode math = null;
        for (JsonNode s : subjects) {
            if (s.path("subjectId").asLong() == mathId) math = s;
        }
        assertNotNull(math);
        assertEquals(4, math.path("sampleSize").asInt());
        assertEquals("ACTIVE", math.path("status").asText());
        assertEquals(5, ability.path("data").path("overall").path("sampleSize").asInt());

        // 按科目过滤 → 单科目模型
        JsonNode filtered = exchange("/api/v2/user/ability?subjectId=" + englishId, HttpMethod.GET, null, token);
        assertEquals(1, filtered.path("data").path("subjects").size());
        assertEquals(1, filtered.path("data").path("subjects").get(0).path("sampleSize").asInt());

        // 空样本科目
        for (JsonNode s : subjects) {
            if (s.path("subjectId").asLong() != mathId && s.path("subjectId").asLong() != englishId) {
                assertEquals(0, s.path("sampleSize").asInt());
                assertTrue(s.path("overall").isNull());
            }
        }
    }
}
