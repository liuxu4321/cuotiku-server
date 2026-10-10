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

/** 随机组卷与打印草稿。优先练习次数少的错题，草稿固定PHOTO输入版本。 */
@Service
@Transactional
public class MiniPaperService {
  private final MiniSupport s;
  private final MiniSourceService sources;
  private final MiniTaxonomyService taxonomy;
  private final MiniTemplateService templates;
  private final MiniIdempotency receipts;

  public MiniPaperService(
      MiniSupport s,
      MiniSourceService sources,
      MiniTaxonomyService taxonomy,
      MiniTemplateService templates,
      MiniIdempotency receipts) {
    this.s = s;
    this.sources = sources;
    this.taxonomy = taxonomy;
    this.templates = templates;
    this.receipts = receipts;
  }

  private Map<String, Object> filters(User user, String student, JsonNode body) {
    s.activeStudent(user, student);
    Map<String, Object> f = map("userId", user.getId(), "studentId", student, "deletedAt", null);
    String subject = text(body, "subjectId"), topic = text(body, "topicId");
    if (subject != null)
      f.put("subjectId", taxonomy.subject(user, student, subject, false).getId());
    if (topic != null) {
      SubjectTopic row = taxonomy.topic(user, student, topic, false);
      if (subject != null && !row.getSubjectId().equals(longId(subject)))
        throw ApiException.badRequest("主题不属于科目");
      f.put("topicId", row.getId());
    }
    return f;
  }

  @Transactional(readOnly = true)
  public Object availability(User user, String student, JsonNode body) {
    Map<String, Object> f = filters(user, student, body);
    List<ErrorType> types =
        s.find(
            ErrorType.class,
            map(
                "userId",
                user.getId(),
                "studentId",
                student,
                "deletedAt",
                null,
                "status",
                "ACTIVE"));
    types.sort(Comparator.comparing(ErrorType::getSortOrder));
    return map(
        "types",
        types.stream()
            .map(
                t -> {
                  Map<String, Object> filter = new LinkedHashMap<>(f);
                  filter.put("errorTypeId", t.getId());
                  return map(
                      "errorTypeId",
                      t.getId(),
                      "name",
                      t.getName(),
                      "availableCount",
                      s.count(BookEntry.class, filter));
                })
            .toList());
  }

  public Object random(User user, String student, JsonNode body) {
    return receipts.once(
        user,
        "RANDOM_PAPER",
        required(body, "clientRequestId", 64),
        map("studentId", student, "body", body),
        () -> {
          Map<String, Object> base = filters(user, student, body);
          List<JsonNode> counts = array(body, "counts", 100);
          Set<String> ids = new HashSet<>();
          List<Object> selected = new ArrayList<>(), shortages = new ArrayList<>();
          int requested = 0;
          for (JsonNode count : counts) {
            String errorId = required(count, "errorTypeId", 36);
            taxonomy.error(user, student, errorId, true);
            if (!ids.add(errorId)) throw ApiException.badRequest("错误类型重复");
            int quantity = integer(count, "count", -1, 0, 100);
            if (quantity < 0) throw ApiException.badRequest("count必填");
            requested += quantity;
            if (requested > 100) throw ApiException.badRequest("总题数最多100");
            if (quantity == 0) continue;
            var cb = s.em.getCriteriaBuilder();
            var q = cb.createQuery(BookEntry.class);
            var root = q.from(BookEntry.class);
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            base.forEach(
                (k, v) ->
                    predicates.add(v == null ? cb.isNull(root.get(k)) : cb.equal(root.get(k), v)));
            predicates.add(cb.equal(root.get("errorTypeId"), errorId));
            q.where(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
            q.orderBy(cb.asc(root.get("practiceCount")), cb.asc(cb.function("rand", Double.class)));
            List<BookEntry> rows = s.em.createQuery(q).setMaxResults(quantity).getResultList();
            for (BookEntry row : rows)
              selected.add(
                  map("sourceType", "ENTRY", "sourceId", row.getId(), "inputRevisionId", null));
            if (rows.size() < quantity)
              shortages.add(
                  map("errorTypeId", errorId, "requested", quantity, "actual", rows.size()));
          }
          return map(
              "studentId",
              student,
              "sourceType",
              "RANDOM",
              "sources",
              selected,
              "requestedCount",
              requested,
              "actualCount",
              selected.size(),
              "shortages",
              shortages);
        });
  }

  public Object createDraft(User user, String student, JsonNode body) {
    return receipts.once(
        user,
        "PAPER_DRAFT",
        required(body, "clientRequestId", 64),
        map("studentId", student, "body", body),
        () -> {
          PaperDraft draft = new PaperDraft();
          draft.setUserId(user.getId());
          draft.setStudentId(student);
          draft.setSourceType(
              oneOf(required(body, "sourceType", 16), "COLLECTION", "CAPTURE", "RANDOM"));
          draft.setExpiresAt(Instant.now().plusSeconds(86400));
          draft.setFilterSnapshotJson(s.encode(body.path("filterSnapshot")));
          s.save(draft);
          List<JsonNode> inputs = array(body, "sources", 100);
          Set<String> unique = new HashSet<>();
          for (int i = 0; i < inputs.size(); i++) {
            MiniSourceService.Source source = sources.resolve(user, student, inputs.get(i));
            if (!unique.add(source.type() + ":" + source.id()))
              throw ApiException.badRequest("来源重复");
            PaperDraftItem item = new PaperDraftItem();
            item.setDraftId(draft.getId());
            item.setSortOrder(i);
            item.setSourceEntryId(source.type().equals("ENTRY") ? source.id() : null);
            item.setSourceRegionId(source.type().equals("REGION") ? source.id() : null);
            item.setSourcePhotoId(source.type().equals("PHOTO") ? source.id() : null);
            item.setInputRevisionId(source.revisionId());
            s.save(item);
          }
          return draftDto(user, student, draft);
        });
  }

  public PaperDraft draft(User user, String student, String id) {
    PaperDraft draft = s.owned(PaperDraft.class, id, user, student);
    if (draft.getExpiresAt().isBefore(Instant.now())) throw new ApiException(410, "打印草稿已过期");
    if (!"ACTIVE".equals(draft.getStatus())) throw ApiException.conflict("草稿已提交或放弃");
    return draft;
  }

  public List<PaperDraftItem> items(PaperDraft draft) {
    List<PaperDraftItem> rows = s.find(PaperDraftItem.class, map("draftId", draft.getId()));
    rows.sort(Comparator.comparing(PaperDraftItem::getSortOrder));
    return rows;
  }

  public JsonNode input(PaperDraftItem item) {
    return s.json.valueToTree(
        map(
            "sourceType",
            item.getSourceEntryId() != null
                ? "ENTRY"
                : item.getSourceRegionId() != null ? "REGION" : "PHOTO",
            "sourceId",
            item.getSourceEntryId() != null
                ? item.getSourceEntryId()
                : item.getSourceRegionId() != null
                    ? item.getSourceRegionId()
                    : item.getSourcePhotoId(),
            "inputRevisionId",
            item.getInputRevisionId()));
  }

  @Transactional(readOnly = true)
  public Object getDraft(User user, String student, String id) {
    return draftDto(user, student, draft(user, student, id));
  }

  public void version(PaperDraft draft, int expected) {
    if (draft.getVersion() != expected)
      throw new ApiException(
          4091,
          "草稿已更新，请刷新",
          map("current", map("id", draft.getId(), "version", draft.getVersion())));
  }

  public Object updateDraft(User user, String student, String id, JsonNode body) {
    PaperDraft draft = draft(user, student, id);
    s.em.lock(draft, LockModeType.PESSIMISTIC_WRITE);
    version(draft, integer(body, "version", -1, 0, Integer.MAX_VALUE));
    String template = text(body, "selectedTemplateId");
    if (template != null) templates.published(template);
    draft.setSelectedTemplateId(template);
    s.em.flush();
    return draftDto(user, student, draft);
  }

  public Object order(User user, String student, String id, JsonNode body) {
    PaperDraft draft = draft(user, student, id);
    s.em.lock(draft, LockModeType.PESSIMISTIC_WRITE);
    version(draft, integer(body, "version", -1, 0, Integer.MAX_VALUE));
    List<JsonNode> ids = array(body, "itemIds", 100);
    List<PaperDraftItem> items = items(draft);
    if (ids.size() != items.size()) throw ApiException.badRequest("请提交完整题目顺序");
    int temporary = items.stream().mapToInt(PaperDraftItem::getSortOrder).max().orElse(0) + 1000;
    for (int i = 0; i < items.size(); i++) items.get(i).setSortOrder(temporary + i);
    s.em.flush();
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < ids.size(); i++) {
      String itemId = ids.get(i).asText();
      if (!seen.add(itemId)) throw ApiException.badRequest("题目ID重复");
      PaperDraftItem item = s.get(PaperDraftItem.class, itemId);
      if (!id.equals(item.getDraftId())) throw ApiException.notFound("草稿题目不存在");
      item.setSortOrder(i);
    }
    draft.setUpdatedAt(Instant.now());
    s.em.flush();
    return draftDto(user, student, draft);
  }

  public Object deleteItem(
      User user, String student, String id, String itemId, Map<String, String> query) {
    PaperDraft draft = draft(user, student, id);
    s.em.lock(draft, LockModeType.PESSIMISTIC_WRITE);
    version(draft, queryVersion(query));
    PaperDraftItem item = s.get(PaperDraftItem.class, itemId);
    if (!id.equals(item.getDraftId())) throw ApiException.notFound("草稿题目不存在");
    s.em.remove(item);
    draft.setUpdatedAt(Instant.now());
    s.em.flush();
    return draftDto(user, student, draft);
  }

  private int queryVersion(Map<String, String> query) {
    try {
      int version = Integer.parseInt(query.get("version"));
      if (version < 0) throw new IllegalArgumentException();
      return version;
    } catch (Exception e) {
      throw ApiException.badRequest("version必填且非负整数");
    }
  }

  public Object deleteDraft(User user, String student, String id, Map<String, String> query) {
    PaperDraft draft = draft(user, student, id);
    s.em.lock(draft, LockModeType.PESSIMISTIC_WRITE);
    version(draft, queryVersion(query));
    draft.setStatus("ABANDONED");
    return empty();
  }

  public Object draftDto(User user, String student, PaperDraft draft) {
    List<Object> items = new ArrayList<>();
    for (PaperDraftItem item : items(draft)) {
      JsonNode input = input(item);
      MiniSourceService.Source source = sources.resolve(user, student, input);
      items.add(
          map(
              "id",
              item.getId(),
              "sortOrder",
              item.getSortOrder(),
              "sourceType",
              source.type(),
              "sourceId",
              source.id(),
              "inputRevisionId",
              source.revisionId(),
              "imageAssetId",
              source.asset().getId(),
              "subjectName",
              name(source.content().get("subject")),
              "topicName",
              name(source.content().get("topic")),
              "errorTypeName",
              name(source.content().get("errorType"))));
    }
    return map(
        "id",
        draft.getId(),
        "studentId",
        student,
        "sourceType",
        draft.getSourceType(),
        "selectedTemplateId",
        draft.getSelectedTemplateId(),
        "items",
        items,
        "version",
        draft.getVersion(),
        "status",
        draft.getStatus(),
        "expiresAt",
        draft.getExpiresAt());
  }

  private Object name(Object value) {
    return value instanceof Map<?, ?> map ? map.get("name") : null;
  }
}
