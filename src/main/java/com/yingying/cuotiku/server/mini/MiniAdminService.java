package com.yingying.cuotiku.server.mini;

import static com.yingying.cuotiku.server.mini.MiniSupport.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.yingying.cuotiku.server.entity.*;
import com.yingying.cuotiku.server.web.ApiException;
import jakarta.persistence.LockModeType;
import java.time.*;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 小程序后台管理，不复用旧“一键注销会员”语义；撤销权益后普通账号仍可登录。 */
@Service
@Transactional
public class MiniAdminService {
  private final MiniSupport s;
  private final MiniAccountService accounts;
  private final MiniStudentService students;
  private final MiniProcessingService processing;
  private final MiniPrintService prints;
  private final MiniFeedbackService feedback;
  private final MiniAssetService assets;
  private final MiniIdempotency receipts;
  private final MiniOutboxService outbox;
  private final PasswordEncoder passwords;

  public MiniAdminService(
      MiniSupport s,
      MiniAccountService accounts,
      MiniStudentService students,
      MiniProcessingService processing,
      MiniPrintService prints,
      MiniFeedbackService feedback,
      MiniAssetService assets,
      MiniIdempotency receipts,
      MiniOutboxService outbox,
      PasswordEncoder passwords) {
    this.s = s;
    this.accounts = accounts;
    this.students = students;
    this.processing = processing;
    this.prints = prints;
    this.feedback = feedback;
    this.assets = assets;
    this.receipts = receipts;
    this.outbox = outbox;
    this.passwords = passwords;
  }

  private User account(String id) {
    return s.get(User.class, longId(id));
  }

  @Transactional(readOnly = true)
  public Object accounts(Map<String, String> query) {
    Map<String, Object> f = empty();
    if (query.containsKey("enabled")) {
      String value = oneOf(query.get("enabled"), "true", "false");
      f.put("enabled", Boolean.valueOf(value));
    }
    return s.page(User.class, f, query, "createdAt", true, this::accountDto);
  }

  @Transactional(readOnly = true)
  public Object accountDetail(String id) {
    return accountDto(account(id));
  }

  public Object createAccount(JsonNode body) {
    String phone = required(body, "phone", 20);
    if (!phone.matches("1[0-9]{10}")) throw ApiException.badRequest("手机号格式错误");
    if (s.count(User.class, map("phone", phone)) > 0) throw ApiException.conflict("手机号已存在");
    User row = new User();
    row.setPhone(phone);
    row.setPassword(passwords.encode(password(body)));
    applyAccount(row, body, null);
    s.save(row);
    return accountDto(row);
  }

  private String password(JsonNode body) {
    String password = required(body, "password", 128);
    if (password.length() < 6) throw ApiException.badRequest("密码至少6位");
    return password;
  }

  private void applyAccount(User row, JsonNode body, User operator) {
    if (body.has("memberName")) row.setMemberName(text(body, "memberName"));
    if (body.has("memberNo")) row.setMemberNo(text(body, "memberNo"));
    if (row.getMemberName() != null && row.getMemberName().length() > 64)
      throw ApiException.badRequest("会员名最长64字符");
    if (row.getMemberNo() != null && row.getMemberNo().length() > 32)
      throw ApiException.badRequest("会员号最长32字符");
    if (body.has("memberExpireAt")) {
      String date = text(body, "memberExpireAt");
      try {
        row.setMemberExpireAt(date == null ? null : LocalDate.parse(date));
      } catch (Exception e) {
        throw ApiException.badRequest("会员日期格式为yyyy-MM-dd");
      }
    }
    if (body.has("aiEnabled")) row.setAiEnabled(bool(body, "aiEnabled", false));
    if (body.has("enabled")) {
      boolean enabled = bool(body, "enabled", true);
      if (operator != null && row.getId().equals(operator.getId()) && !enabled)
        throw ApiException.badRequest("不能停用自己的账号");
      row.setEnabled(enabled);
      if (!enabled) revoke(row);
    }
    if (body.has("role")) {
      Role role = Role.valueOf(oneOf(required(body, "role", 16), "USER", "ADMIN"));
      if (operator != null && row.getId().equals(operator.getId()) && role != Role.ADMIN)
        throw ApiException.badRequest("不能取消自己的管理员角色");
      if (role != row.getRole()) revoke(row);
      row.setRole(role);
    }
  }

  public Object updateAccount(User operator, String id, JsonNode body) {
    User row = account(id);
    s.em.lock(row, LockModeType.PESSIMISTIC_WRITE);
    applyAccount(row, body, operator);
    return accountDto(row);
  }

  public Object password(User operator, String id, JsonNode body) {
    User row = account(id);
    s.em.lock(row, LockModeType.PESSIMISTIC_WRITE);
    row.setPassword(passwords.encode(password(body)));
    revoke(row);
    return empty();
  }

  public Object removeMembership(User operator, String id) {
    User row = account(id);
    row.setMemberNo(null);
    row.setMemberExpireAt(null);
    row.setAiEnabled(false);
    return accountDto(row);
  }

  private void revoke(User row) {
    row.setSessionJti(null);
    row.setRefreshJti(null);
    row.setPrevSessionJti(null);
    row.setPrevRefreshJti(null);
  }

  private Map<String, Object> accountDto(User row) {
    Map<String, Object> out = accounts.account(row);
    out.putAll(
        map("phone", row.getPhone(), "enabled", row.isEnabled(), "cancelled", row.isCancelled()));
    return out;
  }

  @Transactional(readOnly = true)
  public Object identities(String id) {
    User user = account(id);
    return map(
        "items",
        s.find(UserIdentity.class, map("userId", user.getId())).stream()
            .map(
                row ->
                    map(
                        "id",
                        row.getId(),
                        "provider",
                        row.getProvider(),
                        "appId",
                        row.getAppId(),
                        "maskedOpenid",
                        row.getOpenid().length() > 3
                            ? "***" + row.getOpenid().substring(row.getOpenid().length() - 3)
                            : "***",
                        "status",
                        row.getStatus(),
                        "boundAt",
                        row.getBoundAt()))
            .toList());
  }

  @Transactional(readOnly = true)
  public Object students(String id, Map<String, String> query) {
    return students.list(account(id), query);
  }

  @Transactional(readOnly = true)
  public Object integrity(Map<String, String> query) {
    String scope = "";
    Map<String, Object> params = new LinkedHashMap<>();
    if (query.containsKey("userId")) {
      scope += " and e.userId=:userId";
      params.put("userId", longId(query.get("userId")));
    }
    if (query.containsKey("studentId")) {
      scope += " and e.studentId=:studentId";
      params.put("studentId", query.get("studentId"));
    }
    long missing =
        diagnostic("select count(e) from BookEntry e where e.studentId is null" + scope, params);
    long cross = 0;
    String[][] refs = {
      {"BookEntry", "StudentProfile", "studentId"},
      {"BookEntry", "UserSubject", "subjectId"},
      {"BookEntry", "SubjectTopic", "topicId"},
      {"BookEntry", "ErrorType", "errorTypeId"},
      {"BookEntry", "MediaAsset", "imageAssetId"},
      {"CapturePhoto", "CaptureBatch", "batchId"},
      {"CapturePhoto", "MediaAsset", "originalAssetId"},
      {"ImageRevision", "CapturePhoto", "photoId"},
      {"ImageRevision", "MediaAsset", "assetId"},
      {"QuestionRegion", "ImageRevision", "revisionId"},
      {"ProcessingJob", "CaptureBatch", "batchId"}
    };
    for (String[] ref : refs) {
      String targetStudent = ref[1].equals("StudentProfile") ? "t.id" : "t.studentId";
      cross +=
          diagnostic(
              "select count(e) from "
                  + ref[0]
                  + " e,"
                  + ref[1]
                  + " t where e."
                  + ref[2]
                  + "=t.id and e.studentId is not null and (e.userId<>t.userId or e.studentId<>"
                  + targetStudent
                  + ")"
                  + scope,
              params);
    }
    long orphan =
        diagnostic(
            "select count(e) from MediaAsset e where (e.studentId is not null and not exists"
                + " (select p.id from StudentProfile p where p.id=e.studentId) or e.userId is not"
                + " null and not exists (select u.id from User u where u.id=e.userId))"
                + scope,
            params);
    List<?> indexes =
        s.em
            .createNativeQuery(
                "select distinct INDEX_NAME from information_schema.STATISTICS where"
                    + " TABLE_SCHEMA=database() and TABLE_NAME='user_subject' and NON_UNIQUE=0 and"
                    + " INDEX_NAME not in"
                    + " ('PRIMARY','uk_subject_scope_name','uk_subject_scope_system')")
            .getResultList();
    return map(
        "missingStudentCount",
        missing,
        "crossStudentReferenceCount",
        cross,
        "orphanAssetCount",
        orphan,
        "legacyIndexWarnings",
        indexes);
  }

  private long diagnostic(String query, Map<String, Object> params) {
    var q = s.em.createQuery(query, Long.class);
    params.forEach(q::setParameter);
    return q.getSingleResult();
  }

  @Transactional(readOnly = true)
  public Object agents() {
    return map("items", s.find(AiAgentConfig.class, empty()).stream().map(this::agentDto).toList());
  }

  public Object updateAgent(String key, JsonNode body) {
    AiAgentConfig row = s.get(AiAgentConfig.class, key);
    s.em.lock(row, LockModeType.PESSIMISTIC_WRITE);
    if (integer(body, "configVersion", -1, 1, Integer.MAX_VALUE)
        != (row.getConfigVersion() == null ? 1 : row.getConfigVersion()))
      throw new ApiException(4091, "模型配置已更新");
    row.setName(required(body, "name", 64));
    row.setSystemPrompt(required(body, "systemPrompt", 20000));
    row.setUserPromptTemplate(required(body, "userPromptTemplate", 20000));
    row.setProvider(oneOf(required(body, "provider", 32), "DASHSCOPE"));
    row.setModel(required(body, "model", 64));
    JsonNode temperature = body.get("temperature");
    if (temperature == null
        || !temperature.isNumber()
        || temperature.asDouble() < 0
        || temperature.asDouble() > 2) throw ApiException.badRequest("temperature范围0～2");
    row.setTemperature(temperature.asDouble());
    row.setMaxTokens(integer(body, "maxTokens", 4096, 1, 32768));
    row.setEnabled(bool(body, "enabled", true));
    row.setConfigVersion((row.getConfigVersion() == null ? 1 : row.getConfigVersion()) + 1);
    s.em.flush();
    return agentDto(row);
  }

  private Object agentDto(AiAgentConfig row) {
    return map(
        "agentKey",
        row.getAgentKey(),
        "name",
        row.getName(),
        "systemPrompt",
        row.getSystemPrompt(),
        "userPromptTemplate",
        row.getUserPromptTemplate(),
        "provider",
        row.getProvider(),
        "model",
        row.getModel(),
        "temperature",
        row.getTemperature(),
        "maxTokens",
        row.getMaxTokens(),
        "enabled",
        row.isEnabled(),
        "configVersion",
        row.getConfigVersion(),
        "updatedAt",
        row.getUpdatedAt());
  }

  private Map<String, Object> filters(Map<String, String> query, String... allowed) {
    Map<String, Object> filters = empty();
    for (String key : allowed)
      if (query.containsKey(key)) {
        String value = query.get(key);
        filters.put(key, key.equals("userId") ? longId(value) : value);
      }
    return filters;
  }

  @Transactional(readOnly = true)
  public Object aiCalls(Map<String, String> query) {
    Map<String, Object> f =
        filters(query, "userId", "studentId", "provider", "aiType", "status", "traceId");
    if (query.containsKey("success"))
      f.put("success", Boolean.valueOf(oneOf(query.get("success"), "true", "false")));
    return s.page(
        AiCallLog.class,
        f,
        query,
        "createdAt",
        true,
        r ->
            map(
                "id",
                r.getId().toString(),
                "userId",
                r.getUserId() == null ? null : r.getUserId().toString(),
                "studentId",
                r.getStudentId(),
                "provider",
                r.getProvider(),
                "aiType",
                r.getAiType(),
                "apiAction",
                r.getApiAction(),
                "model",
                r.getModel(),
                "success",
                r.isSuccess(),
                "traceId",
                r.getTraceId(),
                "requestId",
                r.getRequestId(),
                "inputBytes",
                r.getInputBytes(),
                "outputBytes",
                r.getOutputBytes(),
                "durationMs",
                r.getDurationMs(),
                "inputTokens",
                r.getInputTokens(),
                "outputTokens",
                r.getOutputTokens(),
                "createdAt",
                r.getCreatedAt()));
  }

  @Transactional(readOnly = true)
  public Object statistics(Map<String, String> query) {
    var cb = s.em.getCriteriaBuilder();
    var q = cb.createQuery(Object[].class);
    var root = q.from(AiCallLog.class);
    List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
    filters(query, "userId", "studentId", "provider", "aiType")
        .forEach((k, v) -> predicates.add(cb.equal(root.get(k), v)));
    for (String key : List.of("start", "end"))
      if (query.containsKey(key)) {
        try {
          Instant date = Instant.parse(query.get(key));
          predicates.add(
              key.equals("start")
                  ? cb.greaterThanOrEqualTo(root.get("createdAt"), date)
                  : cb.lessThan(root.get("createdAt"), date));
        } catch (Exception e) {
          throw ApiException.badRequest("日期格式错误");
        }
      }
    q.multiselect(
            cb.count(root),
            cb.sum(cb.<Long>selectCase().when(cb.isTrue(root.get("success")), 1L).otherwise(0L)),
            cb.sum(root.<Long>get("inputBytes")),
            cb.sum(root.<Long>get("outputBytes")),
            cb.sumAsLong(root.get("inputTokens")),
            cb.sumAsLong(root.get("outputTokens")))
        .where(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
    Object[] values = s.em.createQuery(q).getSingleResult();
    long total = ((Number) values[0]).longValue(),
        success = values[1] == null ? 0 : ((Number) values[1]).longValue();
    return map(
        "callCount",
        total,
        "successCount",
        success,
        "failureCount",
        total - success,
        "inputBytes",
        values[2] == null ? 0 : values[2],
        "outputBytes",
        values[3] == null ? 0 : values[3],
        "inputTokens",
        values[4],
        "outputTokens",
        values[5],
        "billingAmount",
        null);
  }

  @Transactional(readOnly = true)
  public Object jobs(Map<String, String> query) {
    return s.page(
        ProcessingJob.class,
        filters(query, "userId", "studentId", "status"),
        query,
        "createdAt",
        true,
        processing::dto);
  }

  @Transactional(readOnly = true)
  public Object job(String id) {
    return processing.dto(s.get(ProcessingJob.class, id));
  }

  public Object retryJob(User operator, String id, JsonNode body) {
    required(body, "reason", 500);
    return receipts.once(
        operator,
        "ADMIN_PROCESS_RETRY",
        required(body, "clientRequestId", 64),
        map("id", id, "body", body),
        () -> {
          ProcessingJob job = s.get(ProcessingJob.class, id);
          User owner = s.get(User.class, job.getUserId());
          List<JsonNode> selected = array(body, "itemIds", 30);
          Set<String> ids = new HashSet<>();
          s.em.lock(job, LockModeType.PESSIMISTIC_WRITE);
          if (!List.of("FAILED", "PARTIAL_SUCCESS").contains(job.getStatus()))
            throw ApiException.conflict("任务没有可重试的失败项");
          if (!owner.isAiEnabled() || !owner.isEnabled() || owner.isCancelled())
            throw ApiException.forbidden("账号未开通AI或已停用");
          for (JsonNode item : selected) {
            String itemId = item.asText();
            if (!ids.add(itemId)) throw ApiException.badRequest("处理项重复");
            ProcessingJobItem row = s.get(ProcessingJobItem.class, itemId);
            if (!id.equals(row.getJobId()) || !"FAILED".equals(row.getStatus()))
              throw ApiException.badRequest("只能重试本任务的失败项");
            row.setStatus("QUEUED");
            row.setFinishedAt(null);
            row.setErrorCode(null);
            row.setErrorMessage(null);
          }
          job.setStatus("QUEUED");
          job.setFinishedAt(null);
          outbox.enqueue("PROCESSING_JOB", id, "PROCESS_REQUESTED");
          return processing.dto(job);
        });
  }

  @Transactional(readOnly = true)
  public Object prints(Map<String, String> query) {
    return s.page(
        PrintTask.class,
        filters(query, "userId", "studentId", "status"),
        query,
        "createdAt",
        true,
        prints::dto);
  }

  @Transactional(readOnly = true)
  public Object print(String id) {
    PrintTask task = s.get(PrintTask.class, id);
    return prints.detail(task);
  }

  public Object retryPrint(User operator, String id, JsonNode body) {
    String reason = required(body, "reason", 500);
    return receipts.once(
        operator,
        "ADMIN_PRINT_RETRY",
        required(body, "clientRequestId", 64),
        map("id", id, "body", body),
        () -> {
          PrintTask task = s.get(PrintTask.class, id);
          return prints.retry(
              s.get(User.class, task.getUserId()),
              task.getStudentId(),
              id,
              "ADMIN",
              reason,
              operator.getId().toString());
        });
  }

  @Transactional(readOnly = true)
  public Object assets(Map<String, String> query) {
    return s.page(
        MediaAsset.class,
        filters(query, "userId", "studentId", "status", "purpose", "storageProvider"),
        query,
        "createdAt",
        true,
        row -> {
          Map<String, Object> dto = assets.dto(row);
          dto.putAll(
              map(
                  "storageProvider",
                  row.getStorageProvider(),
                  "bucket",
                  row.getBucket(),
                  "region",
                  row.getRegion(),
                  "objectKey",
                  row.getObjectKey()));
          return dto;
        });
  }

  private boolean referenced(String id) { // 保持检查清单显式，新增资产引用时应补齐。
    Map<Class<?>, List<String>> refs = new LinkedHashMap<>();
    refs.put(UserProfile.class, List.of("avatarAssetId"));
    refs.put(StudentProfile.class, List.of("avatarAssetId"));
    refs.put(CapturePhoto.class, List.of("originalAssetId"));
    refs.put(ImageRevision.class, List.of("assetId"));
    refs.put(QuestionRegion.class, List.of("cropAssetId"));
    refs.put(BookEntry.class, List.of("imageAssetId", "thumbnailAssetId"));
    refs.put(PrintTemplateVersion.class, List.of("previewSvgAssetId"));
    refs.put(PrintTask.class, List.of("outputPdfAssetId"));
    refs.put(PrintTaskItem.class, List.of("imageAssetId"));
    refs.put(AiCallLog.class, List.of("inputAssetId", "outputAssetId", "responseAssetId"));
    for (var e : refs.entrySet())
      for (String field : e.getValue()) if (s.count(e.getKey(), map(field, id)) > 0) return true;
    return false;
  }

  public Object cleanup(User operator, String id, JsonNode body) {
    required(body, "reason", 500);
    return receipts.once(
        operator,
        "ASSET_CLEANUP",
        required(body, "clientRequestId", 64),
        map("id", id, "body", body),
        () -> {
          MediaAsset asset = s.get(MediaAsset.class, id);
          s.em.lock(asset, LockModeType.PESSIMISTIC_WRITE);
          if (referenced(id)) throw new ApiException(4092, "资产仍有业务引用，不能清理");
          if (!List.of("PENDING_UPLOAD", "AVAILABLE", "DELETE_PENDING").contains(asset.getStatus()))
            throw ApiException.conflict("资产状态不能清理");
          List<AssetUploadSession> sessions = s.find(AssetUploadSession.class, map("assetId", id));
          if (sessions.stream()
              .anyMatch(
                  row ->
                      "ISSUED".equals(row.getStatus())
                          && row.getExpiresAt().isAfter(Instant.now())))
            throw ApiException.conflict("上传会话尚未过期");
          asset.setStatus("DELETE_PENDING");
          outbox.enqueue("ASSET", id, "DELETE_REQUESTED");
          return map("assetId", id, "status", "DELETE_PENDING");
        });
  }

  public void deleteAsset(String id) {
    MediaAsset asset = s.get(MediaAsset.class, id);
    s.em.lock(asset, LockModeType.PESSIMISTIC_WRITE);
    if (!"DELETE_PENDING".equals(asset.getStatus())) return;
    if (referenced(id)) throw new ApiException(4092, "资产已有业务引用");
    assets.storage.deleteStrict(asset.getObjectKey());
    asset.setStatus("DELETED");
    asset.setDeletedAt(Instant.now());
  }

  @Transactional(readOnly = true)
  public Object feedbacks(Map<String, String> query) {
    return s.page(
        UserFeedback.class,
        filters(query, "userId", "status"),
        query,
        "createdAt",
        true,
        row -> {
          Map<String, Object> dto = (Map<String, Object>) feedback.dto(row);
          dto.put("adminNote", row.getAdminNote());
          return dto;
        });
  }

  public Object updateFeedback(String id, JsonNode body) {
    UserFeedback row = s.get(UserFeedback.class, id);
    if (body.has("status"))
      row.setStatus(oneOf(required(body, "status", 16), "NEW", "REVIEWING", "RESOLVED", "CLOSED"));
    if (body.has("adminNote")) {
      String note = text(body, "adminNote");
      if (note != null && note.length() > 2000) throw ApiException.badRequest("内部备注最长2000字符");
      row.setAdminNote(note);
    }
    Map<String, Object> dto = (Map<String, Object>) feedback.dto(row);
    dto.put("adminNote", row.getAdminNote());
    return dto;
  }

  @Transactional(readOnly = true)
  public Object events(Map<String, String> query) {
    return s.page(
        OutboxEvent.class,
        filters(query, "status", "aggregateType"),
        query,
        "createdAt",
        true,
        row ->
            map(
                "id",
                row.getId(),
                "aggregateType",
                row.getAggregateType(),
                "aggregateId",
                row.getAggregateId(),
                "eventType",
                row.getEventType(),
                "status",
                row.getStatus(),
                "attemptCount",
                row.getAttemptCount(),
                "nextAttemptAt",
                row.getNextAttemptAt()));
  }

  public Object retryEvent(String id, JsonNode body) {
    required(body, "reason", 500);
    OutboxEvent row = s.get(OutboxEvent.class, id);
    s.em.lock(row, LockModeType.PESSIMISTIC_WRITE);
    if (!"FAILED".equals(row.getStatus())) throw ApiException.conflict("仅失败消息可重试");
    row.setStatus("PENDING");
    row.setAttemptCount(0);
    row.setNextAttemptAt(Instant.now());
    return map("id", id, "status", "PENDING");
  }
}
