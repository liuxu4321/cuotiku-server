package com.yingying.cuotiku.server.mini;

import static com.yingying.cuotiku.server.mini.MiniSupport.*;

import com.yingying.cuotiku.server.entity.*;
import com.yingying.cuotiku.server.service.AgentRuntimeService;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 云调用前后分别使用短事务；结果记录补充学生、题图版本和调用元数据。 */
@Service
public class MiniAgentStore {
  private final MiniSupport s;
  private final MiniSourceService sources;
  private final MiniAssetService assets;

  public MiniAgentStore(MiniSupport s, MiniSourceService sources, MiniAssetService assets) {
    this.s = s;
    this.sources = sources;
    this.assets = assets;
  }

  public record Input(
      String key,
      String contentHash,
      String revisionId,
      String assetId,
      byte[] bytes,
      String mime,
      Map<String, String> vars) {}

  @Transactional(readOnly = true)
  public Input input(User user, String student, String id, String type) {
    BookEntry entry = sources.entry(user, student, id);
    MediaAsset asset = assets.available(user, student, entry.getImageAssetId());
    Map<String, String> vars = new HashMap<>();
    vars.put("subject", s.get(UserSubject.class, entry.getSubjectId()).getName());
    vars.put(
        "topic",
        entry.getTopicId() == null ? "" : s.get(SubjectTopic.class, entry.getTopicId()).getName());
    vars.put("errorType", s.get(ErrorType.class, entry.getErrorTypeId()).getName());
    vars.put("grade", String.valueOf(entry.getGrade()));
    vars.put("term", entry.getTerm() == null ? "不限" : entry.getTerm() == 1 ? "上学期" : "下学期");
    vars.put("count", "3");
    String hash =
        s.hash(
            map("assetHash", asset.getChecksumSha256(), "vars", vars, "answer", entry.getAnswer()));
    AiAgentConfig config = s.get(AiAgentConfig.class, type);
    String configFingerprint =
        s.hash(
            map(
                "systemPrompt",
                config.getSystemPrompt(),
                "userPrompt",
                config.getUserPromptTemplate(),
                "model",
                config.getModel(),
                "temperature",
                config.getTemperature(),
                "maxTokens",
                config.getMaxTokens(),
                "configVersion",
                config.getConfigVersion()));
    String key =
        s.hash(
            map(
                "userId",
                user.getId(),
                "studentId",
                student,
                "entryId",
                id,
                "hash",
                hash,
                "configFingerprint",
                configFingerprint));
    return new Input(
        key,
        hash,
        entry.getImageRevisionId(),
        asset.getId(),
        assets.bytes(asset),
        asset.getMimeType(),
        vars);
  }

  private Object analogyQuestions(AgentRuntimeService.AgentRawResult result) {
    List<Object> questions = new ArrayList<>();
    for (var question : result.json().path("items"))
      questions.add(
          map(
              "questionText",
              question.path("stem").asText(),
              "options",
              question.path("options"),
              "answer",
              question.path("answer").asText(),
              "explanation",
              question.path("analysis").asText(),
              "difficulty",
              question.get("difficulty")));
    return questions;
  }

  @Transactional
  public Object result(
      User user,
      String student,
      String entryId,
      String type,
      Input input,
      AgentRuntimeService.AgentRawResult result) {
    List<AiAgentResult> rows =
        s.find(
            AiAgentResult.class,
            map(
                "agentKey",
                type,
                "subjectKey",
                "entry:" + input.key(),
                "traceId",
                result.traceId()));
    AiAgentResult row = rows.isEmpty() ? new AiAgentResult() : rows.get(0);
    if (row.getId() == null) {
      row.setAgentKey(type);
      row.setSubjectKey("entry:" + input.key());
      row.setPromptHash(s.hash(result.json()));
      row.setResultJson(result.json().toString());
      row.setTraceId(result.traceId());
      s.em.persist(row);
    }
    row.setUserId(user.getId());
    row.setStudentId(student);
    row.setEntryId(entryId);
    row.setInputRevisionId(input.revisionId());
    row.setInputContentHash(input.contentHash());
    row.setResultType(type);
    row.setProvider("DASHSCOPE");
    row.setModel(result.model());
    row.setConfigVersion(s.get(AiAgentConfig.class, type).getConfigVersion());
    row.setStatus("SUCCEEDED");
    for (AiCallLog log :
        s.find(AiCallLog.class, map("traceId", result.traceId(), "userId", user.getId()))) {
      log.setStudentId(student);
      log.setProvider("DASHSCOPE");
      log.setEntryId(entryId);
      log.setInputAssetId(input.assetId());
      log.setModel(result.model());
      log.setStatus(log.isSuccess() ? "SUCCEEDED" : "FAILED");
    }
    s.em.flush();
    Object content =
        type.equals("EXPLAIN")
            ? map(
                "schemaVersion",
                1,
                "text",
                result.json().path("analysis").asText(),
                "steps",
                result.json().path("steps"),
                "knowledgePoints",
                result.json().path("knowledgePoints"),
                "summary",
                result.json().path("summary"))
            : map("schemaVersion", 1, "questions", analogyQuestions(result));
    return map(
        "id",
        row.getId().toString(),
        "entryId",
        entryId,
        "resultType",
        type,
        "status",
        row.getStatus(),
        "cached",
        result.cached(),
        "provider",
        "DASHSCOPE",
        "model",
        result.model(),
        "inputContentHash",
        input.contentHash(),
        "result",
        content,
        "traceId",
        result.traceId(),
        "createdAt",
        row.getCreatedAt());
  }
}
