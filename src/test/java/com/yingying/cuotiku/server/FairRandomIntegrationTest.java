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
        "BOOK_LOCAL_DIR=target/test-data-random/storage",
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
class FairRandomIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private CaptchaService captchaService;
    @Autowired
    private ObjectMapper objectMapper;

    private static String token;
    private static final List<String> mathIds = new ArrayList<>();

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

    @Test
    @Order(1)
    void setup() throws Exception {
        String adminToken = login("13800000000", "admin123456");
        JsonNode created = exchange("/api/admin/users", HttpMethod.POST,
                Map.of("phone", "13966667777", "password", "test123456"), adminToken);
        assertEquals(0, created.path("code").asInt(), () -> "create failed: " + created);
        token = login("13966667777", "test123456");

        // 6 道数学-马虎 + 1 道语文-马虎（用于筛选验证）
        Color[] colors = {Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.CYAN, Color.MAGENTA};
        for (Color color : colors) {
            JsonNode added = exchange("/api/book/entries", HttpMethod.POST,
                    Map.of("grade", 7, "subject", "数学", "imageBase64", pngBase64(color), "errorType", "马虎"), token);
            assertEquals(0, added.path("code").asInt());
            mathIds.add(added.path("data").path("id").asText());
        }
        exchange("/api/book/entries", HttpMethod.POST,
                Map.of("grade", 7, "subject", "语文", "imageBase64", pngBase64(Color.ORANGE), "errorType", "马虎"), token);
    }

    @Test
    @Order(2)
    void leastPracticedSelectedFirst() throws Exception {
        // e1,e2 练 2 次；e3 练 1 次；e4-e6 练 0 次
        bump(mathIds.get(0), mathIds.get(1));
        bump(mathIds.get(0), mathIds.get(1));
        bump(mathIds.get(2));

        JsonNode result = random(Map.of("subject", "数学", "counts", Map.of("马虎", 3)));
        assertEquals(0, result.path("code").asInt(), () -> "random failed: " + result);
        JsonNode data = result.path("data");
        assertEquals(3, data.path("selected").asInt());
        assertEquals(6, data.path("byType").path("马虎").path("poolSize").asInt());
        Set<String> picked = pickedIds(data);
        // 必须精确命中 0 次层 {e4,e5,e6}
        assertEquals(Set.of(mathIds.get(3), mathIds.get(4), mathIds.get(5)), picked);
    }

    @Test
    @Order(3)
    void fairnessInvariantAcrossRounds() throws Exception {
        // 多轮「抽 2 道 + 打印成功计数」：每轮抽中的必须是全池练习次数最低的 2 道
        //（最少层不足时允许进入次低层，但不得越过）
        for (int round = 0; round < 8; round += 1) {
            Map<String, Integer> counts = currentCounts();
            int[] sorted = counts.values().stream().mapToInt(Integer::intValue).sorted().toArray();
            int threshold = sorted[1]; // 第 2 小的练习次数
            int min = sorted[0];
            JsonNode result = random(Map.of("subject", "数学", "counts", Map.of("马虎", 2)));
            Set<String> picked = pickedIds(result.path("data"));
            assertEquals(2, picked.size(), "第" + round + "轮抽取数量不对");
            boolean hitMin = false;
            for (String id : picked) {
                assertTrue(counts.get(id) <= threshold,
                        "第" + round + "轮公平性被破坏：抽中了练习次数高于最低两档的题 " + id
                                + " count=" + counts.get(id) + " threshold=" + threshold);
                if (counts.get(id) == min) hitMin = true;
            }
            assertTrue(hitMin, "第" + round + "轮未优先抽取最少练习层的题");
            bump(picked.toArray(new String[0]));
        }
        // 8 轮 x 2 道 = 16 次练习分摊到 6 道题：每道至少 2 次，最多最少差距 ≤ 1（全覆盖轮转）
        Map<String, Integer> finalCounts = currentCounts();
        int fmin = finalCounts.values().stream().mapToInt(Integer::intValue).min().orElseThrow();
        int fmax = finalCounts.values().stream().mapToInt(Integer::intValue).max().orElseThrow();
        assertTrue(fmin >= 2, "存在从未被充分轮转的错题: " + finalCounts);
        assertTrue(fmax - fmin <= 1, "练习次数差距过大，轮转不公平: " + finalCounts);
    }

    @Test
    @Order(4)
    void filtersAndPoolExhaustion() throws Exception {
        // 学期筛选：题池均未指定 term，按 term=1 过滤应无命中
        JsonNode noTerm = random(Map.of("term", 1, "subject", "数学", "counts", Map.of("马虎", 3)));
        assertEquals(0, noTerm.path("data").path("selected").asInt());
        assertEquals(0, noTerm.path("data").path("byType").path("马虎").path("poolSize").asInt());

        // 年级筛选无命中
        JsonNode noGrade = random(Map.of("grade", 8, "subject", "数学", "counts", Map.of("马虎", 3)));
        assertEquals(0, noGrade.path("data").path("selected").asInt());
        assertEquals(0, noGrade.path("data").path("byType").path("马虎").path("poolSize").asInt());

        // 题池不足：请求 10 道只有 6 道
        JsonNode exhausted = random(Map.of("subject", "数学", "counts", Map.of("马虎", 10)));
        assertEquals(10, exhausted.path("data").path("requested").asInt());
        assertEquals(6, exhausted.path("data").path("selected").asInt());
        assertEquals(6, exhausted.path("data").path("items").size());

        // 多类型请求：不存在的类型返回 0 抽中但保留统计
        JsonNode multi = random(Map.of("subject", "数学",
                "counts", Map.of("马虎", 1, "不会", 2, "概念不清", 0)));
        assertEquals(1, multi.path("data").path("selected").asInt());
        assertEquals(0, multi.path("data").path("byType").path("不会").path("poolSize").asInt());
        assertEquals(0, multi.path("data").path("byType").path("其他").path("requested").asInt());
    }

    @Test
    @Order(5)
    void validation() throws Exception {
        JsonNode badType = random(Map.of("counts", Map.of("随机", 3)));
        assertEquals(400, badType.path("code").asInt());

        JsonNode allZero = random(Map.of("counts", Map.of("马虎", 0)));
        assertEquals(400, allZero.path("code").asInt());

        JsonNode badSubject = random(Map.of("subject", "物理", "counts", Map.of("马虎", 1)));
        assertEquals(400, badSubject.path("code").asInt());

        JsonNode tooMany = random(Map.of("counts", Map.of("马虎", 101)));
        assertEquals(400, tooMany.path("code").asInt());

        JsonNode noAuth = exchange("/api/book/entries/random", HttpMethod.POST,
                Map.of("counts", Map.of("马虎", 1)), null);
        assertEquals(401, noAuth.path("code").asInt());
    }

    private JsonNode random(Map<String, Object> body) throws Exception {
        return exchange("/api/book/entries/random", HttpMethod.POST, body, token);
    }

    private void bump(String... ids) throws Exception {
        JsonNode result = exchange("/api/book/entries/practice", HttpMethod.POST,
                Map.of("ids", List.of(ids)), token);
        assertEquals(0, result.path("code").asInt());
    }

    private Set<String> pickedIds(JsonNode data) {
        Set<String> ids = new HashSet<>();
        for (JsonNode item : data.path("items")) {
            ids.add(item.path("id").asText());
        }
        return ids;
    }

    private Map<String, Integer> currentCounts() throws Exception {
        JsonNode list = exchange("/api/book/entries?subject=数学&size=100", HttpMethod.GET, null, token);
        Map<String, Integer> counts = new HashMap<>();
        for (JsonNode item : list.path("data").path("items")) {
            counts.put(item.path("id").asText(), item.path("practiceCount").asInt());
        }
        return counts;
    }
}
