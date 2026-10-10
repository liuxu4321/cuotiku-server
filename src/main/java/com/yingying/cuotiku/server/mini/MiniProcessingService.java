package com.yingying.cuotiku.server.mini;

import static com.yingying.cuotiku.server.mini.MiniSupport.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.yingying.cuotiku.server.ai.ImageUtil;
import com.yingying.cuotiku.server.entity.*;
import com.yingying.cuotiku.server.web.ApiException;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 异步图片任务：提交固定输入，短事务认领/发布，外部云调用由Worker在事务外执行。 */
@Service
@Transactional
public class MiniProcessingService {
  private final MiniSupport s;
  private final MiniCaptureService captures;
  private final MiniIdempotency receipts;
  private final MiniOutboxService outbox;
  private final MiniAssetService assets;

  public MiniProcessingService(
      MiniSupport s,
      MiniCaptureService captures,
      MiniIdempotency receipts,
      MiniOutboxService outbox,
      MiniAssetService assets) {
    this.s = s;
    this.captures = captures;
    this.receipts = receipts;
    this.outbox = outbox;
    this.assets = assets;
  }

  public ProcessingJob job(User user, String student, String id) {
    ProcessingJob row = s.owned(ProcessingJob.class, id, user, student);
    return row;
  }

  public Object create(User user, String student, JsonNode body) {
    if (!user.isAiEnabled()) throw ApiException.forbidden("账号未开通AI权限");
    s.activeStudent(user, student);
    return receipts.once(
        user,
        "PROCESSING_JOB",
        required(body, "requestKey", 64),
        map("studentId", student, "body", body),
        () -> {
          CaptureBatch batch = captures.batch(user, student, required(body, "batchId", 36));
          if (!"READY".equals(batch.getStatus())) throw ApiException.conflict("请先完成拍摄");
          ProcessingJob job = new ProcessingJob();
          job.setUserId(user.getId());
          job.setStudentId(student);
          job.setBatchId(batch.getId());
          job.setOperation(
              oneOf(
                  required(body, "operation", 32),
                  "CORRECT",
                  "ENHANCE",
                  "ERASE",
                  "SPLIT",
                  "PAPER_PROCESS"));
          job.setApplyScope(oneOf(required(body, "applyScope", 16), "CURRENT", "ALL"));
          JsonNode parameters = body.get("parameters");
          if (parameters == null
              || !parameters.isObject()
              || parameters.path("schemaVersion").asInt() != 1)
            throw ApiException.badRequest("parameters需要schemaVersion=1");
          validateParameters(job.getOperation(), parameters);
          job.setParametersJson(s.encode(parameters));
          job.setRequestKey(required(body, "requestKey", 64));
          job.setRequestHash(s.hash(body));
          job.setTraceId(UUID.randomUUID().toString().replace("-", ""));
          List<JsonNode> targets = array(body, "targets", 30);
          if ("CURRENT".equals(job.getApplyScope()) && targets.size() != 1)
            throw ApiException.badRequest("CURRENT只允许一张照片");
          if ("ALL".equals(job.getApplyScope()) && targets.size() != captures.photos(batch).size())
            throw ApiException.badRequest("ALL必须包含批次全部照片");
          s.save(job);
          Set<String> ids = new HashSet<>();
          for (JsonNode target : targets) {
            CapturePhoto photo = captures.photo(user, student, required(target, "photoId", 36));
            if (!photo.getBatchId().equals(batch.getId()) || !ids.add(photo.getId()))
              throw ApiException.badRequest("任务照片重复或不属于该批次");
            ImageRevision revision =
                captures.revision(user, student, photo, required(target, "inputRevisionId", 36));
            ProcessingJobItem item = new ProcessingJobItem();
            item.setJobId(job.getId());
            item.setPhotoId(photo.getId());
            item.setInputRevisionId(revision.getId());
            item.setStepResultsJson("[]");
            s.save(item);
          }
          outbox.enqueue("PROCESSING_JOB", job.getId(), "PROCESS_REQUESTED");
          return dto(job);
        });
  }

  private void validateParameters(String operation, JsonNode p) {
    Set<String> allowed = new HashSet<>(List.of("schemaVersion"));
    if (List.of("CORRECT", "ENHANCE").contains(operation))
      allowed.addAll(List.of("crop", "deskew", "adjustOrientation", "enhanceType"));
    if (operation.equals("SPLIT")) allowed.add("useNewModel");
    if (operation.equals("PAPER_PROCESS")) allowed.addAll(List.of("enhance", "erase", "split"));
    p.fieldNames()
        .forEachRemaining(
            k -> {
              if (!allowed.contains(k)) throw ApiException.badRequest("不支持的工具参数：" + k);
            });
    for (String key :
        List.of("crop", "deskew", "adjustOrientation", "useNewModel", "enhance", "erase", "split"))
      if (p.has(key)) bool(p, key, false);
    if (p.has("enhanceType")) integer(p, "enhanceType", -1, -1, 6);
  }

  @Transactional(readOnly = true)
  public Object get(User user, String student, String id) {
    return dto(job(user, student, id));
  }

  public Object retryRequest(User user, String student, String id, JsonNode body) {
    return receipts.once(
        user,
        "PROCESS_RETRY",
        required(body, "clientRequestId", 64),
        map("studentId", student, "id", id, "body", body),
        () -> {
          ProcessingJob job = job(user, student, id);
          s.em.lock(job, LockModeType.PESSIMISTIC_WRITE);
          if (!List.of("FAILED", "PARTIAL_SUCCESS").contains(job.getStatus()))
            throw ApiException.conflict("仅失败处理项可重试");
          if (!user.isAiEnabled()) throw ApiException.forbidden("账号未开通AI权限");
          Set<String> ids = new HashSet<>();
          for (JsonNode value : array(body, "itemIds", 30)) {
            String itemId = value.asText();
            if (!ids.add(itemId)) throw ApiException.badRequest("处理项重复");
            ProcessingJobItem item = s.get(ProcessingJobItem.class, itemId);
            if (!id.equals(item.getJobId()) || !"FAILED".equals(item.getStatus()))
              throw ApiException.badRequest("只能重试当前任务失败项");
            item.setStatus("QUEUED");
            item.setFinishedAt(null);
            item.setErrorCode(null);
            item.setErrorMessage(null);
          }
          job.setStatus("QUEUED");
          job.setFinishedAt(null);
          outbox.enqueue("PROCESSING_JOB", id, "PROCESS_REQUESTED");
          return dto(job);
        });
  }

  public Object retry(User user, String student, String id) {
    ProcessingJob job = job(user, student, id);
    s.em.lock(job, LockModeType.PESSIMISTIC_WRITE);
    if (!List.of("FAILED", "PARTIAL_SUCCESS").contains(job.getStatus()))
      throw ApiException.conflict("仅失败处理项可重试");
    if (!user.isAiEnabled()) throw ApiException.forbidden("账号未开通AI权限");
    for (ProcessingJobItem item : items(job)) {
      if ("FAILED".equals(item.getStatus())) {
        item.setStatus("QUEUED");
        item.setFinishedAt(null);
        item.setErrorCode(null);
        item.setErrorMessage(null);
      }
    }
    job.setStatus("QUEUED");
    job.setFinishedAt(null);
    outbox.enqueue("PROCESSING_JOB", id, "PROCESS_REQUESTED");
    return dto(job);
  }

  public Object cancel(User user, String student, String id) {
    ProcessingJob job = job(user, student, id);
    s.em.lock(job, LockModeType.PESSIMISTIC_WRITE);
    if ("CANCELLED".equals(job.getStatus())) return dto(job);
    if (!List.of("QUEUED", "RUNNING").contains(job.getStatus()))
      throw ApiException.conflict("终态不能取消");
    job.setStatus("CANCELLED");
    job.setFinishedAt(Instant.now());
    for (ProcessingJobItem item : items(job))
      if (List.of("QUEUED", "RUNNING").contains(item.getStatus())) {
        item.setStatus("CANCELLED");
        item.setFinishedAt(Instant.now());
      }
    return dto(job);
  }

  public List<ProcessingJobItem> items(ProcessingJob job) {
    return s.find(ProcessingJobItem.class, map("jobId", job.getId()));
  }

  @Transactional(readOnly = true)
  public List<String> itemIds(String id) {
    return items(s.get(ProcessingJob.class, id)).stream().map(ProcessingJobItem::getId).toList();
  }

  public record ItemInput(
      String jobId,
      String itemId,
      int attempt,
      User user,
      String studentId,
      String photoId,
      String revisionId,
      String assetId,
      String operation,
      JsonNode parameters,
      String traceId) {}

  public ItemInput begin(String itemId) {
    ProcessingJobItem item = s.get(ProcessingJobItem.class, itemId);
    ProcessingJob job = s.get(ProcessingJob.class, item.getJobId());
    s.em.lock(job, LockModeType.PESSIMISTIC_WRITE);
    s.em.lock(item, LockModeType.PESSIMISTIC_WRITE);
    if (!List.of("QUEUED", "RUNNING").contains(job.getStatus())
        || !List.of("QUEUED", "RUNNING").contains(item.getStatus())) return null;
    job.setStatus("RUNNING");
    item.setStatus("RUNNING");
    item.setStartedAt(Instant.now());
    item.setAttemptCount(item.getAttemptCount() + 1);
    StudentProfile student = s.get(StudentProfile.class, job.getStudentId());
    if (!"ACTIVE".equals(student.getStatus()) || student.getDeletedAt() != null) {
      item.setStatus("FAILED");
      item.setErrorCode("STUDENT_ARCHIVED");
      item.setErrorMessage("学生已归档，处理任务停止");
      item.setFinishedAt(Instant.now());
      finish(job);
      return null;
    }
    ImageRevision input = s.get(ImageRevision.class, item.getInputRevisionId());
    return new ItemInput(
        job.getId(),
        item.getId(),
        item.getAttemptCount(),
        s.get(User.class, job.getUserId()),
        job.getStudentId(),
        item.getPhotoId(),
        input.getId(),
        input.getAssetId(),
        job.getOperation(),
        s.decode(job.getParametersJson()),
        job.getTraceId());
  }

  public void succeeded(ItemInput input, MiniOcrGateway.Result result) {
    ProcessingJob job = s.get(ProcessingJob.class, input.jobId());
    s.em.lock(job, LockModeType.PESSIMISTIC_WRITE);
    ProcessingJobItem item = s.get(ProcessingJobItem.class, input.itemId());
    if (!"RUNNING".equals(job.getStatus())
        || item.getAttemptCount() != input.attempt()
        || !"RUNNING".equals(item.getStatus())) return;
    CapturePhoto photo = captures.photo(input.user(), input.studentId(), input.photoId());
    ImageRevision output = s.get(ImageRevision.class, input.revisionId());
    if (result.bytes() != null) {
      MediaAsset asset =
          assets.store(
              input.user(),
              input.studentId(),
              result.bytes(),
              "PROCESSED",
              ImageUtil.isPng(result.bytes()) ? "image/png" : "image/jpeg");
      ImageRevision revision = new ImageRevision();
      revision.setPhotoId(photo.getId());
      revision.setUserId(job.getUserId());
      revision.setStudentId(job.getStudentId());
      revision.setParentRevisionId(input.revisionId());
      revision.setAssetId(asset.getId());
      revision.setOperation(input.operation());
      revision.setParametersJson(s.encode(input.parameters()));
      revision.setJobItemId(item.getId());
      output = s.save(revision);
      if (input.revisionId().equals(photo.getCurrentRevisionId()))
        photo.setCurrentRevisionId(revision.getId());
    }
    if (List.of("SPLIT", "PAPER_PROCESS").contains(input.operation())) {
      for (QuestionRegion old :
          s.find(
              QuestionRegion.class,
              map("photoId", photo.getId(), "revisionId", output.getId(), "deletedAt", null)))
        old.setDeletedAt(Instant.now());
      for (int i = 0; i < result.regions().size(); i++) {
        QuestionRegion region = new QuestionRegion();
        region.setUserId(job.getUserId());
        region.setStudentId(job.getStudentId());
        region.setPhotoId(photo.getId());
        region.setRevisionId(output.getId());
        region.setGeometryJson(s.encode(result.regions().get(i)));
        region.setOrigin("AI");
        region.setSortOrder(i);
        region.setJobItemId(item.getId());
        s.save(region);
      }
    }
    item.setOutputRevisionId(output.getId());
    item.setStepResultsJson(s.encode(result.steps()));
    item.setStatus("SUCCEEDED");
    item.setFinishedAt(Instant.now());
    finish(job);
  }

  public void failed(ItemInput input, Exception error) {
    ProcessingJob job = s.get(ProcessingJob.class, input.jobId());
    s.em.lock(job, LockModeType.PESSIMISTIC_WRITE);
    ProcessingJobItem item = s.get(ProcessingJobItem.class, input.itemId());
    if (!"RUNNING".equals(job.getStatus()) || item.getAttemptCount() != input.attempt()) return;
    item.setStatus("FAILED");
    item.setErrorCode(
        error instanceof ApiException e ? Integer.toString(e.getCode()) : "PROCESSING_FAILED");
    item.setErrorMessage(
        error instanceof ApiException ? error.getMessage() : "图片处理失败，请使用traceId排查");
    item.setFinishedAt(Instant.now());
    finish(job);
  }

  private void finish(ProcessingJob job) {
    List<ProcessingJobItem> items = items(job);
    if (items.stream().anyMatch(i -> List.of("QUEUED", "RUNNING").contains(i.getStatus()))) return;
    long success = items.stream().filter(i -> "SUCCEEDED".equals(i.getStatus())).count();
    job.setStatus(
        success == items.size() ? "SUCCEEDED" : success == 0 ? "FAILED" : "PARTIAL_SUCCESS");
    job.setFinishedAt(Instant.now());
  }

  public Object dto(ProcessingJob job) {
    return map(
        "id",
        job.getId(),
        "studentId",
        job.getStudentId(),
        "batchId",
        job.getBatchId(),
        "operation",
        job.getOperation(),
        "applyScope",
        job.getApplyScope(),
        "status",
        job.getStatus(),
        "traceId",
        job.getTraceId(),
        "items",
        items(job).stream()
            .map(
                i ->
                    map(
                        "id",
                        i.getId(),
                        "photoId",
                        i.getPhotoId(),
                        "inputRevisionId",
                        i.getInputRevisionId(),
                        "outputRevisionId",
                        i.getOutputRevisionId(),
                        "status",
                        i.getStatus(),
                        "attemptCount",
                        i.getAttemptCount(),
                        "stepResults",
                        s.decode(i.getStepResultsJson()),
                        "errorCode",
                        i.getErrorCode(),
                        "errorMessage",
                        i.getErrorMessage()))
            .toList(),
        "pollAfterMs",
        1500,
        "createdAt",
        job.getCreatedAt(),
        "finishedAt",
        job.getFinishedAt());
  }
}
