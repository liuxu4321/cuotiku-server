package com.yingying.cuotiku.server.mini;

import static com.yingying.cuotiku.server.mini.MiniSupport.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.yingying.cuotiku.server.entity.*;
import com.yingying.cuotiku.server.web.ApiException;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 打印任务的创建与状态机。READY代表PDF生成，只有用户确认才记实际打印。 */
@Service
@Transactional
public class MiniPrintService {
  private final MiniSupport s;
  private final MiniTemplateService templates;
  private final MiniSourceService sources;
  private final MiniPaperService papers;
  private final MiniIdempotency receipts;
  private final MiniOutboxService outbox;
  private final MiniAssetService assets;

  public MiniPrintService(
      MiniSupport s,
      MiniTemplateService templates,
      MiniSourceService sources,
      MiniPaperService papers,
      MiniIdempotency receipts,
      MiniOutboxService outbox,
      MiniAssetService assets) {
    this.s = s;
    this.templates = templates;
    this.sources = sources;
    this.papers = papers;
    this.receipts = receipts;
    this.outbox = outbox;
    this.assets = assets;
  }

  public PrintTask task(User user, String student, String id) {
    PrintTask row = s.owned(PrintTask.class, id, user, student);
    if (row.getDeletedAt() != null) throw ApiException.notFound("任务不存在");
    return row;
  }

  public Object create(User user, String student, JsonNode body) {
    s.activeStudent(user, student);
    return receipts.once(
        user,
        "PRINT_TASK",
        required(body, "clientRequestId", 64),
        map("studentId", student, "body", body),
        () -> {
          String versionId = required(body, "templateVersionId", 36);
          PrintTemplateVersion version = s.get(PrintTemplateVersion.class, versionId);
          templates.version(version.getTemplateId(), versionId, true);
          PrintTemplate template = templates.published(version.getTemplateId());
          String draftId = text(body, "draftId");
          List<JsonNode> inputs = new ArrayList<>();
          PaperDraft draft = null;
          String sourceType =
              oneOf(required(body, "sourceType", 16), "COLLECTION", "CAPTURE", "RANDOM");
          if (draftId != null) {
            if (body.has("sources")) throw ApiException.badRequest("sources与draftId互斥");
            draft = papers.draft(user, student, draftId);
            s.em.lock(draft, LockModeType.PESSIMISTIC_WRITE);
            papers.version(draft, integer(body, "draftVersion", -1, 0, Integer.MAX_VALUE));
            if (!sourceType.equals(draft.getSourceType()))
              throw ApiException.badRequest("草稿来源类型不匹配");
            for (PaperDraftItem item : papers.items(draft)) inputs.add(papers.input(item));
            if (inputs.isEmpty()) throw ApiException.badRequest("请选择错题");
          } else inputs.addAll(array(body, "sources", 100));
          PrintTask task = new PrintTask();
          task.setTaskNo("P" + UUID.randomUUID().toString().replace("-", ""));
          task.setUserId(user.getId());
          task.setStudentId(student);
          task.setTemplateVersionId(versionId);
          Map<String, Object> snapshot = templates.versionDto(version, true);
          snapshot.put("templateName", template.getName());
          task.setTemplateSnapshotJson(s.encode(snapshot));
          task.setSourceType(sourceType);
          task.setItemCount(inputs.size());
          task.setCopies(integer(body, "copies", 1, 1, 20));
          task.setClientRequestId(required(body, "clientRequestId", 64));
          task.setRequestHash(s.hash(body));
          s.save(task);
          Set<String> unique = new HashSet<>();
          for (int i = 0; i < inputs.size(); i++) {
            MiniSourceService.Source source = sources.resolve(user, student, inputs.get(i));
            if (!unique.add(source.type() + ":" + source.id()))
              throw ApiException.badRequest("打印来源重复");
            PrintTaskItem item = new PrintTaskItem();
            item.setTaskId(task.getId());
            item.setSortOrder(i);
            item.setSourceEntryId(source.type().equals("ENTRY") ? source.id() : null);
            item.setSourceRegionId(source.type().equals("REGION") ? source.id() : null);
            item.setSourcePhotoId(source.type().equals("PHOTO") ? source.id() : null);
            item.setImageAssetId(source.asset().getId());
            Map<String, Object> content = new LinkedHashMap<>(source.content());
            content.put("inputRevisionId", source.revisionId());
            content.put("sourceType", source.type());
            content.put("sourceId", source.id());
            item.setContentSnapshotJson(s.encode(content));
            s.save(item);
          }
          if (draft != null) draft.setStatus("SUBMITTED");
          event(task, "CREATED", "USER", user.getId().toString(), null, "QUEUED", empty());
          outbox.enqueue("PRINT_TASK", task.getId(), "RENDER_REQUESTED");
          return dto(task);
        });
  }

  @Transactional(readOnly = true)
  public Object list(User user, String student, Map<String, String> query) {
    s.student(user, student);
    Map<String, Object> f = map("userId", user.getId(), "studentId", student, "deletedAt", null);
    if (query.containsKey("status"))
      f.put(
          "status",
          oneOf(
              query.get("status"),
              "QUEUED",
              "RENDERING",
              "READY",
              "FAILED",
              "CANCELLED",
              "PRINT_CONFIRMED"));
    return s.page(PrintTask.class, f, query, "createdAt", true, this::dto);
  }

  @Transactional(readOnly = true)
  public Object get(User user, String student, String id) {
    PrintTask task = task(user, student, id);
    return detail(task);
  }

  /** 管理查询保留用户隐藏后的历史；调用方须已验证ADMIN权限。 */
  @Transactional(readOnly = true)
  public Object detail(PrintTask task) {
    Map<String, Object> dto = dto(task);
    dto.put("templateSnapshot", s.decode(task.getTemplateSnapshotJson()));
    List<PrintTaskItem> items = items(task);
    dto.put(
        "items",
        items.stream()
            .map(
                i ->
                    map(
                        "id",
                        i.getId(),
                        "sortOrder",
                        i.getSortOrder(),
                        "sourceEntryId",
                        i.getSourceEntryId(),
                        "sourceRegionId",
                        i.getSourceRegionId(),
                        "sourcePhotoId",
                        i.getSourcePhotoId(),
                        "imageAssetId",
                        i.getImageAssetId(),
                        "contentSnapshot",
                        s.decode(i.getContentSnapshotJson())))
            .toList());
    return dto;
  }

  public Object retryRequest(User user, String student, String id, JsonNode body) {
    return receipts.once(
        user,
        "PRINT_RETRY",
        required(body, "clientRequestId", 64),
        map("studentId", student, "id", id, "body", body),
        () -> retry(user, student, id, "USER", null));
  }

  public Object confirmRequest(User user, String student, String id, JsonNode body) {
    return receipts.once(
        user,
        "PRINT_CONFIRM",
        required(body, "clientRequestId", 64),
        map("studentId", student, "id", id, "body", body),
        () -> confirm(user, student, id));
  }

  public Object retry(User user, String student, String id, String actor, String reason) {
    return retry(user, student, id, actor, reason, user.getId().toString());
  }

  public Object retry(
      User user, String student, String id, String actor, String reason, String actorId) {
    PrintTask task = task(user, student, id);
    s.em.lock(task, LockModeType.PESSIMISTIC_WRITE);
    if (!"FAILED".equals(task.getStatus())) throw ApiException.conflict("仅失败任务可重试");
    change(task, "QUEUED", "RETRIED", actor, actorId, map("reason", reason));
    task.setErrorCode(null);
    task.setErrorMessage(null);
    task.setFinishedAt(null);
    outbox.enqueue("PRINT_TASK", id, "RENDER_REQUESTED");
    return dto(task);
  }

  public Object cancel(User user, String student, String id) {
    PrintTask task = task(user, student, id);
    s.em.lock(task, LockModeType.PESSIMISTIC_WRITE);
    if ("CANCELLED".equals(task.getStatus())) return dto(task);
    if (!List.of("QUEUED", "RENDERING").contains(task.getStatus()))
      throw ApiException.conflict("当前状态不能取消");
    change(task, "CANCELLED", "CANCELLED", "USER", user.getId().toString(), empty());
    task.setFinishedAt(Instant.now());
    return dto(task);
  }

  @Transactional(readOnly = true)
  public Object download(User user, String student, String id) {
    PrintTask task = task(user, student, id);
    if (!List.of("READY", "PRINT_CONFIRMED").contains(task.getStatus())
        || task.getOutputPdfAssetId() == null) throw ApiException.conflict("PDF尚未生成");
    return assets.access(assets.available(user, student, task.getOutputPdfAssetId()), "ATTACHMENT");
  }

  public Object confirm(User user, String student, String id) {
    PrintTask task = task(user, student, id);
    s.em.lock(task, LockModeType.PESSIMISTIC_WRITE);
    if ("PRINT_CONFIRMED".equals(task.getStatus())) return dto(task);
    if (!"READY".equals(task.getStatus())) throw ApiException.conflict("仅已生成PDF的任务可确认打印");
    task.setPrintConfirmedAt(Instant.now());
    change(task, "PRINT_CONFIRMED", "CONFIRMED", "USER", user.getId().toString(), empty());
    return dto(task);
  }

  public Object hide(User user, String student, String id) {
    PrintTask task = task(user, student, id);
    if (List.of("QUEUED", "RENDERING").contains(task.getStatus()))
      throw ApiException.conflict("请先取消运行中的任务");
    task.setDeletedAt(Instant.now());
    return empty();
  }

  @Transactional(readOnly = true)
  public Object events(User user, String student, String id, Map<String, String> query) {
    task(user, student, id);
    return s.page(
        PrintTaskEvent.class,
        map("taskId", id),
        query,
        "occurredAt",
        false,
        r ->
            map(
                "id",
                r.getId(),
                "eventType",
                r.getEventType(),
                "fromStatus",
                r.getFromStatus(),
                "toStatus",
                r.getToStatus(),
                "occurredAt",
                r.getOccurredAt()));
  }

  public List<PrintTaskItem> items(PrintTask task) {
    List<PrintTaskItem> items = s.find(PrintTaskItem.class, map("taskId", task.getId()));
    items.sort(Comparator.comparing(PrintTaskItem::getSortOrder));
    return items;
  }

  private void event(
      PrintTask task,
      String type,
      String actor,
      String actorId,
      String from,
      String to,
      Object data) {
    PrintTaskEvent event = new PrintTaskEvent();
    event.setTaskId(task.getId());
    event.setEventType(type);
    event.setActorType(actor);
    event.setActorId(actorId);
    event.setFromStatus(from);
    event.setToStatus(to);
    event.setMetadataJson(s.encode(data));
    event.setOccurredAt(Instant.now());
    s.save(event);
  }

  private void change(
      PrintTask task, String status, String type, String actor, String actorId, Object data) {
    String from = task.getStatus();
    task.setStatus(status);
    event(task, type, actor, actorId, from, status, data);
  }

  public record RenderInput(
      String taskId,
      Long userId,
      String studentId,
      JsonNode snapshot,
      List<String> assetIds,
      int copies) {}

  public RenderInput beginRender(String id) {
    PrintTask task = s.get(PrintTask.class, id);
    s.em.lock(task, LockModeType.PESSIMISTIC_WRITE);
    if (!List.of("QUEUED", "RENDERING").contains(task.getStatus())) return null;
    if ("QUEUED".equals(task.getStatus()))
      change(task, "RENDERING", "RENDER_STARTED", "SYSTEM", null, empty());
    return new RenderInput(
        id,
        task.getUserId(),
        task.getStudentId(),
        s.decode(task.getTemplateSnapshotJson()),
        items(task).stream().map(PrintTaskItem::getImageAssetId).toList(),
        task.getCopies());
  }

  public void rendered(String id, byte[] pdf, int pages) {
    PrintTask task = s.get(PrintTask.class, id);
    s.em.lock(task, LockModeType.PESSIMISTIC_WRITE);
    if (!"RENDERING".equals(task.getStatus())) return;
    MediaAsset asset =
        assets.store(
            s.get(User.class, task.getUserId()),
            task.getStudentId(),
            pdf,
            "PRINT_PDF",
            "application/pdf");
    task.setOutputPdfAssetId(asset.getId());
    task.setPageCount(pages);
    task.setFinishedAt(Instant.now());
    change(task, "READY", "PDF_READY", "SYSTEM", null, empty());
  }

  public void renderFailed(String id, String message) {
    PrintTask task = s.get(PrintTask.class, id);
    s.em.lock(task, LockModeType.PESSIMISTIC_WRITE);
    if (!"RENDERING".equals(task.getStatus())) return;
    task.setErrorCode("RENDER_FAILED");
    task.setErrorMessage(
        message == null ? "PDF生成失败" : message.substring(0, Math.min(message.length(), 500)));
    task.setFinishedAt(Instant.now());
    change(task, "FAILED", "RENDER_FAILED", "SYSTEM", null, empty());
  }

  public Map<String, Object> dto(PrintTask task) {
    JsonNode snapshot = s.decode(task.getTemplateSnapshotJson());
    return map(
        "id",
        task.getId(),
        "studentId",
        task.getStudentId(),
        "templateVersionId",
        task.getTemplateVersionId(),
        "templateName",
        snapshot.path("templateName").asText(),
        "sourceType",
        task.getSourceType(),
        "status",
        task.getStatus(),
        "itemCount",
        task.getItemCount(),
        "copies",
        task.getCopies(),
        "pageCount",
        task.getPageCount(),
        "outputPdfAssetId",
        task.getOutputPdfAssetId(),
        "errorCode",
        task.getErrorCode(),
        "errorMessage",
        task.getErrorMessage(),
        "createdAt",
        task.getCreatedAt(),
        "finishedAt",
        task.getFinishedAt(),
        "printConfirmedAt",
        task.getPrintConfirmedAt(),
        "pollAfterMs",
        1500);
  }
}
