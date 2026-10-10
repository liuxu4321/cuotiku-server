package com.yingying.cuotiku.server.mini;

import static com.yingying.cuotiku.server.mini.MiniSupport.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.yingying.cuotiku.server.entity.*;
import com.yingying.cuotiku.server.service.NameNormalizer;
import com.yingying.cuotiku.server.web.ApiException;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 每个学生拥有独立科目、主题、错误类型；停用保留历史，删除必须无引用。 */
@Service
@Transactional
public class MiniTaxonomyService {
  private final MiniSupport s;

  public MiniTaxonomyService(MiniSupport s) {
    this.s = s;
  }

  private void lock(User user, String studentId) {
    s.em.lock(s.activeStudent(user, studentId), LockModeType.PESSIMISTIC_WRITE);
  }

  private Map<String, Object> scope(User user, String id) {
    return map("userId", user.getId(), "studentId", id, "deletedAt", null);
  }

  public UserSubject subject(User user, String student, String id, boolean active) {
    UserSubject row = s.owned(UserSubject.class, longId(id), user, student);
    if (row.getDeletedAt() != null) throw ApiException.notFound("科目不存在");
    if (active && !"ACTIVE".equals(row.getStatus())) throw ApiException.conflict("科目已停用");
    return row;
  }

  public SubjectTopic topic(User user, String student, String id, boolean active) {
    SubjectTopic row = s.owned(SubjectTopic.class, longId(id), user, student);
    if (row.getDeletedAt() != null) throw ApiException.notFound("主题不存在");
    if (active && !"ACTIVE".equals(row.getStatus())) throw ApiException.conflict("主题已停用");
    subject(user, student, row.getSubjectId().toString(), active);
    return row;
  }

  public ErrorType error(User user, String student, String id, boolean active) {
    ErrorType row = s.owned(ErrorType.class, id, user, student);
    if (row.getDeletedAt() != null) throw ApiException.notFound("错误类型不存在");
    if (active && !"ACTIVE".equals(row.getStatus())) throw ApiException.conflict("错误类型已停用");
    return row;
  }

  @Transactional(readOnly = true)
  public Object list(
      User user, String student, String kind, String parent, Map<String, String> query) {
    s.student(user, student);
    Map<String, Object> f = scope(user, student);
    if (query.containsKey("status"))
      f.put("status", oneOf(query.get("status"), "ACTIVE", "ARCHIVED"));
    if (kind.equals("topic")) {
      subject(user, student, parent, false);
      f.put("subjectId", longId(parent));
    }
    return switch (kind) {
      case "subject" -> s.page(UserSubject.class, f, query, "sortOrder", false, v -> subjectDto(v));
      case "topic" -> s.page(SubjectTopic.class, f, query, "sortOrder", false, v -> topicDto(v));
      default -> s.page(ErrorType.class, f, query, "sortOrder", false, v -> errorDto(v));
    };
  }

  public Object create(User user, String student, String kind, String parent, JsonNode body) {
    lock(user, student);
    String name = NameNormalizer.validate(required(body, "name", 64), "分类");
    int order = integer(body, "sortOrder", 0, 0, 100000);
    if (kind.equals("subject")) {
      unique(UserSubject.class, scope(user, student), name, null);
      UserSubject row = new UserSubject();
      row.setUserId(user.getId());
      row.setStudentId(student);
      row.setName(name);
      row.setNormalizedName(NameNormalizer.normalized(name));
      row.setSortOrder(order);
      s.save(row);
      return subjectDto(row);
    }
    if (kind.equals("topic")) {
      UserSubject parentRow = subject(user, student, parent, true);
      Map<String, Object> f = scope(user, student);
      f.put("subjectId", parentRow.getId());
      unique(SubjectTopic.class, f, name, null);
      SubjectTopic row = new SubjectTopic();
      row.setUserId(user.getId());
      row.setStudentId(student);
      row.setSubjectId(parentRow.getId());
      row.setName(name);
      row.setNormalizedName(NameNormalizer.normalized(name));
      row.setSortOrder(order);
      s.save(row);
      return topicDto(row);
    }
    unique(ErrorType.class, scope(user, student), name, null);
    ErrorType row = new ErrorType();
    row.setUserId(user.getId());
    row.setStudentId(student);
    row.setCode("CUSTOM_" + UUID.randomUUID());
    row.setName(name);
    row.setNormalizedName(NameNormalizer.normalized(name));
    row.setSortOrder(order);
    row.setDrawGroupCode(
        oneOf(
            Optional.ofNullable(text(body, "drawGroupCode")).orElse("OTHER"),
            "CARELESS",
            "UNFAMILIAR",
            "CONCEPT",
            "OTHER"));
    s.save(row);
    return errorDto(row);
  }

  private <T> void unique(Class<T> type, Map<String, Object> filters, String name, Object except) {
    filters.put("normalizedName", NameNormalizer.normalized(name));
    for (T row : s.find(type, filters)) {
      Object id = s.em.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(row);
      if (!Objects.equals(id, except)) throw new ApiException(4093, "同一范围已存在同名分类");
    }
  }

  public Object update(User user, String student, String kind, String id, JsonNode body) {
    lock(user, student);
    String name =
        body.has("name") ? NameNormalizer.validate(required(body, "name", 64), "分类") : null;
    String status =
        body.has("status") ? oneOf(required(body, "status", 16), "ACTIVE", "ARCHIVED") : null;
    if (kind.equals("subject")) {
      UserSubject row = subject(user, student, id, false);
      revision(row.getRevision(), body);
      if (name != null) {
        unique(UserSubject.class, scope(user, student), name, row.getId());
        row.setName(name);
        row.setNormalizedName(NameNormalizer.normalized(name));
      }
      if (status != null) row.setStatus(status);
      if (body.has("sortOrder")) row.setSortOrder(integer(body, "sortOrder", 0, 0, 100000));
      row.setRevision(row.getRevision() + 1);
      s.em.flush();
      return subjectDto(row);
    }
    if (kind.equals("topic")) {
      SubjectTopic row = topic(user, student, id, false);
      revision(row.getRevision(), body);
      if (name != null) {
        Map<String, Object> f = scope(user, student);
        f.put("subjectId", row.getSubjectId());
        unique(SubjectTopic.class, f, name, row.getId());
        row.setName(name);
        row.setNormalizedName(NameNormalizer.normalized(name));
      }
      if (status != null) row.setStatus(status);
      if (body.has("sortOrder")) row.setSortOrder(integer(body, "sortOrder", 0, 0, 100000));
      row.setRevision(row.getRevision() + 1);
      s.em.flush();
      return topicDto(row);
    }
    ErrorType row = error(user, student, id, false);
    revision(row.getRevision(), body);
    if (name != null) {
      unique(ErrorType.class, scope(user, student), name, row.getId());
      row.setName(name);
      row.setNormalizedName(NameNormalizer.normalized(name));
    }
    if (status != null) row.setStatus(status);
    if (body.has("drawGroupCode"))
      row.setDrawGroupCode(
          oneOf(required(body, "drawGroupCode", 16), "CARELESS", "UNFAMILIAR", "CONCEPT", "OTHER"));
    if (body.has("sortOrder")) row.setSortOrder(integer(body, "sortOrder", 0, 0, 100000));
    row.setRevision(row.getRevision() + 1);
    s.em.flush();
    return errorDto(row);
  }

  public Object order(User user, String student, String kind, String parent, JsonNode body) {
    lock(user, student);
    List<JsonNode> items = array(body, "items", 1000);
    Set<String> seen = new HashSet<>();
    Map<String, Object> scope = scope(user, student);
    if (kind.equals("topic")) {
      subject(user, student, parent, true);
      scope.put("subjectId", longId(parent));
    }
    Class<?> type =
        kind.equals("subject")
            ? UserSubject.class
            : kind.equals("topic") ? SubjectTopic.class : ErrorType.class;
    if (s.count(type, scope) != items.size()) throw ApiException.badRequest("排序必须包含当前范围全部分类");
    List<Object> result = new ArrayList<>();
    for (int i = 0; i < items.size(); i++) {
      JsonNode item = items.get(i);
      String id = required(item, "id", 64);
      if (!seen.add(id)) throw ApiException.badRequest("排序ID重复");
      if (kind.equals("subject")) {
        UserSubject row = subject(user, student, id, false);
        revision(row.getRevision(), item);
        row.setSortOrder(i);
        row.setRevision(row.getRevision() + 1);
        result.add(subjectDto(row));
      } else if (kind.equals("topic")) {
        SubjectTopic row = topic(user, student, id, false);
        if (!row.getSubjectId().equals(longId(parent))) throw ApiException.notFound("主题不存在");
        revision(row.getRevision(), item);
        row.setSortOrder(i);
        row.setRevision(row.getRevision() + 1);
        result.add(topicDto(row));
      } else {
        ErrorType row = error(user, student, id, false);
        revision(row.getRevision(), item);
        row.setSortOrder(i);
        row.setRevision(row.getRevision() + 1);
        result.add(errorDto(row));
      }
    }
    s.em.flush();
    return map("items", result);
  }

  public Object delete(User user, String student, String kind, String id) {
    lock(user, student);
    Map<String, Object> f = scope(user, student);
    Object row;
    if (kind.equals("subject")) {
      row = subject(user, student, id, false);
      f.put("subjectId", longId(id));
      if (s.count(SubjectTopic.class, f) > 0) throw new ApiException(4092, "科目下仍有主题，请改为停用");
    } else if (kind.equals("topic")) {
      row = topic(user, student, id, false);
      f.put("topicId", longId(id));
    } else {
      row = error(user, student, id, false);
      f.put("errorTypeId", id);
    }
    f.remove("deletedAt");
    if (s.count(BookEntry.class, f) > 0) throw new ApiException(4092, "分类已有错题引用，请改为停用");
    // 无引用才物理删除，避免唯一名称占位及历史外键悬空。
    List<StudentTaxonomyPref> prefs =
        s.find(StudentTaxonomyPref.class, map("userId", user.getId(), "studentId", student));
    for (StudentTaxonomyPref p : prefs) {
      if (kind.equals("subject") && Objects.equals(p.getDefaultSubjectId(), longId(id))) {
        p.setDefaultSubjectId(null);
        p.setDefaultTopicId(null);
      }
      if (kind.equals("topic") && Objects.equals(p.getDefaultTopicId(), longId(id)))
        p.setDefaultTopicId(null);
    }
    s.em.remove(row);
    return empty();
  }

  @Transactional(readOnly = true)
  public Object preferences(User user, String student) {
    StudentProfile row = s.student(user, student);
    List<StudentTaxonomyPref> prefs =
        s.find(StudentTaxonomyPref.class, map("userId", user.getId(), "studentId", student));
    StudentTaxonomyPref p = prefs.isEmpty() ? null : prefs.get(0);
    return map(
        "defaultSubjectId",
        p == null || p.getDefaultSubjectId() == null ? null : p.getDefaultSubjectId().toString(),
        "defaultTopicId",
        p == null || p.getDefaultTopicId() == null ? null : p.getDefaultTopicId().toString(),
        "defaultTemplateId",
        row.getDefaultTemplateId());
  }

  public Object preferences(User user, String student, JsonNode body) {
    lock(user, student);
    String subject = text(body, "defaultSubjectId"),
        topic = text(body, "defaultTopicId"),
        template = text(body, "defaultTemplateId");
    if (subject != null) subject(user, student, subject, true);
    if (topic != null) {
      SubjectTopic t = topic(user, student, topic, true);
      if (subject == null || !t.getSubjectId().equals(longId(subject)))
        throw ApiException.badRequest("默认主题必须属于默认科目");
    }
    if (template != null) {
      PrintTemplate t = s.get(PrintTemplate.class, template);
      if (!"PUBLISHED".equals(t.getStatus()) || t.getCurrentVersionId() == null)
        throw ApiException.conflict("模板不可用");
    }
    List<StudentTaxonomyPref> prefs =
        s.find(StudentTaxonomyPref.class, map("userId", user.getId(), "studentId", student));
    StudentTaxonomyPref p = prefs.isEmpty() ? new StudentTaxonomyPref() : prefs.get(0);
    if (p.getId() == null) {
      p.setUserId(user.getId());
      p.setStudentId(student);
      s.em.persist(p);
    }
    p.setDefaultSubjectId(subject == null ? null : longId(subject));
    p.setDefaultTopicId(topic == null ? null : longId(topic));
    s.student(user, student).setDefaultTemplateId(template);
    return preferences(user, student);
  }

  public Object subjectDto(UserSubject row) {
    Map<String, Object> f =
        map(
            "userId",
            row.getUserId(),
            "studentId",
            row.getStudentId(),
            "subjectId",
            row.getId(),
            "deletedAt",
            null);
    return map(
        "id",
        row.getId().toString(),
        "studentId",
        row.getStudentId(),
        "name",
        row.getName(),
        "systemKey",
        row.getSystemKey(),
        "status",
        row.getStatus(),
        "sortOrder",
        row.getSortOrder(),
        "revision",
        row.getRevision(),
        "topicCount",
        s.count(SubjectTopic.class, f),
        "entryCount",
        s.count(BookEntry.class, f),
        "updatedAt",
        row.getUpdatedAt());
  }

  public Object topicDto(SubjectTopic row) {
    return map(
        "id",
        row.getId().toString(),
        "studentId",
        row.getStudentId(),
        "subjectId",
        row.getSubjectId().toString(),
        "name",
        row.getName(),
        "status",
        row.getStatus(),
        "sortOrder",
        row.getSortOrder(),
        "revision",
        row.getRevision(),
        "entryCount",
        s.count(
            BookEntry.class,
            map(
                "userId",
                row.getUserId(),
                "studentId",
                row.getStudentId(),
                "topicId",
                row.getId(),
                "deletedAt",
                null)),
        "updatedAt",
        row.getUpdatedAt());
  }

  public Object errorDto(ErrorType row) {
    return map(
        "id",
        row.getId(),
        "studentId",
        row.getStudentId(),
        "code",
        row.getCode(),
        "name",
        row.getName(),
        "drawGroupCode",
        row.getDrawGroupCode(),
        "status",
        row.getStatus(),
        "sortOrder",
        row.getSortOrder(),
        "revision",
        row.getRevision(),
        "entryCount",
        s.count(
            BookEntry.class,
            map(
                "userId",
                row.getUserId(),
                "studentId",
                row.getStudentId(),
                "errorTypeId",
                row.getId(),
                "deletedAt",
                null)),
        "updatedAt",
        row.getUpdatedAt());
  }
}
