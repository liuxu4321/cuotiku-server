package com.yingying.cuotiku.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yingying.cuotiku.server.entity.AiCallLog;
import com.yingying.cuotiku.server.repository.AiCallLogRepository;
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
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "BOOK_LOCAL_DIR=target/test-data-agent/storage",
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
class AgentIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private CaptchaService captchaService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AiCallLogRepository callLogRepository;
    @Autowired
    private com.yingying.cuotiku.server.repository.AiAgentResultRepository agentResultRepository;

    private static String aiToken;
    private static String noAiToken;
    private static String adminToken;
    private static String entryId;
    private static Long aiUserId;

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

    private static String pngBase64() throws Exception {
        BufferedImage image = new BufferedImage(400, 240, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 400, 240);
        g.setColor(Color.BLACK);
        g.drawString("3 x + 5 = 20", 60, 120);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    @Test
    @Order(1)
    void setup() throws Exception {
        adminToken = login("13800000000", "admin123456");
        JsonNode aiUser = exchange("/api/admin/users", HttpMethod.POST,
                Map.of("phone", "13944445555", "password", "test123456", "aiEnabled", true), adminToken);
        aiUserId = aiUser.path("data").path("id").asLong();
        exchange("/api/admin/users", HttpMethod.POST,
                Map.of("phone", "13955556660", "password", "test123456"), adminToken);
        aiToken = login("13944445555", "test123456");
        noAiToken = login("13955556660", "test123456");

        JsonNode added = exchange("/api/book/entries", HttpMethod.POST,
                Map.of("grade", 7, "term", 1, "subject", "数学",
                        "imageBase64", pngBase64(), "errorType", "马虎"), aiToken);
        assertEquals(0, added.path("code").asInt());
        entryId = added.path("data").path("id").asText();
    }

    @Test
    @Order(2)
    void accessGuards() throws Exception {
        assertEquals(401, exchange("/api/agent/analogy", HttpMethod.POST,
                Map.of("entryId", entryId), null).path("code").asInt());
        assertEquals(401, exchange("/api/agent/explain", HttpMethod.POST,
                Map.of("entryId", entryId), null).path("code").asInt());
        assertEquals(403, exchange("/api/agent/analogy", HttpMethod.POST,
                Map.of("entryId", entryId), noAiToken).path("code").asInt());
        assertEquals(403, exchange("/api/agent/explain", HttpMethod.POST,
                Map.of("entryId", entryId), noAiToken).path("code").asInt());
    }

    @Test
    @Order(3)
    void entryResolutionAndNoKey() throws Exception {
        // 错题不存在 → 404（先于模型配置校验）
        assertEquals(404, exchange("/api/agent/analogy", HttpMethod.POST,
                Map.of("entryId", "no-such-entry"), aiToken).path("code").asInt());
        // 缺少 entryId 与 imageBase64 → 400
        assertEquals(400, exchange("/api/agent/analogy", HttpMethod.POST,
                Map.of("count", 3), aiToken).path("code").asInt());
        // 直传图片缺科目/年级 → 400
        assertEquals(400, exchange("/api/agent/explain", HttpMethod.POST,
                Map.of("imageBase64", pngBase64()), aiToken).path("code").asInt());
        // 未配置 DASHSCOPE_API_KEY → 503 并记流水
        JsonNode result = exchange("/api/agent/analogy", HttpMethod.POST,
                Map.of("entryId", entryId, "count", 3), aiToken);
        assertEquals(503, result.path("code").asInt());
        JsonNode explain = exchange("/api/agent/explain", HttpMethod.POST,
                Map.of("imageBase64", pngBase64(), "subject", "数学", "grade", 7, "errorType", "马虎"), aiToken);
        assertEquals(503, explain.path("code").asInt());
    }

    @Test
    @Order(4)
    void dailyQuota() throws Exception {
        Instant now = Instant.now();
        for (int i = 0; i < 20; i += 1) {
            AiCallLog logRow = new AiCallLog();
            logRow.setUserId(aiUserId);
            logRow.setPhone("13944445555");
            logRow.setAiType("ANALOGY");
            logRow.setSuccess(true);
            logRow.setInputBytes(0);
            logRow.setOutputBytes(0);
            logRow.setDurationMs(1);
            callLogRepository.save(logRow);
            assertNotNull(now);
        }
        JsonNode result = exchange("/api/agent/analogy", HttpMethod.POST,
                Map.of("entryId", entryId), aiToken);
        assertEquals(429, result.path("code").asInt(), "达到每日配额应返回429");
    }

    @Test
    @Order(5)
    void adminAgentConfig() throws Exception {
        JsonNode list = exchange("/api/admin/agents", HttpMethod.GET, null, adminToken);
        assertEquals(0, list.path("code").asInt());
        assertEquals(2, list.path("data").size());

        JsonNode asUser = exchange("/api/admin/agents", HttpMethod.GET, null, aiToken);
        assertEquals(403, asUser.path("code").asInt());

        JsonNode analogy = null;
        for (JsonNode a : list.path("data")) {
            if ("ANALOGY".equals(a.path("key").asText())) {
                analogy = a;
                break;
            }
        }
        assertNotNull(analogy);
        String originalSystem = analogy.path("systemPrompt").asText();
        assertTrue(analogy.path("variables").toString().contains("count"));

        JsonNode updated = exchange("/api/admin/agents/ANALOGY", HttpMethod.PUT, Map.of(
                "systemPrompt", "测试系统提示词",
                "userPromptTemplate", analogy.path("userPromptTemplate").asText(),
                "model", "qwen-vl-plus",
                "temperature", 0.3,
                "maxTokens", 2048,
                "enabled", true), adminToken);
        assertEquals(0, updated.path("code").asInt());
        assertEquals("测试系统提示词", updated.path("data").path("systemPrompt").asText());
        assertEquals("qwen-vl-plus", updated.path("data").path("model").asText());

        JsonNode reset = exchange("/api/admin/agents/ANALOGY/reset", HttpMethod.POST, null, adminToken);
        assertEquals(0, reset.path("code").asInt());
        assertEquals(originalSystem, reset.path("data").path("systemPrompt").asText());
        assertEquals("qwen-vl-max", reset.path("data").path("model").asText());

        JsonNode badUpdate = exchange("/api/admin/agents/ANALOGY", HttpMethod.PUT, Map.of(
                "systemPrompt", "", "userPromptTemplate", "x", "model", "m"), adminToken);
        assertEquals(400, badUpdate.path("code").asInt());
    }

    @Test
    @Order(6)
    void resultCacheHitAndPurge() throws Exception {
        // 按服务端同款公式构造 promptHash：sha256(system + "\n" + renderedUser + "\n" + model + "\n" + temperature)
        JsonNode list = exchange("/api/admin/agents", HttpMethod.GET, null, adminToken);
        JsonNode analogy = null;
        for (JsonNode a : list.path("data")) {
            if ("ANALOGY".equals(a.path("key").asText())) analogy = a;
        }
        assertNotNull(analogy);
        String rendered = analogy.path("userPromptTemplate").asText()
                .replace("{subject}", "数学").replace("{grade}", "7")
                .replace("{term}", "上学期").replace("{errorType}", "马虎").replace("{count}", "3");
        String source = analogy.path("systemPrompt").asText() + "\n" + rendered + "\n"
                + analogy.path("model").asText() + "\n" + analogy.path("temperature").asDouble();
        String promptHash = sha256Hex(source);
        String subjectKey = "entry:" + entryId;

        com.yingying.cuotiku.server.entity.AiAgentResult cached = new com.yingying.cuotiku.server.entity.AiAgentResult();
        cached.setAgentKey("ANALOGY");
        cached.setSubjectKey(subjectKey);
        cached.setPromptHash(promptHash);
        cached.setResultJson("{\"items\":[{\"stem\":\"缓存题\",\"options\":[],\"answer\":\"42\",\"analysis\":\"来自缓存\",\"difficulty\":2}]}");
        cached.setTraceId("cachedtrace1234567");
        agentResultRepository.save(cached);

        // 命中缓存：无需大模型密钥即返回，且 cached=true
        JsonNode hit = exchange("/api/agent/analogy", HttpMethod.POST,
                Map.of("entryId", entryId, "count", 3), aiToken);
        assertEquals(0, hit.path("code").asInt(), () -> "cache hit failed: " + hit);
        assertTrue(hit.path("data").path("cached").asBoolean());
        assertEquals("cachedtrace1234567", hit.path("data").path("traceId").asText());
        assertEquals("缓存题", hit.path("data").path("items").get(0).path("stem").asText());

        // 管理端清空缓存后回退上游（无密钥 → 503）
        JsonNode purge = exchange("/api/admin/agents/results?agentKey=ANALOGY&subjectKey=" + subjectKey,
                HttpMethod.DELETE, null, adminToken);
        assertEquals(0, purge.path("code").asInt());
        assertEquals(1, purge.path("data").path("deleted").asLong());
        // 缓存已清空：不再命中（Order(4) 已耗尽当日配额，故此处为 429 而非上游 503，二者均证明未走缓存）
        JsonNode miss = exchange("/api/agent/analogy", HttpMethod.POST,
                Map.of("entryId", entryId, "count", 3), aiToken);
        assertEquals(429, miss.path("code").asInt());
    }

    private static String sha256Hex(String value) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        return java.util.HexFormat.of().formatHex(digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    @Order(7)
    void statsAndPage() throws Exception {
        JsonNode stats = exchange("/api/admin/ai-stats?aiType=ANALOGY", HttpMethod.GET, null, adminToken);
        assertEquals(0, stats.path("code").asInt());
        assertTrue(stats.path("data").path("total").asLong() >= 1);

        ResponseEntity<String> page = rest.getForEntity("/admin/index.html", String.class);
        assertTrue(page.getBody().contains("Agent管理"));
    }
}
