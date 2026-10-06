package com.yingying.cuotiku.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yingying.cuotiku.server.entity.BookEntry;
import com.yingying.cuotiku.server.entity.SubjectTopic;
import com.yingying.cuotiku.server.entity.UserSubject;
import com.yingying.cuotiku.server.repository.BookEntryRepository;
import com.yingying.cuotiku.server.repository.SubjectTopicRepository;
import com.yingying.cuotiku.server.repository.UserRepository;
import com.yingying.cuotiku.server.repository.UserSubjectRepository;
import com.yingying.cuotiku.server.service.CaptchaService;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
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
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PERFORMANCE.md P1–P6、P9 服务端实测（本机 Docker MySQL，非生产）。
 * 运行：PERF_RUN=true mvn test -Dtest=TaxonomyPerfHarnessTest
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "BOOK_LOCAL_DIR=target/test-data-perf/storage",
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
@EnabledIfEnvironmentVariable(named = "PERF_RUN", matches = "true")
class TaxonomyPerfHarnessTest extends AbstractIntegrationTest {

    private static final int SAMPLES = 20;

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private CaptchaService captchaService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserSubjectRepository subjectRepository;
    @Autowired
    private SubjectTopicRepository topicRepository;
    @Autowired
    private BookEntryRepository entryRepository;

    private static String token;
    private static Long userId;
    private static Long mathId;
    private static Long topicId;
    private static final List<Long> sampleMs = new ArrayList<>();

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
    void seedXScale() throws Exception {
        String adminToken = login("13800000000", "admin123456");
        JsonNode created = exchange("/api/admin/users", HttpMethod.POST,
                Map.of("phone", "13940001111", "password", "test123456"), adminToken);
        userId = created.path("data").path("id").asLong();
        token = login("13940001111", "test123456");

        Random random = new Random(42);
        List<UserSubject> subjects = new ArrayList<>();
        for (int i = 0; i < 50; i += 1) {
            UserSubject s = new UserSubject();
            s.setUserId(userId);
            s.setName(i == 0 ? "数学" : "科目" + i);
            s.setNormalizedName((i == 0 ? "数学" : "科目" + i).toLowerCase());
            s.setSystemKey(i == 0 ? "math" : null);
            s.setSortOrder(i * 10);
            subjects.add(s);
        }
        subjects = subjectRepository.saveAll(subjects);
        mathId = subjects.get(0).getId();

        List<SubjectTopic> topics = new ArrayList<>();
        for (int i = 0; i < 200; i += 1) {
            SubjectTopic t = new SubjectTopic();
            t.setUserId(userId);
            t.setSubjectId(mathId);
            t.setName("主题" + i);
            t.setNormalizedName("主题" + i);
            t.setSortOrder(i * 10);
            if (i >= 190) {
                t.setStatus("DISABLED");
            }
            topics.add(t);
        }
        topics = topicRepository.saveAll(topics);
        topicId = topics.get(0).getId();

        List<BookEntry> entries = new ArrayList<>();
        String[] errorTypes = {"马虎", "不会", "概念不清", "其他"};
        for (int i = 0; i < 1000; i += 1) {
            BookEntry e = new BookEntry();
            e.setId(UUID.randomUUID().toString());
            e.setUserId(userId);
            e.setGrade(1 + random.nextInt(12));
            e.setTerm(1 + random.nextInt(2));
            UserSubject subject = subjects.get(random.nextInt(subjects.size()));
            e.setSubjectId(subject.getId());
            e.setSubject(subject.getName());
            if (i >= 300 && subject.getId().equals(mathId)) {
                e.setTopicId(topics.get(random.nextInt(topics.size())).getId());
            }
            if (i >= 100 && i < 200) {
                e.setTopicId(topics.get(190 + random.nextInt(10)).getId());
            }
            e.setErrorType(errorTypes[random.nextInt(4)]);
            e.setCreatedAt(Instant.now().minusSeconds(1000 - i));
            e.setWidth(100);
            e.setHeight(80);
            e.setPracticeCount(random.nextInt(21));
            e.setObjectKey("perf/" + e.getId() + ".zip");
            e.setFormat("png");
            entries.add(e);
            if (entries.size() == 200) {
                entryRepository.saveAll(entries);
                entries.clear();
            }
        }
        entryRepository.saveAll(entries);
        System.out.println("PERF-SEED entries=" + entryRepository.countByUserId(userId)
                + " subjects=" + subjectRepository.countByUserId(userId)
                + " topics=" + topicRepository.countBySubjectId(mathId));
    }

    private long timed(JsonNode ignored) {
        return 0;
    }

    private interface Call {
        JsonNode call() throws Exception;
    }

    private List<Long> sample(Call call) throws Exception {
        call.call();
        List<Long> durations = new ArrayList<>();
        for (int i = 0; i < SAMPLES; i += 1) {
            long start = System.nanoTime();
            call.call();
            durations.add((System.nanoTime() - start) / 1_000_000);
        }
        Collections.sort(durations);
        return durations;
    }

    private long p95(List<Long> sorted) {
        return sorted.get((int) Math.ceil(sorted.size() * 0.95) - 1);
    }

    @Test
    @Order(2)
    void p1ToP4() throws Exception {
        List<Long> p1 = sample(() -> {
            JsonNode subjects = exchange("/api/v2/subjects", HttpMethod.GET, null, token);
            return exchange("/api/v2/subjects/" + mathId + "/topics", HttpMethod.GET, null, token);
        });
        List<Long> p2 = sample(() -> exchange("/api/v2/book/entries?size=50&unclassified=true",
                HttpMethod.GET, null, token));
        List<Long> p3 = sample(() -> exchange("/api/v2/book/entries/random", HttpMethod.POST,
                Map.of("counts", Map.of("马虎", 10, "不会", 10)), token));
        List<Long> p4 = sample(() -> exchange("/api/v2/user/ability?topicId=" + topicId,
                HttpMethod.GET, null, token));
        System.out.println("PERF P1 分类加载 P95=" + p95(p1) + "ms median=" + p1.get(p1.size() / 2));
        System.out.println("PERF P2 列表(未分类筛选,L/U) P95=" + p95(p2) + "ms median=" + p2.get(p2.size() / 2));
        System.out.println("PERF P3 公平抽题 P95=" + p95(p3) + "ms median=" + p3.get(p3.size() / 2));
        System.out.println("PERF P4 能力(主题筛选,X) P95=" + p95(p4) + "ms median=" + p4.get(p4.size() / 2));
        assertTrue(p95(p1) < 5000);
    }

    @Test
    @Order(3)
    void p5AndP6BatchAndReplay() throws Exception {
        BufferedImage image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 40, 40);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        String imageBase64 = Base64.getEncoder().encodeToString(out.toByteArray());

        List<Long> durations = new ArrayList<>();
        String lastRequestId = null;
        String firstRequestId = null;
        String firstClientId = null;
        for (int run = 0; run < 5; run += 1) {
            if (firstRequestId == null) {
                firstRequestId = UUID.randomUUID().toString();
            }
            List<Map<String, Object>> items = new ArrayList<>();
            for (int i = 0; i < 20; i += 1) {
                Map<String, Object> item = new LinkedHashMap<>();
                String clientId = UUID.randomUUID().toString();
                if (firstClientId == null) {
                    firstClientId = clientId;
                }
                item.put("clientId", clientId);
                item.put("grade", 7);
                item.put("subjectId", mathId);
                item.put("topicId", null);
                item.put("imageBase64", imageBase64);
                item.put("errorType", "马虎");
                items.add(item);
            }
            lastRequestId = run == 0 ? firstRequestId : UUID.randomUUID().toString();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("requestId", lastRequestId);
            body.put("items", items);
            long start = System.nanoTime();
            JsonNode result = exchange("/api/v2/book/entries/batch", HttpMethod.POST, body, token);
            durations.add((System.nanoTime() - start) / 1_000_000);
            assertEquals(0, result.path("code").asInt());
        }
        Collections.sort(durations);
        System.out.println("PERF P5 批量加入20题 median=" + durations.get(2) + "ms all=" + durations);

        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("clientId", firstClientId);
        item.put("grade", 7);
        item.put("subjectId", mathId);
        item.put("imageBase64", imageBase64);
        items.add(item);
        long start = System.nanoTime();
        JsonNode replay = exchange("/api/v2/book/entries/batch", HttpMethod.POST,
                Map.of("requestId", firstRequestId, "items", items), token);
        long replayMs = (System.nanoTime() - start) / 1_000_000;
        assertEquals("DUPLICATE", replay.path("data").path("results").get(0).path("status").asText());
        System.out.println("PERF P6 幂等重放=" + replayMs + "ms");
    }

    @Test
    @Order(4)
    void p9ConcurrentInit() throws Exception {
        String adminToken = login("13800000000", "admin123456");
        JsonNode created = exchange("/api/admin/users", HttpMethod.POST,
                Map.of("phone", "13940002222", "password", "test123456"), adminToken);
        Long uid = created.path("data").path("id").asLong();
        String freshToken = login("13940002222", "test123456");

        ExecutorService pool = Executors.newFixedThreadPool(10);
        try {
            List<Future<JsonNode>> futures = new ArrayList<>();
            for (int i = 0; i < 20; i += 1) {
                futures.add(pool.submit(() -> exchange("/api/v2/subjects", HttpMethod.GET, null, freshToken)));
            }
            for (Future<JsonNode> future : futures) {
                JsonNode response = future.get();
                assertEquals(0, response.path("code").asInt(), () -> "并发初始化响应: " + response);
                assertEquals(6, response.path("data").size());
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(6, subjectRepository.countByUserId(uid));
        System.out.println("PERF P9 并发初始化 20 次 → 科目数=6 无重复");
    }
}
