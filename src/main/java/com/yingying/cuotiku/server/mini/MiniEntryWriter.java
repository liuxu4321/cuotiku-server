package com.yingying.cuotiku.server.mini;

import static com.yingying.cuotiku.server.mini.MiniSupport.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.yingying.cuotiku.server.entity.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 每项独立提交，批量保存允许部分失败；回执与错题必须在同一个事务内。 */
@Service
public class MiniEntryWriter {
  private final MiniSupport s;
  private final MiniTaxonomyService taxonomy;
  private final MiniSourceService sources;
  private final MiniIdempotency receipts;

  public MiniEntryWriter(
      MiniSupport s,
      MiniTaxonomyService taxonomy,
      MiniSourceService sources,
      MiniIdempotency receipts) {
    this.s = s;
    this.taxonomy = taxonomy;
    this.sources = sources;
    this.receipts = receipts;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Object save(User user, String student, JsonNode body, JsonNode item) {
    String clientId = required(item, "clientId", 64);
    String key = s.hash(map("requestId", required(body, "requestId", 64), "clientId", clientId));
    Object result =
        receipts.once(
            user,
            "ENTRY_SAVE",
            key,
            map(
                "studentId",
                student,
                "subjectId",
                body.get("subjectId"),
                "topicId",
                body.get("topicId"),
                "errorTypeId",
                body.get("errorTypeId"),
                "item",
                item),
            () -> {
              StudentProfile profile = s.activeStudent(user, student);
              UserSubject subject =
                  taxonomy.subject(user, student, required(body, "subjectId", 64), true);
              String topicId = text(body, "topicId");
              SubjectTopic topic =
                  topicId == null ? null : taxonomy.topic(user, student, topicId, true);
              if (topic != null && !topic.getSubjectId().equals(subject.getId()))
                throw com.yingying.cuotiku.server.web.ApiException.badRequest("主题不属于当前科目");
              ErrorType error =
                  taxonomy.error(user, student, required(body, "errorTypeId", 36), true);
              oneOf(required(item, "sourceType", 16), "PHOTO", "REGION");
              MiniSourceService.Source source = sources.resolve(user, student, item);
              BookEntry entry = new BookEntry();
              entry.setId(UUID.randomUUID().toString());
              entry.setUserId(user.getId());
              entry.setStudentId(student);
              entry.setGrade(profile.getGrade());
              entry.setTerm(profile.getTerm());
              entry.setSubjectId(subject.getId());
              entry.setTopicId(topic == null ? null : topic.getId());
              entry.setSubject(subject.getName());
              entry.setErrorTypeId(error.getId());
              entry.setErrorType(error.getName());
              entry.setSourcePhotoId(source.photoId());
              entry.setSourceRegionId(source.type().equals("REGION") ? source.id() : null);
              entry.setImageRevisionId(source.revisionId());
              entry.setImageAssetId(source.asset().getId());
              entry.setObjectKey(source.asset().getObjectKey());
              entry.setWidth(source.asset().getWidth());
              entry.setHeight(source.asset().getHeight());
              entry.setFormat(source.asset().getFormat());
              entry.setHasThumb(false);
              entry.setSizeBytes(source.asset().getSizeBytes());
              entry.setRemark(optionalContent(item, "remark"));
              entry.setAnswer(optionalContent(item, "answer"));
              entry.setMasteryStatus("UNPRACTICED");
              entry.setCorrectCount(0);
              entry.setCreatedAt(Instant.now());
              s.save(entry);
              return map(
                  "clientId",
                  clientId,
                  "status",
                  "SUCCESS",
                  "entryId",
                  entry.getId(),
                  "code",
                  0,
                  "message",
                  "ok");
            });
    if (result instanceof JsonNode node) {
      Map<String, Object> replay = s.json.convertValue(node, Map.class);
      replay.put("status", "DUPLICATE");
      return replay;
    }
    return result;
  }

  static String optionalContent(JsonNode item, String key) {
    String value = text(item, key);
    if (value != null && value.length() > 1000)
      throw com.yingying.cuotiku.server.web.ApiException.badRequest(key + "最长1000字符");
    return value == null || value.isBlank() ? null : value;
  }
}
