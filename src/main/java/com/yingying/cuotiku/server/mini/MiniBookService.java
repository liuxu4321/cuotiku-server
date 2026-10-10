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

/** 错题查询、分类编辑与练习统计；乐观版本用于阻止覆盖其他页面的新修改。 */
@Service
public class MiniBookService {
  private final MiniSupport s;
  private final MiniSourceService sources;
  private final MiniEntryWriter writer;
  private final MiniTaxonomyService taxonomy;
  private final MiniIdempotency receipts;

  public MiniBookService(
      MiniSupport s,
      MiniSourceService sources,
      MiniEntryWriter writer,
      MiniTaxonomyService taxonomy,
      MiniIdempotency receipts) {
    this.s = s;
    this.sources = sources;
    this.writer = writer;
    this.taxonomy = taxonomy;
    this.receipts = receipts;
  }

  @Transactional(readOnly = true)
  public Object batch(User user, String student, JsonNode body) {
    s.activeStudent(user, student);
    List<JsonNode> items = array(body, "items", 100);
    required(body, "requestId", 64);
    Set<String> seen = new HashSet<>();
    for (JsonNode item : items)
      if (!seen.add(required(item, "clientId", 64))) throw ApiException.badRequest("clientId重复");
    List<Object> result = new ArrayList<>();
    for (JsonNode item : items) {
      try {
        result.add(writer.save(user, student, body, item));
      } catch (ApiException e) {
        result.add(
            map(
                "clientId",
                item.path("clientId").asText(),
                "status",
                "FAILED",
                "entryId",
                null,
                "code",
                e.getCode(),
                "message",
                e.getMessage()));
      }
    }
    return map("results", result);
  }

  @Transactional(readOnly = true)
  public Object list(User user, String student, Map<String, String> query) {
    s.student(user, student);
    Map<String, Object> f = map("userId", user.getId(), "studentId", student, "deletedAt", null);
    if (query.containsKey("subjectId"))
      f.put("subjectId", taxonomy.subject(user, student, query.get("subjectId"), false).getId());
    if (query.containsKey("topicId"))
      f.put("topicId", taxonomy.topic(user, student, query.get("topicId"), false).getId());
    if (query.containsKey("errorTypeId"))
      f.put("errorTypeId", taxonomy.error(user, student, query.get("errorTypeId"), false).getId());
    if (query.containsKey("masteryStatus"))
      f.put(
          "masteryStatus",
          oneOf(query.get("masteryStatus"), "UNPRACTICED", "PRACTICING", "MASTERED"));
    return s.page(BookEntry.class, f, query, "createdAt", true, sources::entryDto);
  }

  @Transactional(readOnly = true)
  public Object get(User user, String student, String id) {
    return sources.entryDto(sources.entry(user, student, id));
  }

  @Transactional
  public Object update(User user, String student, String id, JsonNode body) {
    BookEntry row = sources.entry(user, student, id);
    s.em.lock(row, LockModeType.PESSIMISTIC_WRITE);
    version(row, body);
    UserSubject subject =
        body.has("subjectId")
            ? taxonomy.subject(user, student, required(body, "subjectId", 64), true)
            : taxonomy.subject(user, student, row.getSubjectId().toString(), false);
    String topicId =
        body.has("topicId")
            ? text(body, "topicId")
            : row.getTopicId() == null ? null : row.getTopicId().toString();
    SubjectTopic topic =
        topicId == null ? null : taxonomy.topic(user, student, topicId, body.has("topicId"));
    if (topic != null && !topic.getSubjectId().equals(subject.getId()))
      throw ApiException.badRequest("主题不属于科目");
    row.setSubjectId(subject.getId());
    row.setSubject(subject.getName());
    row.setTopicId(topic == null ? null : topic.getId());
    if (body.has("errorTypeId")) {
      ErrorType error = taxonomy.error(user, student, required(body, "errorTypeId", 36), true);
      row.setErrorTypeId(error.getId());
      row.setErrorType(error.getName());
    }
    if (body.has("answer")) row.setAnswer(MiniEntryWriter.optionalContent(body, "answer"));
    if (body.has("remark")) row.setRemark(MiniEntryWriter.optionalContent(body, "remark"));
    if (body.has("masteryStatus"))
      row.setMasteryStatus(
          oneOf(required(body, "masteryStatus", 16), "UNPRACTICED", "PRACTICING", "MASTERED"));
    row.setUpdatedAt(Instant.now());
    s.em.flush();
    return sources.entryDto(row);
  }

  static void version(BookEntry row, JsonNode body) {
    if (integer(body, "version", -1, 0, Integer.MAX_VALUE) != row.getVersion())
      throw new ApiException(
          4091, "错题已更新，请刷新", map("current", map("id", row.getId(), "version", row.getVersion())));
  }

  @Transactional
  public Object delete(User user, String student, String id, Map<String, String> query) {
    BookEntry row = sources.entry(user, student, id);
    if (query.containsKey("version"))
      version(row, s.json.valueToTree(map("version", Integer.parseInt(query.get("version")))));
    row.setDeletedAt(Instant.now());
    return empty();
  }

  @Transactional
  public Object practice(User user, String student, String id, JsonNode body) {
    return receipts.once(
        user,
        "PRACTICE",
        required(body, "clientRequestId", 64),
        map("studentId", student, "entryId", id, "body", body),
        () -> {
          BookEntry entry = sources.entry(user, student, id);
          s.em.lock(entry, LockModeType.PESSIMISTIC_WRITE);
          if (!body.has("correct")) throw ApiException.badRequest("correct必填");
          BookPracticeRecord row = new BookPracticeRecord();
          row.setUserId(user.getId());
          row.setStudentId(student);
          row.setEntryId(id);
          row.setClientRequestId(required(body, "clientRequestId", 64));
          row.setMode(oneOf(required(body, "mode", 16), "PRACTICE", "ANALOGY"));
          row.setAnswerContent(MiniEntryWriter.optionalContent(body, "answerContent"));
          row.setCorrect(bool(body, "correct", false));
          row.setDurationSeconds(integer(body, "durationSeconds", 0, 0, 86400));
          String at = text(body, "practicedAt");
          try {
            row.setPracticedAt(at == null ? Instant.now() : Instant.parse(at));
            if (row.getPracticedAt().isAfter(Instant.now().plusSeconds(60)))
              throw new IllegalArgumentException();
          } catch (Exception e) {
            throw ApiException.badRequest("练习时间无效");
          }
          s.save(row);
          entry.setPracticeCount(entry.getPracticeCount() + 1);
          entry.setCorrectCount(
              (entry.getCorrectCount() == null ? 0 : entry.getCorrectCount())
                  + (row.isCorrect() ? 1 : 0));
          if (entry.getLastPracticedAt() == null
              || entry.getLastPracticedAt().isBefore(row.getPracticedAt()))
            entry.setLastPracticedAt(row.getPracticedAt());
          if (!"MASTERED".equals(entry.getMasteryStatus())) entry.setMasteryStatus("PRACTICING");
          s.em.flush();
          Map<String, Object> dto = practiceDto(row);
          dto.putAll(
              map(
                  "entryVersion",
                  entry.getVersion(),
                  "practiceCount",
                  entry.getPracticeCount(),
                  "correctCount",
                  entry.getCorrectCount(),
                  "masteryStatus",
                  entry.getMasteryStatus()));
          return dto;
        });
  }

  @Transactional(readOnly = true)
  public Object practices(User user, String student, String id, Map<String, String> query) {
    s.student(user, student);
    if (id != null) sources.entry(user, student, id);
    Map<String, Object> f = map("userId", user.getId(), "studentId", student);
    if (id != null) f.put("entryId", id);
    if (query.containsKey("correct")) {
      if (!List.of("true", "false").contains(query.get("correct")))
        throw ApiException.badRequest("correct格式错误");
      f.put("correct", Boolean.valueOf(query.get("correct")));
    }
    return s.page(BookPracticeRecord.class, f, query, "practicedAt", true, this::practiceDto);
  }

  public Map<String, Object> practiceDto(BookPracticeRecord row) {
    return map(
        "id",
        row.getId().toString(),
        "entryId",
        row.getEntryId(),
        "studentId",
        row.getStudentId(),
        "mode",
        row.getMode(),
        "answerContent",
        row.getAnswerContent(),
        "correct",
        row.isCorrect(),
        "durationSeconds",
        row.getDurationSeconds(),
        "practicedAt",
        row.getPracticedAt());
  }
}
