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
        "BOOK_LOCAL_DIR=target/test-data-book/storage",
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
class BookApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private CaptchaService captchaService;
    @Autowired
    private ObjectMapper objectMapper;

    private static String userToken;
    private static String entryId;
    private static String entryId2;

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
                Map.of("phone", phone, "password", password,
                        "captchaId", captchaId, "captchaCode", code, "clientLabel", "book-test"),
                null);
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

    private static String pngBase64(int width, int height, Color color) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.BLACK);
        g.drawLine(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    @Test
    @Order(1)
    void setupUsers() throws Exception {
        String adminToken = login("13800000000", "admin123456");
        JsonNode created = exchange("/api/admin/users", HttpMethod.POST,
                Map.of("phone", "13955556666", "memberNo", "BOOK01", "password", "test123456"), adminToken);
        assertEquals(0, created.path("code").asInt(), () -> "create user failed: " + created);
        userToken = login("13955556666", "test123456");
    }

    @Test
    @Order(2)
    void addSingleEntryWithZipStorage() throws Exception {
        JsonNode result = exchange("/api/book/entries", HttpMethod.POST, Map.of(
                "grade", 7, "term", 1, "subject", "数学",
                "imageBase64", pngBase64(800, 400, Color.WHITE),
                "errorType", "马虎",
                "remark", "注意移项变号",
                "answer", "x = 5"),
                userToken);
        assertEquals(0, result.path("code").asInt(), () -> "add failed: " + result);
        JsonNode first = result.path("data");
        entryId = first.path("id").asText();
        assertEquals(7, first.path("grade").asInt());
        assertEquals(1, first.path("term").asInt());
        assertEquals("数学", first.path("subject").asText());
        assertEquals("注意移项变号", first.path("remark").asText());
        assertEquals("x = 5", first.path("answer").asText());
        assertEquals("马虎", first.path("errorType").asText());
        assertEquals(800, first.path("width").asInt());
        assertEquals(400, first.path("height").asInt());
        assertEquals(0, first.path("practiceCount").asInt());
        assertTrue(first.path("createdAt").asLong() > 0);
        assertEquals("/api/book/entries/" + entryId + "/image", first.path("imageUrl").asText());

        // 未传 errorType 时默认「其他」
        JsonNode second = exchange("/api/book/entries", HttpMethod.POST, Map.of(
                "grade", 7, "subject", "数学",
                "imageBase64", pngBase64(600, 900, Color.LIGHT_GRAY)),
                userToken);
        assertEquals(0, second.path("code").asInt());
        assertEquals("其他", second.path("data").path("errorType").asText());
        // 未传 term 时为 null（兼容旧客户端）
        assertTrue(second.path("data").path("term").isNull());
        // 备注/答案默认空（null）
        assertTrue(second.path("data").path("remark").isNull());
        assertTrue(second.path("data").path("answer").isNull());
        entryId2 = second.path("data").path("id").asText();
    }

    @Test
    @Order(3)
    void addEntriesValidation() throws Exception {
        String image = pngBase64(100, 100, Color.WHITE);
        JsonNode badSubject = exchange("/api/book/entries", HttpMethod.POST,
                Map.of("grade", 7, "subject", "物理", "imageBase64", image), userToken);
        assertEquals(400, badSubject.path("code").asInt());

        JsonNode badGrade = exchange("/api/book/entries", HttpMethod.POST,
                Map.of("grade", 10, "subject", "数学", "imageBase64", image), userToken);
        assertEquals(400, badGrade.path("code").asInt());

        JsonNode badImage = exchange("/api/book/entries", HttpMethod.POST,
                Map.of("grade", 7, "subject", "数学", "imageBase64", "not-an-image"), userToken);
        assertEquals(400, badImage.path("code").asInt());
    }

    @Test
    @Order(4)
    void listWithFilters() throws Exception {
        JsonNode all = exchange("/api/book/entries?page=0&size=50", HttpMethod.GET, null, userToken);
        assertEquals(0, all.path("code").asInt());
        assertEquals(2, all.path("data").path("total").asLong());

        JsonNode bySubject = exchange("/api/book/entries?subject=数学", HttpMethod.GET, null, userToken);
        assertEquals(2, bySubject.path("data").path("total").asLong());

        JsonNode byErrorType = exchange("/api/book/entries?errorType=马虎", HttpMethod.GET, null, userToken);
        assertEquals(1, byErrorType.path("data").path("total").asLong());

        JsonNode byGrade = exchange("/api/book/entries?grade=8", HttpMethod.GET, null, userToken);
        assertEquals(0, byGrade.path("data").path("total").asLong());

        JsonNode byTerm = exchange("/api/book/entries?term=1", HttpMethod.GET, null, userToken);
        assertEquals(1, byTerm.path("data").path("total").asLong());
        JsonNode byTerm2 = exchange("/api/book/entries?term=2", HttpMethod.GET, null, userToken);
        assertEquals(0, byTerm2.path("data").path("total").asLong());

        JsonNode badFilter = exchange("/api/book/entries?subject=物理", HttpMethod.GET, null, userToken);
        assertEquals(400, badFilter.path("code").asInt());
    }

    @Test
    @Order(5)
    void getSingleEntry() throws Exception {
        JsonNode one = exchange("/api/book/entries/" + entryId, HttpMethod.GET, null, userToken);
        assertEquals(0, one.path("code").asInt());
        assertEquals(entryId, one.path("data").path("id").asText());
        assertEquals("数学", one.path("data").path("subject").asText());

        JsonNode missing = exchange("/api/book/entries/no-such-id", HttpMethod.GET, null, userToken);
        assertEquals(404, missing.path("code").asInt());
    }

    @Test
    @Order(6)
    void batchGetImages() throws Exception {
        JsonNode result = exchange("/api/book/entries/images", HttpMethod.POST,
                Map.of("ids", List.of(entryId, entryId2, "no-such-id"), "kind", "thumb"), userToken);
        assertEquals(0, result.path("code").asInt());
        JsonNode items = result.path("data");
        assertEquals(2, items.size());
        assertEquals(entryId, items.get(0).path("id").asText());
        assertEquals("image/jpeg", items.get(0).path("contentType").asText());
        assertTrue(items.get(0).path("imageBase64").asText().length() > 0);

        JsonNode originals = exchange("/api/book/entries/images", HttpMethod.POST,
                Map.of("ids", List.of(entryId)), userToken);
        assertEquals(0, originals.path("code").asInt());
        assertEquals("image/png", originals.path("data").get(0).path("contentType").asText());
        // 原图 Base64 解码后为合法 PNG
        byte[] decoded = Base64.getDecoder().decode(originals.path("data").get(0).path("imageBase64").asText());
        assertEquals((byte) 0x89, decoded[0]);
        assertEquals('P', decoded[1]);

        JsonNode empty = exchange("/api/book/entries/images", HttpMethod.POST,
                Map.of("ids", List.of()), userToken);
        assertEquals(400, empty.path("code").asInt());
    }

    @Test
    @Order(7)
    void downloadOriginalAndThumb() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(userToken);

        ResponseEntity<byte[]> original = rest.exchange("/api/book/entries/" + entryId + "/image",
                HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
        assertEquals(HttpStatus.OK, original.getStatusCode());
        assertEquals("image/png", original.getHeaders().getContentType().toString());
        assertTrue(original.getBody().length > 0);

        ResponseEntity<byte[]> thumb = rest.exchange("/api/book/entries/" + entryId + "/image?kind=thumb",
                HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
        assertEquals(HttpStatus.OK, thumb.getStatusCode());
        assertEquals("image/jpeg", thumb.getHeaders().getContentType().toString());
        assertTrue(thumb.getBody().length > 0);
        // JPEG 魔数校验
        assertEquals((byte) 0xFF, thumb.getBody()[0]);
        assertEquals((byte) 0xD8, thumb.getBody()[1]);
    }

    @Test
    @Order(8)
    void entryIsolationBetweenUsers() throws Exception {
        String adminToken = login("13800000000", "admin123456");
        JsonNode notFound = exchange("/api/book/entries/" + entryId, HttpMethod.PUT,
                Map.of("grade", 3), adminToken);
        assertEquals(404, notFound.path("code").asInt());

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        ResponseEntity<String> image = rest.exchange("/api/book/entries/" + entryId + "/image",
                HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertEquals(404, image.getStatusCode().value());
    }

    @Test
    @Order(9)
    void updateEntryMeta() throws Exception {
        JsonNode updated = exchange("/api/book/entries/" + entryId, HttpMethod.PUT,
                Map.of("grade", 8, "term", 2, "subject", "英语", "errorType", "概念不清"), userToken);
        assertEquals(0, updated.path("code").asInt());
        assertEquals(8, updated.path("data").path("grade").asInt());
        assertEquals(2, updated.path("data").path("term").asInt());

        JsonNode badTerm = exchange("/api/book/entries/" + entryId, HttpMethod.PUT,
                Map.of("term", 3), userToken);
        assertEquals(400, badTerm.path("code").asInt());

        // 备注/答案：修改与清空（空字符串清除为 null）
        JsonNode withNote = exchange("/api/book/entries/" + entryId, HttpMethod.PUT,
                Map.of("remark", "二刷仍错", "answer", ""), userToken);
        assertEquals(0, withNote.path("code").asInt());
        assertEquals("二刷仍错", withNote.path("data").path("remark").asText());
        assertTrue(withNote.path("data").path("answer").isNull());
        assertEquals("英语", updated.path("data").path("subject").asText());
        assertEquals("概念不清", updated.path("data").path("errorType").asText());

        JsonNode bad = exchange("/api/book/entries/" + entryId, HttpMethod.PUT,
                Map.of("errorType", "随机"), userToken);
        assertEquals(400, bad.path("code").asInt());
    }

    @Test
    @Order(10)
    void bumpPracticeCount() throws Exception {
        JsonNode bumped = exchange("/api/book/entries/practice", HttpMethod.POST,
                Map.of("ids", List.of(entryId, "non-existent-id")), userToken);
        assertEquals(0, bumped.path("code").asInt());
        assertEquals(1, bumped.path("data").path("updated").asInt());

        JsonNode list = exchange("/api/book/entries?grade=8", HttpMethod.GET, null, userToken);
        assertEquals(1, list.path("data").path("items").get(0).path("practiceCount").asInt());
    }

    @Test
    @Order(11)
    void practiceRecords() throws Exception {
        // 三条刷题记录：2 对 1 错
        for (int i = 0; i < 3; i += 1) {
            JsonNode rec = exchange("/api/book/entries/" + entryId + "/practices", HttpMethod.POST,
                    Map.of("correct", i != 2, "answerContent", i == 2 ? "忘了变号" : "x = 5"), userToken);
            assertEquals(0, rec.path("code").asInt(), () -> "practice failed: " + rec);
            assertEquals(entryId, rec.path("data").path("entryId").asText());
            assertTrue(rec.path("data").path("practicedAt").asLong() > 0);
        }
        // 单条错题聚合：recordCount/correctCount/accuracy/lastPracticedAt；practiceCount 同步 +3（此前为1）
        JsonNode one = exchange("/api/book/entries/" + entryId, HttpMethod.GET, null, userToken);
        assertEquals(3, one.path("data").path("recordCount").asLong());
        assertEquals(2, one.path("data").path("correctCount").asLong());
        assertEquals(66.7, one.path("data").path("accuracy").asDouble(), 0.01);
        assertTrue(one.path("data").path("lastPracticedAt").asLong() > 0);
        assertEquals(4, one.path("data").path("practiceCount").asInt());

        // 明细列表（倒序）
        JsonNode list = exchange("/api/book/entries/" + entryId + "/practices?page=0&size=10",
                HttpMethod.GET, null, userToken);
        assertEquals(3, list.path("data").path("total").asLong());
        assertEquals(66.7, list.path("data").path("accuracy").asDouble(), 0.01);
        assertTrue(list.path("data").path("items").get(0).path("practicedAt").asLong()
                >= list.path("data").path("items").get(2).path("practicedAt").asLong());

        // 跨题历史：按对错筛选
        JsonNode wrong = exchange("/api/book/practices/history?correct=false", HttpMethod.GET, null, userToken);
        assertEquals(1, wrong.path("data").path("total").asLong());
        assertEquals("忘了变号", wrong.path("data").path("items").get(0).path("answerContent").asText());
        JsonNode all = exchange("/api/book/practices/history", HttpMethod.GET, null, userToken);
        assertEquals(3, all.path("data").path("total").asLong());
        assertEquals(66.7, all.path("data").path("accuracy").asDouble(), 0.01);

        // 校验与越权
        JsonNode noCorrect = exchange("/api/book/entries/" + entryId + "/practices", HttpMethod.POST,
                Map.of("answerContent", "x"), userToken);
        assertEquals(400, noCorrect.path("code").asInt());
        String adminToken = login("13800000000", "admin123456");
        assertEquals(404, exchange("/api/book/entries/" + entryId + "/practices", HttpMethod.GET,
                null, adminToken).path("code").asInt());
    }

    @Test
    @Order(12)
    void deleteEntryRemovesRowAndObject() throws Exception {
        JsonNode deleted = exchange("/api/book/entries/" + entryId, HttpMethod.DELETE, null, userToken);
        assertEquals(0, deleted.path("code").asInt());

        JsonNode again = exchange("/api/book/entries/" + entryId, HttpMethod.DELETE, null, userToken);
        assertEquals(404, again.path("code").asInt());

        JsonNode deleted2 = exchange("/api/book/entries/" + entryId2, HttpMethod.DELETE, null, userToken);
        assertEquals(0, deleted2.path("code").asInt());

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(userToken);
        ResponseEntity<String> image = rest.exchange("/api/book/entries/" + entryId + "/image",
                HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertEquals(404, image.getStatusCode().value());

        // 刷题记录级联删除
        JsonNode practices = exchange("/api/book/entries/" + entryId + "/practices",
                HttpMethod.GET, null, userToken);
        assertEquals(404, practices.path("code").asInt());
    }

    @Test
    @Order(13)
    void bookApiRequiresAuth() throws Exception {
        JsonNode list = exchange("/api/book/entries", HttpMethod.GET, null, null);
        assertEquals(401, list.path("code").asInt());
    }
}
