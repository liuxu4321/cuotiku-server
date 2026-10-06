package com.yingying.cuotiku.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yingying.cuotiku.server.entity.BookEntry;
import com.yingying.cuotiku.server.repository.BookEntryRepository;
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
        "BOOK_LOCAL_DIR=target/test-data-compat/storage",
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
class TaxonomyCompatIntegrationTest extends AbstractIntegrationTest {

    private static final String UPGRADE = "此账号已启用新版科目与主题，请升级至 1.5.0 或以上版本后继续此操作。";
    private static final String STATS = "旧版客户端无法完整展示当前范围的能力统计，请升级至 1.5.0 或以上版本。";

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private CaptchaService captchaService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private BookEntryRepository entryRepository;

    private static String token;
    private static Long mathId;
    private static Long englishId;
    private static Long customId;
    private static Long topicId;
    private static String plainEntryId;
    private static String topicEntryId;
    private static String customEntryId;

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
        BufferedImage image = new BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 120, 80);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    private Map<String, Object> v2Item(Long subjectId, Long topicId) throws Exception {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("clientId", UUID.randomUUID().toString());
        item.put("grade", 7);
        item.put("subjectId", subjectId);
        item.put("topicId", topicId);
        item.put("imageBase64", pngBase64());
        item.put("errorType", "马虎");
        return item;
    }

    @Test
    @Order(1)
    void capabilitiesAndPreActivationV1() throws Exception {
        String adminToken = login("13800000000", "admin123456");
        exchange("/api/admin/users", HttpMethod.POST,
                Map.of("phone", "13960001111", "password", "test123456"), adminToken);
        token = login("13960001111", "test123456");

        JsonNode caps = exchange("/api/v2/meta/capabilities", HttpMethod.GET, null, token);
        assertTrue(caps.path("data").path("taxonomyV2Supported").asBoolean());
        assertTrue(caps.path("data").path("taxonomyV2EntryOpen").asBoolean());
        assertFalse(caps.path("data").path("taxonomyV2Activated").asBoolean());

        // 未激活：v1 加入照旧 + 双写 subjectId
        JsonNode added = exchange("/api/book/entries", HttpMethod.POST,
                Map.of("grade", 7, "subject", "数学", "imageBase64", pngBase64(), "errorType", "马虎"), token);
        assertEquals(0, added.path("code").asInt());
        plainEntryId = added.path("data").path("id").asText();
        BookEntry stored = entryRepository.findById(plainEntryId).orElseThrow();
        assertNotNull(stored.getSubjectId(), "未激活 v1 写入双写 subjectId");

        JsonNode subjects = exchange("/api/v2/subjects", HttpMethod.GET, null, token);
        for (JsonNode s : subjects.path("data")) {
            if ("math".equals(s.path("systemKey").asText())) mathId = s.path("id").asLong();
            if ("english".equals(s.path("systemKey").asText())) englishId = s.path("id").asLong();
        }
        assertEquals(mathId, stored.getSubjectId());
    }

    @Test
    @Order(2)
    void activatedV1Protections() throws Exception {
        // 激活：v2 管理写
        JsonNode custom = exchange("/api/v2/subjects", HttpMethod.POST, Map.of("name", "编程"), token);
        customId = custom.path("data").path("id").asLong();
        JsonNode caps = exchange("/api/v2/meta/capabilities", HttpMethod.GET, null, token);
        assertTrue(caps.path("data").path("taxonomyV2Activated").asBoolean());

        // v1 add 阻断
        JsonNode blocked = exchange("/api/book/entries", HttpMethod.POST,
                Map.of("grade", 7, "subject", "数学", "imageBase64", pngBase64()), token);
        assertEquals(4026, blocked.path("code").asInt());
        assertEquals(UPGRADE, blocked.path("message").asText());

        // v1 update 仅备注：放行且保留分类
        JsonNode remarkOnly = exchange("/api/book/entries/" + plainEntryId, HttpMethod.PUT,
                Map.of("remark", "旧版改备注"), token);
        assertEquals(0, remarkOnly.path("code").asInt());
        assertEquals(mathId, entryRepository.findById(plainEntryId).orElseThrow().getSubjectId());

        // v1 update 预设科目间切换（无 topic）：放行并按 systemKey 重映射
        JsonNode remap = exchange("/api/book/entries/" + plainEntryId, HttpMethod.PUT,
                Map.of("subject", "英语"), token);
        assertEquals(0, remap.path("code").asInt());
        assertEquals(englishId, entryRepository.findById(plainEntryId).orElseThrow().getSubjectId());

        // 带 topic 的题：v1 改科目阻断
        JsonNode topic = exchange("/api/v2/subjects/" + mathId + "/topics", HttpMethod.POST,
                Map.of("name", "行程"), token);
        topicId = topic.path("data").path("id").asLong();
        JsonNode batch = exchange("/api/v2/book/entries/batch", HttpMethod.POST,
                Map.of("requestId", UUID.randomUUID().toString(),
                        "items", List.of(v2Item(mathId, topicId), v2Item(customId, null))), token);
        topicEntryId = batch.path("data").path("results").get(0).path("entryId").asText();
        customEntryId = batch.path("data").path("results").get(1).path("entryId").asText();
        JsonNode blockedEdit = exchange("/api/book/entries/" + topicEntryId, HttpMethod.PUT,
                Map.of("subject", "英语"), token);
        assertEquals(4026, blockedEdit.path("code").asInt());

        // v1 随机抽题：未指定科目且范围含自定义题 → 426；指定标准科目 → 正常
        JsonNode randomAll = exchange("/api/book/entries/random", HttpMethod.POST,
                Map.of("counts", Map.of("马虎", 2)), token);
        assertEquals(4026, randomAll.path("code").asInt());
        JsonNode randomMath = exchange("/api/book/entries/random", HttpMethod.POST,
                Map.of("subject", "数学", "counts", Map.of("马虎", 2)), token);
        assertEquals(0, randomMath.path("code").asInt());

        // v1 能力：全科范围含自定义 → 426 统计文案；指定标准科目 → 正常
        JsonNode abilityAll = exchange("/api/user/ability", HttpMethod.GET, null, token);
        assertEquals(4026, abilityAll.path("code").asInt());
        assertEquals(STATS, abilityAll.path("message").asText());
        JsonNode abilityMath = exchange("/api/user/ability?subject=数学", HttpMethod.GET, null, token);
        assertEquals(0, abilityMath.path("code").asInt());
    }

    @Test
    @Order(3)
    void v1ReadNameMapping() throws Exception {
        // 改名后 v1 读返回模板规范名，v2 读返回当前名
        JsonNode list = exchange("/api/v2/subjects", HttpMethod.GET, null, token);
        int rev = 0;
        for (JsonNode s : list.path("data")) {
            if (s.path("id").asLong() == mathId) rev = s.path("revision").asInt();
        }
        exchange("/api/v2/subjects/" + mathId, HttpMethod.PUT,
                Map.of("name", "高等数学", "revision", rev), token);

        JsonNode v1List = exchange("/api/book/entries?size=100", HttpMethod.GET, null, token);
        boolean sawMath = false;
        for (JsonNode item : v1List.path("data").path("items")) {
            if (item.path("id").asText().equals(topicEntryId)) {
                assertEquals("数学", item.path("subject").asText(), "v1 六科题返回模板规范名");
                sawMath = true;
            }
            if (item.path("id").asText().equals(customEntryId)) {
                assertEquals("编程", item.path("subject").asText(), "v1 自定义题原样返回当前名");
            }
        }
        assertTrue(sawMath);
        JsonNode v2List = exchange("/api/v2/book/entries?size=100", HttpMethod.GET, null, token);
        for (JsonNode item : v2List.path("data").path("items")) {
            if (item.path("id").asText().equals(topicEntryId)) {
                assertEquals("高等数学", item.path("subjectName").asText());
            }
        }
    }
}
