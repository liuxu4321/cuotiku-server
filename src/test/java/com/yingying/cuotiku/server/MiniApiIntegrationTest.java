package com.yingying.cuotiku.server;

import static com.yingying.cuotiku.server.mini.MiniSupport.map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.*;
import com.yingying.cuotiku.server.ai.TencentOcrClient;
import com.yingying.cuotiku.server.entity.*;
import com.yingying.cuotiku.server.mini.*;
import com.yingying.cuotiku.server.repository.UserRepository;
import com.yingying.cuotiku.server.security.JwtService;
import com.yingying.cuotiku.server.service.CaptchaService;
import jakarta.persistence.EntityManager;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** 真MySQL与真实HTTP验证契约链路；云服务使用适配器桩，不调用付费接口。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = {
      "BOOK_LOCAL_DIR=target/test-data-mini/storage",
      "app.jwt.secret=test-secret-key-for-integration-tests-0123456789abcdef",
      "app.admin.phone=13800000000",
      "app.admin.password=admin123456",
      "app.cos.bucket=",
      "app.cos.secret-id=",
      "app.cos.secret-key=",
      "app.ai.tencent.secret-id=",
      "app.ai.tencent.secret-key=",
      "app.mini.worker-enabled=false"
    })
class MiniApiIntegrationTest extends AbstractIntegrationTest {
  @Autowired TestRestTemplate rest;
  @Autowired ObjectMapper json;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;
  @Autowired CaptchaService captcha;
  @Autowired RequestMappingHandlerMapping mappings;
  @Autowired EntityManager em;
  @Autowired PlatformTransactionManager transactions;
  @Autowired MiniOutboxService outbox;
  @Autowired MiniProcessingService processing;
  @Autowired MiniOcrGateway ocr;
  @Autowired MiniAssetService assets;
  @Autowired MiniPrintService prints;
  @Autowired MiniPdfRenderer renderer;
  @MockitoBean WechatIdentityClient wechat;
  @MockitoBean TencentOcrClient tencent;
  String token, adminToken, student, subject, topic, error;
  User owner, admin;

  static String id() {
    return UUID.randomUUID().toString();
  }

  @BeforeEach
  void setup() throws Exception {
    reset(wechat, tencent);
    when(wechat.configured()).thenReturn(true);
    when(wechat.appId()).thenReturn("wx-test");
    when(tencent.configured()).thenReturn(true);
    owner = account(Role.USER);
    admin = account(Role.ADMIN);
    token = issue(owner);
    adminToken = issue(admin);
    student =
        ok(
                "/api/mini/v1/students",
                HttpMethod.POST,
                map("nickname", "星星", "grade", 5, "term", 1),
                token)
            .path("id")
            .asText();
    subject =
        ok(base() + "/subjects", HttpMethod.POST, map("name", "数学"), token).path("id").asText();
    topic =
        ok(base() + "/subjects/" + subject + "/topics", HttpMethod.POST, map("name", "分数"), token)
            .path("id")
            .asText();
    error =
        ok(
                base() + "/error-types",
                HttpMethod.POST,
                map("name", "计算错误", "drawGroupCode", "CARELESS"),
                token)
            .path("id")
            .asText();
  }

  User account(Role role) {
    User user = new User();
    user.setPhone(
        "139"
            + String.valueOf(Math.abs(UUID.randomUUID().getMostSignificantBits())).substring(0, 8));
    user.setPassword(
        new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("secret123"));
    user.setRole(role);
    user.setAiEnabled(true);
    return users.saveAndFlush(user);
  }

  String issue(User user) {
    var issued = jwt.issueAccess(user.getPhone(), user.getRole().name());
    user.setSessionJti(issued.jti());
    users.saveAndFlush(user);
    return issued.token();
  }

  String base() {
    return "/api/mini/v1/students/" + student;
  }

  ResponseEntity<String> request(String path, HttpMethod method, Object body, String token)
      throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    if (token != null) headers.setBearerAuth(token);
    return rest.exchange(
        path,
        method,
        new HttpEntity<>(body == null ? null : json.writeValueAsString(body), headers),
        String.class);
  }

  JsonNode ok(String path, HttpMethod method, Object body, String token) throws Exception {
    var response = request(path, method, body, token);
    JsonNode result = json.readTree(response.getBody());
    assertEquals(0, result.path("code").asInt(), () -> response + " " + path);
    return result.path("data");
  }

  void code(String path, HttpMethod method, Object body, String token, int expected)
      throws Exception {
    var response = request(path, method, body, token);
    assertEquals(
        expected,
        json.readTree(response.getBody()).path("code").asInt(),
        () -> response + " " + path);
  }

  byte[] png() throws Exception {
    BufferedImage image = new BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "png", out);
    return out.toByteArray();
  }

  JsonNode upload() throws Exception {
    byte[] bytes = png();
    JsonNode session =
        ok(
            base() + "/uploads",
            HttpMethod.POST,
            map(
                "clientRequestId",
                id(),
                "purpose",
                "ORIGINAL",
                "fileName",
                "test.png",
                "mimeType",
                "image/png",
                "sizeBytes",
                bytes.length,
                "checksumSha256",
                MiniAssetService.sha256(bytes)),
            token);
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.IMAGE_PNG);
    var response =
        rest.exchange(
            session.path("upload").path("url").asText(),
            HttpMethod.PUT,
            new HttpEntity<>(bytes, headers),
            String.class);
    assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    ok(
        base() + "/uploads/" + session.path("uploadId").asText() + "/complete",
        HttpMethod.POST,
        map("checksumSha256", MiniAssetService.sha256(bytes)),
        token);
    return session;
  }

  JsonNode photo() throws Exception {
    JsonNode session = upload();
    String batch =
        ok(
                base() + "/capture-batches",
                HttpMethod.POST,
                map("clientRequestId", id(), "mode", "MULTI"),
                token)
            .path("id")
            .asText();
    JsonNode photo =
        ok(
            base() + "/capture-batches/" + batch + "/photos",
            HttpMethod.POST,
            map(
                "clientRequestId",
                id(),
                "assetId",
                session.path("assetId").asText(),
                "source",
                "CAMERA"),
            token);
    ok(base() + "/capture-batches/" + batch + "/finish", HttpMethod.POST, null, token);
    return photo;
  }

  Map<String, Object> photoSource(JsonNode photo) {
    return map(
        "sourceType",
        "PHOTO",
        "sourceId",
        photo.path("id").asText(),
        "inputRevisionId",
        photo.path("currentRevisionId").asText());
  }

  String entry(JsonNode photo) throws Exception {
    Map<String, Object> item = photoSource(photo);
    item.put("clientId", id());
    JsonNode result =
        ok(
            base() + "/entries/batch",
            HttpMethod.POST,
            map(
                "requestId",
                id(),
                "subjectId",
                subject,
                "topicId",
                topic,
                "errorTypeId",
                error,
                "items",
                List.of(item)),
            token);
    assertEquals(
        "SUCCESS", result.path("results").get(0).path("status").asText(), result.toString());
    return result.path("results").get(0).path("entryId").asText();
  }

  String template() throws Exception {
    String category =
        ok(
                "/api/admin/mini/v1/template-categories",
                HttpMethod.POST,
                map(
                    "code",
                    "C" + id().replace("-", "").substring(0, 20).toUpperCase(java.util.Locale.ROOT),
                    "name",
                    "A4"),
                adminToken)
            .path("id")
            .asText();
    String template =
        ok(
                "/api/admin/mini/v1/templates",
                HttpMethod.POST,
                map(
                    "categoryId",
                    category,
                    "code",
                    "T" + id().replace("-", "").toUpperCase(java.util.Locale.ROOT),
                    "name",
                    "A4单题"),
                adminToken)
            .path("id")
            .asText();
    String asset =
        ok(
                "/api/admin/mini/v1/assets",
                HttpMethod.POST,
                map(
                    "purpose",
                    "TEMPLATE_PREVIEW",
                    "mimeType",
                    "image/svg+xml",
                    "svg",
                    "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"210\" height=\"297\"><rect"
                        + " x=\"12\" y=\"12\" width=\"186\" height=\"260\" fill=\"none\""
                        + " stroke=\"blue\"/></svg>"),
                adminToken)
            .path("id")
            .asText();
    Object layout =
        map(
            "schemaVersion",
            1,
            "unit",
            "mm",
            "paper",
            map("width", 210, "height", 297, "orientation", "PORTRAIT"),
            "margins",
            map("top", 12, "right", 12, "bottom", 12, "left", 12),
            "slots",
            List.of(map("x", 12, "y", 12, "width", 186, "height", 260)),
            "imageFit",
            "CONTAIN",
            "showAnswer",
            false);
    JsonNode version =
        ok(
            "/api/admin/mini/v1/templates/" + template + "/versions",
            HttpMethod.POST,
            map(
                "paperWidthMm",
                "210.00",
                "paperHeightMm",
                "297.00",
                "orientation",
                "PORTRAIT",
                "slotsPerPage",
                1,
                "layout",
                layout,
                "rendererType",
                "DECLARATIVE",
                "rendererVersion",
                "1",
                "previewSvgAssetId",
                asset),
            adminToken);
    ok(
        "/api/admin/mini/v1/templates/"
            + template
            + "/versions/"
            + version.path("id").asText()
            + "/publish",
        HttpMethod.POST,
        null,
        adminToken);
    return version.path("id").asText();
  }

  @Test
  void everyPlannedContractRouteIsRegistered() throws Exception {
    String doc = Files.readString(Path.of("docs/MINIAPP-API.md"));
    Pattern pattern =
        Pattern.compile("`(GET|POST|PUT|PATCH|DELETE) (/api/(?:mini/v1|admin/mini/v1)/[^` ]*)`");
    Set<String> expected = new HashSet<>();
    Matcher matcher = pattern.matcher(doc);
    while (matcher.find()) expected.add(matcher.group(1) + " " + matcher.group(2));
    Set<String> actual = new HashSet<>();
    mappings
        .getHandlerMethods()
        .forEach(
            (mapping, handler) -> {
              for (String path : mapping.getPatternValues())
                for (var method : mapping.getMethodsCondition().getMethods())
                  actual.add(method + " " + path);
            });
    assertEquals(118, expected.size());
    assertTrue(
        actual.containsAll(expected),
        () -> "Missing " + expected.stream().filter(x -> !actual.contains(x)).toList());
  }

  @Test
  void publicCapabilitiesAndAuthenticationBoundary() throws Exception {
    assertTrue(
        ok("/api/mini/v1/capabilities", HttpMethod.GET, null, null)
            .path("studentScopeSupported")
            .asBoolean());
    code(base() + "/entries", HttpMethod.GET, null, null, 401);
    code("/api/admin/mini/v1/accounts", HttpMethod.GET, null, token, 403);
    String identityToken = jwt.issueMiniIdentity("unknown").token();
    code(base() + "/entries", HttpMethod.GET, null, identityToken, 401);
  }

  @Test
  void studentAndTaxonomyIsolationAndDuplicateNames() throws Exception {
    String other =
        ok(
                "/api/mini/v1/students",
                HttpMethod.POST,
                map("nickname", "弟弟", "grade", 2, "term", 2),
                token)
            .path("id")
            .asText();
    code(
        "/api/mini/v1/students/" + other + "/subjects/" + subject,
        HttpMethod.PATCH,
        map("revision", 0, "name", "语文"),
        token,
        404);
    code(base() + "/subjects", HttpMethod.POST, map("name", " 数学 "), token, 4093);
    ok("/api/mini/v1/students/" + other + "/subjects", HttpMethod.POST, map("name", "数学"), token);
    code("/api/mini/v1/students/" + student, HttpMethod.GET, null, issue(account(Role.USER)), 404);
  }

  @Test
  void revisionsAndPreferenceConflicts() throws Exception {
    ok(
        base() + "/subjects/" + subject,
        HttpMethod.PATCH,
        map("revision", 0, "name", "数学思维"),
        token);
    code(
        base() + "/subjects/" + subject,
        HttpMethod.PATCH,
        map("revision", 0, "name", "数学"),
        token,
        4091);
    ok(
        base() + "/preferences",
        HttpMethod.PUT,
        map("defaultSubjectId", subject, "defaultTopicId", topic, "defaultTemplateId", null),
        token);
    code(
        base() + "/preferences",
        HttpMethod.PUT,
        map("defaultSubjectId", null, "defaultTopicId", topic),
        token,
        400);
    ok("/api/mini/v1/me/current-student", HttpMethod.PUT, map("studentId", student), token);
    assertEquals(
        student, ok("/api/mini/v1/me", HttpMethod.GET, null, token).path("lastStudentId").asText());
    JsonNode me = ok("/api/mini/v1/me", HttpMethod.GET, null, token);
    assertEquals(owner.getId().toString(), me.path("account").path("id").asText());
    assertTrue(me.path("capabilities").path("canReadOwnData").asBoolean());
    assertFalse(me.path("account").has("password"));
    assertFalse(me.path("account").has("sessionJti"));
  }

  @Test
  void uploadCompletionPinsImmutableAssetAndAccessIsSigned() throws Exception {
    JsonNode session = upload();
    String url = session.path("upload").path("url").asText();
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.IMAGE_PNG);
    assertEquals(
        HttpStatus.GONE,
        rest.exchange(url, HttpMethod.PUT, new HttpEntity<>(png(), headers), String.class)
            .getStatusCode());
    JsonNode access =
        ok(
            base() + "/assets/" + session.path("assetId").asText() + "/access",
            HttpMethod.POST,
            map("disposition", "INLINE"),
            token);
    assertArrayEquals(png(), rest.getForObject(access.path("url").asText(), byte[].class));
    String badUrl = access.path("url").asText().replace("ticket=", "ticket=x");
    assertEquals(HttpStatus.UNAUTHORIZED, rest.getForEntity(badUrl, String.class).getStatusCode());
  }

  @Test
  void uploadSizeAndHashChecks() throws Exception {
    code(
        base() + "/uploads",
        HttpMethod.POST,
        map(
            "clientRequestId",
            id(),
            "purpose",
            "ORIGINAL",
            "mimeType",
            "image/png",
            "sizeBytes",
            999999999),
        token,
        400);
    byte[] bytes = png();
    JsonNode session =
        ok(
            base() + "/uploads",
            HttpMethod.POST,
            map(
                "clientRequestId",
                id(),
                "purpose",
                "ORIGINAL",
                "mimeType",
                "image/png",
                "sizeBytes",
                bytes.length,
                "checksumSha256",
                "a".repeat(64)),
            token);
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.IMAGE_PNG);
    rest.exchange(
        session.path("upload").path("url").asText(),
        HttpMethod.PUT,
        new HttpEntity<>(bytes, headers),
        String.class);
    code(
        base() + "/uploads/" + session.path("uploadId").asText() + "/complete",
        HttpMethod.POST,
        map(),
        token,
        400);
  }

  @Test
  void captureIdempotencyAndBatchState() throws Exception {
    String key = id();
    Object body = map("clientRequestId", key, "mode", "SINGLE");
    JsonNode first = ok(base() + "/capture-batches", HttpMethod.POST, body, token);
    assertEquals(first, ok(base() + "/capture-batches", HttpMethod.POST, body, token));
    code(
        base() + "/capture-batches",
        HttpMethod.POST,
        map("clientRequestId", key, "mode", "MULTI"),
        token,
        409);
    code(
        base() + "/capture-batches/" + first.path("id").asText() + "/finish",
        HttpMethod.POST,
        null,
        token,
        400);
  }

  @Test
  void savePartialFailureAndRetryDoesNotDuplicate() throws Exception {
    JsonNode photo = photo();
    Map<String, Object> good = photoSource(photo);
    good.put("clientId", "a");
    Map<String, Object> bad =
        map(
            "clientId",
            "b",
            "sourceType",
            "PHOTO",
            "sourceId",
            "missing",
            "inputRevisionId",
            "missing");
    Object body =
        map(
            "requestId",
            id(),
            "subjectId",
            subject,
            "topicId",
            topic,
            "errorTypeId",
            error,
            "items",
            List.of(good, bad));
    JsonNode result = ok(base() + "/entries/batch", HttpMethod.POST, body, token);
    assertEquals("SUCCESS", result.path("results").get(0).path("status").asText());
    assertEquals("FAILED", result.path("results").get(1).path("status").asText());
    assertEquals(
        "DUPLICATE",
        ok(base() + "/entries/batch", HttpMethod.POST, body, token)
            .path("results")
            .get(0)
            .path("status")
            .asText());
    assertEquals(1, ok(base() + "/entries", HttpMethod.GET, null, token).path("items").size());
  }

  @Test
  void regionCropSaveAndInvalidGeometry() throws Exception {
    JsonNode photo = photo();
    String path = base() + "/photos/" + photo.path("id").asText() + "/regions";
    Object geometry =
        map(
            "schemaVersion",
            1,
            "shape",
            "RECTANGLE",
            "coordinateSpace",
            "NORMALIZED",
            "x",
            0.1,
            "y",
            0.1,
            "width",
            0.4,
            "height",
            0.4);
    JsonNode regions =
        ok(
            path,
            HttpMethod.PUT,
            map(
                "clientRequestId",
                id(),
                "revisionId",
                photo.path("currentRevisionId").asText(),
                "items",
                List.of(map("clientRegionId", id(), "geometry", geometry))),
            token);
    String region = regions.path("items").get(0).path("id").asText();
    JsonNode saved =
        ok(
            base() + "/entries/batch",
            HttpMethod.POST,
            map(
                "requestId",
                id(),
                "subjectId",
                subject,
                "errorTypeId",
                error,
                "items",
                List.of(map("clientId", id(), "sourceType", "REGION", "sourceId", region))),
            token);
    assertEquals("SUCCESS", saved.path("results").get(0).path("status").asText());
    code(
        path,
        HttpMethod.PUT,
        map(
            "clientRequestId",
            id(),
            "revisionId",
            photo.path("currentRevisionId").asText(),
            "items",
            List.of(
                map(
                    "clientRegionId",
                    id(),
                    "geometry",
                    map(
                        "schemaVersion",
                        1,
                        "shape",
                        "RECTANGLE",
                        "coordinateSpace",
                        "NORMALIZED",
                        "x",
                        0.9,
                        "y",
                        0,
                        "width",
                        0.4,
                        "height",
                        1)))),
        token,
        400);
  }

  @Test
  void practiceIdempotencyAndEntryVersion() throws Exception {
    String entry = entry(photo());
    Object body =
        map(
            "clientRequestId",
            id(),
            "mode",
            "PRACTICE",
            "answerContent",
            "答案",
            "correct",
            true,
            "durationSeconds",
            60);
    JsonNode first = ok(base() + "/entries/" + entry + "/practices", HttpMethod.POST, body, token);
    assertEquals(
        first, ok(base() + "/entries/" + entry + "/practices", HttpMethod.POST, body, token));
    JsonNode details = ok(base() + "/entries/" + entry, HttpMethod.GET, null, token);
    assertEquals(1, details.path("practiceCount").asInt());
    assertEquals(1, details.path("correctCount").asInt());
    code(
        base() + "/entries/" + entry,
        HttpMethod.PATCH,
        map("version", 0, "remark", "旧修改"),
        token,
        4091);
    ok(
        base() + "/entries/" + entry,
        HttpMethod.PATCH,
        map("version", details.path("version").asInt(), "remark", null),
        token);
  }

  @Test
  void cursorIsBoundToScopeAndFilters() throws Exception {
    JsonNode photo = photo();
    entry(photo);
    entry(photo);
    JsonNode page = ok(base() + "/entries?limit=1", HttpMethod.GET, null, token);
    String cursor = page.path("nextCursor").asText();
    assertFalse(cursor.isBlank());
    JsonNode second = ok(base() + "/entries?limit=1&cursor=" + cursor, HttpMethod.GET, null, token);
    assertNotEquals(page.path("items").get(0).path("id"), second.path("items").get(0).path("id"));
    code(
        base() + "/entries?limit=1&topicId=" + topic + "&cursor=" + cursor,
        HttpMethod.GET,
        null,
        token,
        400);
    code(base() + "/entries?cursor=" + cursor + "x", HttpMethod.GET, null, token, 400);
    assertEquals(
        2, ok(base() + "/summary", HttpMethod.GET, null, token).path("recentEntries").size());
  }

  @Test
  void randomPapersExcludeOtherStudentsAndUseAvailableCount() throws Exception {
    String entry = entry(photo());
    JsonNode result =
        ok(
            base() + "/random-papers",
            HttpMethod.POST,
            map(
                "clientRequestId",
                id(),
                "subjectId",
                subject,
                "counts",
                List.of(map("errorTypeId", error, "count", 5))),
            token);
    assertEquals(1, result.path("actualCount").asInt());
    assertEquals(entry, result.path("sources").get(0).path("sourceId").asText());
    assertEquals(
        1,
        ok(
                base() + "/random-papers/availability",
                HttpMethod.POST,
                map("subjectId", subject),
                token)
            .path("types")
            .get(0)
            .path("availableCount")
            .asInt());
  }

  @Test
  void printSnapshotRendersRealPdfAndConfirmationIsExplicit() throws Exception {
    String entry = entry(photo()), version = template();
    Object body =
        map(
            "clientRequestId",
            id(),
            "templateVersionId",
            version,
            "sourceType",
            "COLLECTION",
            "copies",
            2,
            "sources",
            List.of(map("sourceType", "ENTRY", "sourceId", entry)));
    JsonNode task = ok(base() + "/print-tasks", HttpMethod.POST, body, token);
    String taskId = task.path("id").asText();
    assertEquals(
        taskId, ok(base() + "/print-tasks", HttpMethod.POST, body, token).path("id").asText());
    ok(base() + "/entries/" + entry, HttpMethod.PATCH, map("version", 0, "remark", "后来修改"), token);
    MiniPrintService.RenderInput input = prints.beginRender(taskId);
    MiniPdfRenderer.Output output = renderer.render(input);
    try (var doc = Loader.loadPDF(output.bytes())) {
      assertEquals(2, doc.getNumberOfPages());
    }
    prints.rendered(taskId, output.bytes(), output.pageCount());
    JsonNode detail = ok(base() + "/print-tasks/" + taskId, HttpMethod.GET, null, token);
    assertTrue(detail.path("items").get(0).path("contentSnapshot").path("remark").isNull());
    assertEquals("READY", detail.path("status").asText());
    ok(base() + "/print-tasks/" + taskId + "/download", HttpMethod.POST, null, token);
    assertEquals(
        "READY",
        ok(base() + "/print-tasks/" + taskId, HttpMethod.GET, null, token).path("status").asText());
    assertEquals(
        "PRINT_CONFIRMED",
        ok(
                base() + "/print-tasks/" + taskId + "/confirm",
                HttpMethod.POST,
                map("clientRequestId", id()),
                token)
            .path("status")
            .asText());
    assertTrue(
        ok(base() + "/print-tasks/" + taskId + "/events", HttpMethod.GET, null, token)
                .path("items")
                .size()
            >= 4);
  }

  @Test
  void draftFixedPhotoVersionAndConcurrentVersionConflict() throws Exception {
    JsonNode photo = photo();
    JsonNode draft =
        ok(
            base() + "/paper-drafts",
            HttpMethod.POST,
            map(
                "clientRequestId",
                id(),
                "sourceType",
                "CAPTURE",
                "sources",
                List.of(photoSource(photo))),
            token);
    String draftId = draft.path("id").asText();
    assertEquals(
        photo.path("currentRevisionId"), draft.path("items").get(0).path("inputRevisionId"));
    ok(
        base() + "/paper-drafts/" + draftId + "/items/order",
        HttpMethod.PUT,
        map("version", 0, "itemIds", List.of(draft.path("items").get(0).path("id").asText())),
        token);
    code(
        base() + "/paper-drafts/" + draftId,
        HttpMethod.PATCH,
        map("version", 0, "selectedTemplateId", null),
        token,
        4091);
  }

  @Test
  void templatesPublishedVersionsImmutableAndSvgRejectsScript() throws Exception {
    String version = template();
    String templateId =
        transaction(() -> em.find(PrintTemplateVersion.class, version).getTemplateId());
    code(
        "/api/admin/mini/v1/templates/" + templateId + "/versions/" + version,
        HttpMethod.PUT,
        map(),
        adminToken,
        409);
    for (String svg :
        List.of(
            "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>",
            "<!DOCTYPE svg [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><svg"
                + " xmlns=\"http://www.w3.org/2000/svg\">&x;</svg>"))
      code(
          "/api/admin/mini/v1/assets",
          HttpMethod.POST,
          map("purpose", "TEMPLATE_PREVIEW", "mimeType", "image/svg+xml", "svg", svg),
          adminToken,
          400);
  }

  <T> T transaction(java.util.function.Supplier<T> action) {
    return new TransactionTemplate(transactions).execute(status -> action.get());
  }

  @Test
  void processingPublishesRevisionAndAuditWithFixedInput() throws Exception {
    JsonNode photo = photo();
    byte[] bytes = png();
    when(tencent.erase(anyString()))
        .thenReturn(
            new TencentOcrClient.EraseResult(
                Base64.getEncoder().encodeToString(bytes), "request-test"));
    JsonNode job =
        ok(
            base() + "/processing-jobs",
            HttpMethod.POST,
            map(
                "requestKey",
                id(),
                "batchId",
                photo.path("batchId").asText(),
                "operation",
                "ERASE",
                "applyScope",
                "CURRENT",
                "targets",
                List.of(
                    map(
                        "photoId",
                        photo.path("id").asText(),
                        "inputRevisionId",
                        photo.path("currentRevisionId").asText())),
                "parameters",
                map("schemaVersion", 1)),
            token);
    String itemId = job.path("items").get(0).path("id").asText();
    var input = processing.begin(itemId);
    processing.succeeded(input, ocr.process(input, bytes));
    JsonNode done =
        ok(base() + "/processing-jobs/" + job.path("id").asText(), HttpMethod.GET, null, token);
    assertEquals("SUCCEEDED", done.path("status").asText());
    assertNotEquals(
        photo.path("currentRevisionId"), done.path("items").get(0).path("outputRevisionId"));
    assertEquals(
        1L,
        transaction(
            () ->
                em.createQuery("select count(l) from AiCallLog l where l.jobItemId=:id", Long.class)
                    .setParameter("id", itemId)
                    .getSingleResult()));
  }

  @Test
  void processingFailureAndCancellationDoNotClaimSuccess() throws Exception {
    JsonNode photo = photo();
    JsonNode body =
        json.valueToTree(
            map(
                "requestKey",
                id(),
                "batchId",
                photo.path("batchId").asText(),
                "operation",
                "ERASE",
                "applyScope",
                "CURRENT",
                "targets",
                List.of(
                    map(
                        "photoId",
                        photo.path("id").asText(),
                        "inputRevisionId",
                        photo.path("currentRevisionId").asText())),
                "parameters",
                map("schemaVersion", 1)));
    JsonNode job = ok(base() + "/processing-jobs", HttpMethod.POST, body, token);
    var input = processing.begin(job.path("items").get(0).path("id").asText());
    processing.failed(input, new com.yingying.cuotiku.server.web.ApiException(502, "上游故障"));
    assertEquals(
        "FAILED",
        ok(base() + "/processing-jobs/" + job.path("id").asText(), HttpMethod.GET, null, token)
            .path("status")
            .asText());
    ok(
        base() + "/processing-jobs/" + job.path("id").asText() + "/retry",
        HttpMethod.POST,
        map(
            "clientRequestId",
            id(),
            "itemIds",
            List.of(job.path("items").get(0).path("id").asText())),
        token);
    ok(
        base() + "/processing-jobs/" + job.path("id").asText() + "/cancel",
        HttpMethod.POST,
        null,
        token);
    assertNull(processing.begin(job.path("items").get(0).path("id").asText()));
  }

  @Test
  void feedbackInternalNotesStayPrivateAndAdminWritesAreAudited() throws Exception {
    JsonNode saved =
        ok(
            "/api/mini/v1/feedbacks",
            HttpMethod.POST,
            map("clientRequestId", id(), "content", "测试建议"),
            token);
    ok(
        "/api/admin/mini/v1/feedbacks/" + saved.path("id").asText(),
        HttpMethod.PATCH,
        map("status", "REVIEWING", "adminNote", "内部说明"),
        adminToken);
    assertFalse(
        ok("/api/mini/v1/feedbacks", HttpMethod.GET, null, token)
            .path("items")
            .get(0)
            .has("adminNote"));
    assertTrue(
        transaction(
                () ->
                    em.createQuery(
                            "select count(a) from MiniAdminAudit a where a.actorId=:id", Long.class)
                        .setParameter("id", admin.getId())
                        .getSingleResult())
            > 0);
  }

  @Test
  void membershipRemovalKeepsOrdinaryAccountAndStudentData() throws Exception {
    ok(
        "/api/admin/mini/v1/accounts/" + owner.getId() + "/membership",
        HttpMethod.DELETE,
        null,
        adminToken);
    assertFalse(
        ok("/api/mini/v1/me", HttpMethod.GET, null, token)
            .path("account")
            .path("memberActive")
            .asBoolean());
    assertEquals(
        student,
        ok("/api/mini/v1/students/" + student, HttpMethod.GET, null, token).path("id").asText());
  }

  @Test
  void wechatBindingRequiresCaptchaAndPasswordAndAllowsBoundLogin() throws Exception {
    when(wechat.exchange("test-code"))
        .thenReturn(new WechatIdentityClient.Identity("openid-" + id(), null));
    String identity =
        ok("/api/mini/v1/auth/wechat", HttpMethod.POST, map("code", "test-code"), null)
            .path("identityToken")
            .asText();
    code("/api/mini/v1/auth/login", HttpMethod.POST, map("identityToken", identity), null, 409);
    String captchaId =
        ok("/api/auth/captcha", HttpMethod.GET, null, null).path("captchaId").asText();
    Field store = CaptchaService.class.getDeclaredField("store");
    store.setAccessible(true);
    Object cached = ((Map<?, ?>) store.get(captcha)).get(captchaId);
    Field answer = cached.getClass().getDeclaredField("code");
    answer.setAccessible(true);
    JsonNode bound =
        ok(
            "/api/mini/v1/auth/member-bind",
            HttpMethod.POST,
            map(
                "identityToken",
                identity,
                "phone",
                owner.getPhone(),
                "password",
                "secret123",
                "captchaId",
                captchaId,
                "captchaCode",
                answer.get(cached)),
            null);
    assertEquals(owner.getId().toString(), bound.path("account").path("id").asText());
    assertFalse(
        ok("/api/mini/v1/auth/login", HttpMethod.POST, map("identityToken", identity), null)
            .path("token")
            .asText()
            .isBlank());
    code(base() + "/entries", HttpMethod.GET, null, token, 4011);
  }

  @Test
  void photoSwapDeleteAndAppendKeepUniqueOrder() throws Exception {
    String batch =
        ok(
                base() + "/capture-batches",
                HttpMethod.POST,
                map("clientRequestId", id(), "mode", "MULTI"),
                token)
            .path("id")
            .asText();
    JsonNode a =
        ok(
            base() + "/capture-batches/" + batch + "/photos",
            HttpMethod.POST,
            map(
                "clientRequestId",
                id(),
                "assetId",
                upload().path("assetId").asText(),
                "source",
                "CAMERA"),
            token);
    JsonNode b =
        ok(
            base() + "/capture-batches/" + batch + "/photos",
            HttpMethod.POST,
            map(
                "clientRequestId",
                id(),
                "assetId",
                upload().path("assetId").asText(),
                "source",
                "ALBUM"),
            token);
    ok(
        base() + "/capture-batches/" + batch + "/photos/order",
        HttpMethod.PUT,
        map("photoIds", List.of(b.path("id").asText(), a.path("id").asText())),
        token);
    JsonNode changed = ok(base() + "/capture-batches/" + batch, HttpMethod.GET, null, token);
    assertEquals(b.path("id"), changed.path("photos").get(0).path("id"));
    ok(base() + "/photos/" + a.path("id").asText(), HttpMethod.DELETE, null, token);
    ok(
        base() + "/capture-batches/" + batch + "/photos",
        HttpMethod.POST,
        map(
            "clientRequestId",
            id(),
            "assetId",
            upload().path("assetId").asText(),
            "source",
            "CAMERA"),
        token);
    assertEquals(
        2,
        ok(base() + "/capture-batches/" + batch, HttpMethod.GET, null, token)
            .path("photos")
            .size());
  }

  @Test
  void draftSwapsAreAtomicAndLegacyApiCannotReadStudentEntry() throws Exception {
    JsonNode first = photo(), second = photo();
    String entry = entry(first);
    code("/api/book/entries/" + entry, HttpMethod.GET, null, token, 404);
    JsonNode draft =
        ok(
            base() + "/paper-drafts",
            HttpMethod.POST,
            map(
                "clientRequestId",
                id(),
                "sourceType",
                "CAPTURE",
                "sources",
                List.of(photoSource(first), photoSource(second))),
            token);
    String a = draft.path("items").get(0).path("id").asText();
    String b = draft.path("items").get(1).path("id").asText();
    JsonNode changed =
        ok(
            base() + "/paper-drafts/" + draft.path("id").asText() + "/items/order",
            HttpMethod.PUT,
            map("version", draft.path("version").asInt(), "itemIds", List.of(b, a)),
            token);
    assertEquals(b, changed.path("items").get(0).path("id").asText());
    assertEquals(a, changed.path("items").get(1).path("id").asText());
  }

  @Test
  void concurrentPracticeReceiptsCountOnceAndCanonicalJsonReplays() throws Exception {
    String entry = entry(photo());
    String request = id();
    var body = map("clientRequestId", request, "mode", "PRACTICE", "correct", true);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<JsonNode> a =
          pool.submit(
              () -> ok(base() + "/entries/" + entry + "/practices", HttpMethod.POST, body, token));
      Future<JsonNode> b =
          pool.submit(
              () -> ok(base() + "/entries/" + entry + "/practices", HttpMethod.POST, body, token));
      assertEquals(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
    } finally {
      pool.shutdownNow();
    }
    ok(
        base() + "/entries/" + entry + "/practices",
        HttpMethod.POST,
        map("correct", true, "mode", "PRACTICE", "clientRequestId", request),
        token);
    assertEquals(
        1,
        ok(base() + "/entries/" + entry, HttpMethod.GET, null, token)
            .path("practiceCount")
            .asInt());
  }

  @Test
  void cleanupRejectionStillAuditsReasonAndArchivedStudentStopsQueuedAi() throws Exception {
    JsonNode photo = photo();
    String entry = entry(photo);
    String asset =
        ok(base() + "/entries/" + entry, HttpMethod.GET, null, token).path("imageAssetId").asText();
    code(
        "/api/admin/mini/v1/assets/" + asset + "/cleanup",
        HttpMethod.POST,
        map("clientRequestId", id(), "reason", "验收：引用保护"),
        adminToken,
        4092);
    assertTrue(
        transaction(
                () ->
                    em.createQuery(
                            "select count(a) from MiniAdminAudit a where a.actorId=:actor and"
                                + " a.reason=:reason and a.httpStatus=409",
                            Long.class)
                        .setParameter("actor", admin.getId())
                        .setParameter("reason", "验收：引用保护")
                        .getSingleResult())
            > 0);
    JsonNode job =
        ok(
            base() + "/processing-jobs",
            HttpMethod.POST,
            map(
                "requestKey",
                id(),
                "batchId",
                photo.path("batchId").asText(),
                "operation",
                "ERASE",
                "applyScope",
                "CURRENT",
                "targets",
                List.of(
                    map(
                        "photoId",
                        photo.path("id").asText(),
                        "inputRevisionId",
                        photo.path("currentRevisionId").asText())),
                "parameters",
                map("schemaVersion", 1)),
            token);
    ok("/api/mini/v1/students/" + student, HttpMethod.PATCH, map("status", "ARCHIVED"), token);
    assertNull(processing.begin(job.path("items").get(0).path("id").asText()));
    assertEquals(
        "FAILED",
        transaction(() -> em.find(ProcessingJob.class, job.path("id").asText()).getStatus()));
    verifyNoInteractions(tencent);
  }

  @Test
  void malformedRequestAndUnknownAssetReferencesReturnClientErrors() throws Exception {
    code("/api/mini/v1/students", HttpMethod.POST, List.of(1, 2), token, 400);
    code(
        "/api/mini/v1/students",
        HttpMethod.POST,
        map("nickname", "错误", "grade", 13, "term", 1),
        token,
        400);
    String entry = entry(photo());
    code(base() + "/subjects/" + subject, HttpMethod.DELETE, null, token, 4092);
    JsonNode details = ok(base() + "/entries/" + entry, HttpMethod.GET, null, token);
    code(
        "/api/admin/mini/v1/assets/" + details.path("imageAssetId").asText() + "/cleanup",
        HttpMethod.POST,
        map("clientRequestId", id(), "reason", "测试清理"),
        adminToken,
        4092);
  }
  /** 与客户端配置页相同的列表路径和分页大小，覆盖空列表及主题计数。 */
  @Test
  void foundationConfigurationListsMatchClientRequests() throws Exception {
    JsonNode subjects = ok(base() + "/subjects?limit=100", HttpMethod.GET, null, token);
    assertEquals(subject, subjects.path("items").get(0).path("id").asText());
    assertEquals(1, subjects.path("items").get(0).path("topicCount").asInt());
    assertFalse(subjects.path("hasMore").asBoolean());
    JsonNode topics = ok(base() + "/subjects/" + subject + "/topics?limit=100", HttpMethod.GET, null, token);
    assertEquals(topic, topics.path("items").get(0).path("id").asText());
    JsonNode errors = ok(base() + "/error-types?limit=100", HttpMethod.GET, null, token);
    assertEquals(error, errors.path("items").get(0).path("id").asText());
    String emptyStudent = ok("/api/mini/v1/students", HttpMethod.POST,
        map("nickname", "无分类学生", "grade", 1, "term", 1), token).path("id").asText();
    JsonNode empty = ok("/api/mini/v1/students/" + emptyStudent + "/subjects?limit=100", HttpMethod.GET, null, token);
    assertEquals(0, empty.path("items").size());
  }

}
