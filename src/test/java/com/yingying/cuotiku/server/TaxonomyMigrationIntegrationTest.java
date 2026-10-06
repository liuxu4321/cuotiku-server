package com.yingying.cuotiku.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yingying.cuotiku.server.entity.BookEntry;
import com.yingying.cuotiku.server.entity.User;
import com.yingying.cuotiku.server.entity.UserSubject;
import com.yingying.cuotiku.server.repository.BookEntryRepository;
import com.yingying.cuotiku.server.repository.UserRepository;
import com.yingying.cuotiku.server.repository.UserSubjectRepository;
import com.yingying.cuotiku.server.service.CaptchaService;
import com.yingying.cuotiku.server.service.TaxonomyMigrationService;
import com.yingying.cuotiku.server.service.TaxonomyService;
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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "BOOK_LOCAL_DIR=target/test-data-taxonomy/storage",
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
class TaxonomyMigrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private CaptchaService captchaService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BookEntryRepository entryRepository;
    @Autowired
    private UserSubjectRepository subjectRepository;
    @Autowired
    private TaxonomyMigrationService migrationService;
    @Autowired
    private TaxonomyService taxonomyService;

    private static Long userId;
    private static long entryCountBefore;

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

    private BookEntry legacyEntry(String subject, String errorType) {
        BookEntry entry = new BookEntry();
        entry.setId(UUID.randomUUID().toString());
        entry.setUserId(userId);
        entry.setGrade(7);
        entry.setSubject(subject);
        entry.setErrorType(errorType);
        entry.setCreatedAt(Instant.now());
        entry.setWidth(100);
        entry.setHeight(80);
        entry.setObjectKey("legacy/" + entry.getId() + ".zip");
        entry.setFormat("png");
        return entryRepository.save(entry);
    }

    @Test
    @Order(1)
    void seedLegacyData() throws Exception {
        String adminToken = login("13800000000", "admin123456");
        JsonNode created = exchange("/api/admin/users", HttpMethod.POST,
                Map.of("phone", "13900001111", "password", "test123456"), adminToken);
        assertEquals(0, created.path("code").asInt());
        userId = created.path("data").path("id").asLong();

        legacyEntry("数学", "马虎");
        legacyEntry("数学", "不会");
        legacyEntry("英语", "马虎");
        legacyEntry("物理", "概念不清");
        legacyEntry("奥数", "不会");
        entryCountBefore = entryRepository.countByUserId(userId);
        assertEquals(5, entryCountBefore);
    }

    @Test
    @Order(2)
    void backfillIsIdempotentAndPreservesUnknown() {
        TaxonomyMigrationService.BackfillSummary first = migrationService.backfillAll();
        assertTrue(first.users() >= 1);
        assertEquals(5, first.entriesUpdated());

        List<UserSubject> subjects = subjectRepository.findByUserIdOrderBySortOrderAscIdAsc(userId);
        // 六科预设 + 自定义「奥数」
        assertEquals(7, subjects.size());
        UserSubject math = subjects.stream().filter(s -> "math".equals(s.getSystemKey())).findFirst().orElseThrow();
        UserSubject physics = subjects.stream().filter(s -> "physics".equals(s.getSystemKey())).findFirst().orElseThrow();
        UserSubject aoshu = subjects.stream().filter(s -> s.getSystemKey() == null).findFirst().orElseThrow();
        assertEquals("奥数", aoshu.getName());

        List<BookEntry> entries = entryRepository.findForAbility(userId, null, null, null, null, null);
        for (BookEntry entry : entries) {
            assertNotNull(entry.getSubjectId(), "回填后 subjectId 非空");
            assertNull(entry.getTopicId(), "历史题 topicId 为空");
        }
        long mathCount = entries.stream().filter(e -> e.getSubjectId().equals(math.getId())).count();
        assertEquals(2, mathCount);
        long physicsCount = entries.stream().filter(e -> e.getSubjectId().equals(physics.getId())).count();
        assertEquals(1, physicsCount);
        long aoshuCount = entries.stream().filter(e -> e.getSubjectId().equals(aoshu.getId())).count();
        assertEquals(1, aoshuCount);

        // 重复执行无新增无重复
        TaxonomyMigrationService.BackfillSummary second = migrationService.backfillAll();
        assertEquals(0, second.entriesUpdated());
        assertEquals(7, subjectRepository.findByUserIdOrderBySortOrderAscIdAsc(userId).size());
        assertEquals(entryCountBefore, entryRepository.countByUserId(userId));

        // 回填不激活
        User user = userRepository.findById(userId).orElseThrow();
        assertNull(user.getTaxonomyV2ActivatedAt());
    }

    @Test
    @Order(3)
    void concurrentInitProducesNoDuplicates() throws Exception {
        String adminToken = login("13800000000", "admin123456");
        JsonNode created = exchange("/api/admin/users", HttpMethod.POST,
                Map.of("phone", "13900002222", "password", "test123456"), adminToken);
        Long uid = created.path("data").path("id").asLong();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            pool.submit(() -> taxonomyService.initSubjects(uid));
            pool.submit(() -> taxonomyService.initSubjects(uid));
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(6, subjectRepository.findByUserIdOrderBySortOrderAscIdAsc(uid).size());
        assertNull(userRepository.findById(uid).orElseThrow().getTaxonomyV2ActivatedAt());
    }
}
