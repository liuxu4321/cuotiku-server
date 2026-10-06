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
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "BOOK_LOCAL_DIR=target/test-data-ability/storage",
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
class AbilityIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private CaptchaService captchaService;
    @Autowired
    private ObjectMapper objectMapper;

    private static String token;
    private static final List<String> mathCarelessIds = new java.util.ArrayList<>();

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

    private static String pngBase64(Color color) throws Exception {
        BufferedImage image = new BufferedImage(200, 120, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, 200, 120);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    private JsonNode ability(String query) throws Exception {
        return exchange("/api/user/ability" + query, HttpMethod.GET, null, token);
    }

    private JsonNode dim(JsonNode model, String key) {
        for (JsonNode d : model.path("dimensions")) {
            if (key.equals(d.path("key").asText())) return d;
        }
        return null;
    }

    private JsonNode subjectModel(JsonNode data, String subject) {
        for (JsonNode s : data.path("subjects")) {
            if (subject.equals(s.path("subject").asText())) return s;
        }
        return null;
    }

    @Test
    @Order(1)
    void setup() throws Exception {
        String adminToken = login("13800000000", "admin123456");
        JsonNode created = exchange("/api/admin/users", HttpMethod.POST,
                Map.of("phone", "13977778888", "password", "test123456"), adminToken);
        assertEquals(0, created.path("code").asInt(), () -> "create failed: " + created);
        token = login("13977778888", "test123456");

        // 数学 3 道马虎 + 语文 1 道不会
        for (Color color : new Color[]{Color.RED, Color.GREEN, Color.BLUE}) {
            JsonNode added = exchange("/api/book/entries", HttpMethod.POST,
                    Map.of("grade", 7, "term", 1, "subject", "数学",
                            "imageBase64", pngBase64(color), "errorType", "马虎"), token);
            assertEquals(0, added.path("code").asInt());
            mathCarelessIds.add(added.path("data").path("id").asText());
        }
        exchange("/api/book/entries", HttpMethod.POST,
                Map.of("grade", 7, "term", 1, "subject", "语文",
                        "imageBase64", pngBase64(Color.ORANGE), "errorType", "不会"), token);
    }

    @Test
    @Order(2)
    void defaultReturnsThreeSubjects() throws Exception {
        JsonNode result = ability("");
        assertEquals(0, result.path("code").asInt(), () -> "ability failed: " + result);
        JsonNode data = result.path("data");
        assertEquals(3, data.path("subjects").size(), "默认应同时返回三科");
        assertEquals(4, data.path("overall").path("sampleSize").asInt());
        assertEquals(3, subjectModel(data, "数学").path("sampleSize").asInt());
        assertEquals(1, subjectModel(data, "语文").path("sampleSize").asInt());
        assertEquals(0, subjectModel(data, "英语").path("sampleSize").asInt());

        // 五维齐全且顺序固定
        JsonNode dims = data.path("overall").path("dimensions");
        assertEquals(5, dims.size());
        assertEquals("CAREFULNESS", dims.get(0).path("key").asText());
        assertEquals("DILIGENCE", dims.get(4).path("key").asText());

        // 数学：马虎拉低细心度；无「不会」→ 理解力满分
        JsonNode math = subjectModel(data, "数学");
        assertTrue(dim(math, "CAREFULNESS").path("score").asInt() < 100);
        assertEquals(100, dim(math, "COMPREHENSION").path("score").asInt());
        assertEquals(3, dim(math, "CAREFULNESS").path("totalCount").asInt());

        // 语文：不会拉低理解力
        JsonNode chinese = subjectModel(data, "语文");
        assertTrue(dim(chinese, "COMPREHENSION").path("score").asInt() < 100);
        assertEquals(100, dim(chinese, "CAREFULNESS").path("score").asInt());

        // 未刷题 → 勤奋度 0
        assertEquals(0, dim(data.path("overall"), "DILIGENCE").path("score").asInt());
    }

    @Test
    @Order(3)
    void practiceImprovesScores() throws Exception {
        JsonNode before = ability("");
        int carefulBefore = dim(subjectModel(before.path("data"), "数学"), "CAREFULNESS").path("score").asInt();
        int diligenceBefore = dim(before.path("data").path("overall"), "DILIGENCE").path("score").asInt();

        // 数学马虎题各刷 3 次
        JsonNode bump = exchange("/api/book/entries/practice", HttpMethod.POST,
                Map.of("ids", mathCarelessIds), token);
        assertEquals(0, bump.path("code").asInt());
        bump = exchange("/api/book/entries/practice", HttpMethod.POST,
                Map.of("ids", mathCarelessIds), token);
        assertEquals(0, bump.path("code").asInt());
        bump = exchange("/api/book/entries/practice", HttpMethod.POST,
                Map.of("ids", mathCarelessIds), token);
        assertEquals(0, bump.path("code").asInt());

        JsonNode after = ability("");
        int carefulAfter = dim(subjectModel(after.path("data"), "数学"), "CAREFULNESS").path("score").asInt();
        int diligenceAfter = dim(after.path("data").path("overall"), "DILIGENCE").path("score").asInt();
        assertTrue(carefulAfter > carefulBefore,
                "刷题后细心度应提升: before=" + carefulBefore + " after=" + carefulAfter);
        assertTrue(diligenceAfter > diligenceBefore,
                "刷题后勤奋度应提升: before=" + diligenceBefore + " after=" + diligenceAfter);
        // 语文未刷题不受影响
        assertEquals(100, dim(subjectModel(after.path("data"), "语文"), "CAREFULNESS").path("score").asInt());
    }

    @Test
    @Order(4)
    void filtersAndValidation() throws Exception {
        JsonNode bySubject = ability("?subject=数学");
        assertEquals(1, bySubject.path("data").path("subjects").size());
        assertEquals("数学", bySubject.path("data").path("subjects").get(0).path("subject").asText());

        JsonNode byTerm = ability("?term=2");
        assertEquals(0, byTerm.path("data").path("overall").path("sampleSize").asInt());
        assertTrue(dim(byTerm.path("data").path("overall"), "CAREFULNESS").path("score").isNull());

        assertEquals(400, ability("?subject=物理").path("code").asInt());
        assertEquals(400, ability("?start=2026/01/01").path("code").asInt());

        JsonNode noAuth = exchange("/api/user/ability", HttpMethod.GET, null, null);
        assertEquals(401, noAuth.path("code").asInt());
    }

    @Test
    @Order(5)
    void emptyUserHasNullScores() throws Exception {
        String adminToken = login("13800000000", "admin123456");
        exchange("/api/admin/users", HttpMethod.POST,
                Map.of("phone", "13988889999", "password", "test123456"), adminToken);
        String emptyToken = login("13988889999", "test123456");
        JsonNode result = exchange("/api/user/ability", HttpMethod.GET, null, emptyToken);
        assertEquals(0, result.path("code").asInt());
        JsonNode overall = result.path("data").path("overall");
        assertEquals(0, overall.path("sampleSize").asInt());
        assertTrue(overall.path("overall").isNull());
        for (JsonNode d : overall.path("dimensions")) {
            assertTrue(d.path("score").isNull());
        }
    }
}
