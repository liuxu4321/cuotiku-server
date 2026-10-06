package com.yingying.cuotiku.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yingying.cuotiku.server.entity.AiAgentResult;
import com.yingying.cuotiku.server.repository.AiAgentResultRepository;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "BOOK_LOCAL_DIR=target/test-data-agentctx/storage",
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
class AgentContextCacheIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private CaptchaService captchaService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AiAgentResultRepository resultRepository;

    private static String token;
    private static String adminToken;
    private static String entryId;
    private static Long mathSubjectId;
    private static String topicName = "行程问题";

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

    private String renderHash(JsonNode config, Map<String, String> vars) throws Exception {
        String rendered = config.path("userPromptTemplate").asText();
        for (Map.Entry<String, String> e : vars.entrySet()) {
            rendered = rendered.replace("{" + e.getKey() + "}", e.getValue());
        }
        String source = config.path("systemPrompt").asText() + "\n" + rendered + "\n"
                + config.path("model").asText() + "\n" + config.path("temperature").asDouble();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
    }

    private void seedCache(String promptHash, String marker) {
        AiAgentResult row = new AiAgentResult();
        row.setAgentKey("ANALOGY");
        row.setSubjectKey("entry:" + entryId);
        row.setPromptHash(promptHash);
        row.setResultJson("{\"items\":[{\"stem\":\"" + marker + "\",\"options\":[],\"answer\":\"\",\"analysis\":\"\",\"difficulty\":1}]}");
        row.setTraceId("ctxcache123456789");
        resultRepository.save(row);
    }

    private Map<String, String> currentVars() {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("subject", "数学");
        vars.put("topic", topicName);
        vars.put("grade", "7");
        vars.put("term", "上学期");
        vars.put("errorType", "马虎");
        vars.put("count", "3");
        return vars;
    }

    @Test
    @Order(1)
    void setup() throws Exception {
        adminToken = login("13800000000", "admin123456");
        exchange("/api/admin/users", HttpMethod.POST,
                Map.of("phone", "13950001111", "password", "test123456", "aiEnabled", true), adminToken);
        token = login("13950001111", "test123456");

        JsonNode subjects = exchange("/api/v2/subjects", HttpMethod.GET, null, token);
        for (JsonNode s : subjects.path("data")) {
            if ("math".equals(s.path("systemKey").asText())) mathSubjectId = s.path("id").asLong();
        }
        JsonNode topic = exchange("/api/v2/subjects/" + mathSubjectId + "/topics", HttpMethod.POST,
                Map.of("name", topicName), token);
        Long topicId = topic.path("data").path("id").asLong();

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("clientId", UUID.randomUUID().toString());
        item.put("grade", 7);
        item.put("term", 1);
        item.put("subjectId", mathSubjectId);
        item.put("topicId", topicId);
        item.put("imageBase64", pngBase64());
        item.put("errorType", "马虎");
        JsonNode batch = exchange("/api/v2/book/entries/batch", HttpMethod.POST,
                Map.of("requestId", UUID.randomUUID().toString(), "items", List.of(item)), token);
        assertEquals("SUCCESS", batch.path("data").path("results").get(0).path("status").asText());
        entryId = batch.path("data").path("results").get(0).path("entryId").asText();
    }

    @Test
    @Order(2)
    void cacheUsesCurrentTaxonomyNames() throws Exception {
        JsonNode config = null;
        for (JsonNode a : exchange("/api/admin/agents", HttpMethod.GET, null, adminToken).path("data")) {
            if ("ANALOGY".equals(a.path("key").asText())) config = a;
        }
        assertNotNull(config);

        // 以当前科目/主题名称渲染的哈希命中缓存 → 证明服务端解析当前分类名称
        seedCache(renderHash(config, currentVars()), "命中当前名称");
        JsonNode hit = exchange("/api/agent/analogy", HttpMethod.POST, Map.of("entryId", entryId), token);
        assertEquals(0, hit.path("code").asInt(), () -> "cache hit failed: " + hit);
        assertTrue(hit.path("data").path("cached").asBoolean());
        assertEquals("命中当前名称", hit.path("data").path("items").get(0).path("stem").asText());

        // 改名后旧缓存失效（哈希含渲染后提示词）
        JsonNode list = exchange("/api/v2/subjects", HttpMethod.GET, null, token);
        int rev = 0;
        for (JsonNode s : list.path("data")) {
            if (s.path("id").asLong() == mathSubjectId) rev = s.path("revision").asInt();
        }
        exchange("/api/v2/subjects/" + mathSubjectId, HttpMethod.PUT,
                Map.of("name", "高等数学", "revision", rev), token);
        JsonNode miss = exchange("/api/agent/analogy", HttpMethod.POST, Map.of("entryId", entryId), token);
        assertEquals(503, miss.path("code").asInt(), "改名后不应命中旧名称缓存");

        // 以新名称渲染可再次命中
        Map<String, String> renamed = currentVars();
        renamed.put("subject", "高等数学");
        seedCache(renderHash(config, renamed), "命中改名后");
        JsonNode hit2 = exchange("/api/agent/analogy", HttpMethod.POST, Map.of("entryId", entryId), token);
        assertEquals(0, hit2.path("code").asInt());
        assertEquals("命中改名后", hit2.path("data").path("items").get(0).path("stem").asText());
    }
}
