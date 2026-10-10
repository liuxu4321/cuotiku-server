package com.yingying.cuotiku.server.service;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yingying.cuotiku.server.config.AppProperties;
import com.yingying.cuotiku.server.entity.AiAgentConfig;
import com.yingying.cuotiku.server.entity.AiAgentResult;
import com.yingying.cuotiku.server.entity.AiCallLog;
import com.yingying.cuotiku.server.entity.User;
import com.yingying.cuotiku.server.repository.AiAgentResultRepository;
import com.yingying.cuotiku.server.repository.AiCallLogRepository;
import com.yingying.cuotiku.server.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AgentRuntimeService {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntimeService.class);
    private static final String JSON_NUDGE = "\n\n上一次输出不是合法 JSON。请只重新输出合法 JSON，不要任何多余文字。";

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final AgentConfigService configService;
    private final AiCallLogRepository callLogRepository;
    private final AiAgentResultRepository resultRepository;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public AgentRuntimeService(ObjectProvider<ChatModel> chatModelProvider,
                               AgentConfigService configService,
                               AiCallLogRepository callLogRepository,
                               AiAgentResultRepository resultRepository,
                               AppProperties properties,
                               ObjectMapper objectMapper,
                               org.springframework.core.env.Environment environment) {
        this.chatModelProvider = chatModelProvider;
        this.configService = configService;
        this.callLogRepository = callLogRepository;
        this.resultRepository = resultRepository;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.apiKey = environment.getProperty("spring.ai.dashscope.api-key", "");
    }

    public record AgentRawResult(JsonNode json, String model, String traceId,
                                 Integer inputTokens, Integer outputTokens, boolean cached) {}

    public AgentRawResult run(String agentKey, Map<String, String> vars,
                              byte[] imageBytes, MimeType imageMime, User user, String entryId) {
        return run(agentKey, vars, imageBytes, imageMime, user, entryId, false);
    }

    /** 小程序显式刷新可跳过缓存；默认调用保持旧客户端行为。 */
    public AgentRawResult run(String agentKey, Map<String, String> vars,
                              byte[] imageBytes, MimeType imageMime, User user, String entryId,
                              boolean forceRefresh) {
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        AiAgentConfig config = configService.require(agentKey);
        if (!config.isEnabled()) {
            throw ApiException.badRequest("「" + config.getName() + "」已停用，请联系管理员");
        }
        String userPromptPreview = render(config.getUserPromptTemplate(), vars);
        String subjectKey = subjectKey(entryId, imageBytes);
        String promptHash = promptHash(config, userPromptPreview);
        Optional<AiAgentResult> cached =
                resultRepository.findByAgentKeyAndSubjectKeyAndPromptHash(agentKey, subjectKey, promptHash);
        if (!forceRefresh && cached.isPresent()) {
            try {
                JsonNode cachedJson = objectMapper.readTree(cached.get().getResultJson());
                log.info("Agent命中缓存 traceId={} agent={} subjectKey={} 原traceId={}",
                        traceId, agentKey, subjectKey, cached.get().getTraceId());
                return new AgentRawResult(cachedJson, config.getModel(), cached.get().getTraceId(),
                        null, null, true);
            } catch (Exception e) {
                log.warn("Agent缓存解析失败，回退重新调用 subjectKey={}: {}", subjectKey, e.getMessage());
            }
        }
        long limit = properties.agent() == null ? 20 : properties.agent().dailyLimit();
        if (limit > 0) {
            Instant todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
            long used = callLogRepository.countByUserIdAndAiTypeAndSuccessTrueAndCreatedAtAfter(
                    user.getId(), agentKey, todayStart);
            if (used >= limit) {
                throw new ApiException(429, "「" + config.getName() + "」今日使用次数已用完（每日 "
                        + limit + " 次），明天再来吧");
            }
        }
        if (apiKey == null || apiKey.isBlank() || "not-configured".equals(apiKey)) {
            saveLog(user, agentKey, false, 503, "后台尚未配置大模型密钥", traceId, null, null, 0);
            throw new ApiException(503, "后台尚未配置大模型密钥（DASHSCOPE_API_KEY），请联系管理员");
        }
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            saveLog(user, agentKey, false, 503, "大模型组件未就绪", traceId, null, null, 0);
            throw new ApiException(503, "大模型组件未就绪，请联系管理员");
        }

        String userPrompt = userPromptPreview;
        long inputBytes = imageBytes == null ? 0 : imageBytes.length;
        long startMillis = System.currentTimeMillis();
        try {
            AgentCall call = callWithRetry(chatModel, config, userPrompt, imageBytes, imageMime);
            JsonNode json = call.json();
            ChatResponse last = call.response();
            Integer inTokens = last == null || last.getMetadata() == null || last.getMetadata().getUsage() == null
                    ? null : (int) last.getMetadata().getUsage().getPromptTokens();
            Integer outTokens = last == null || last.getMetadata() == null || last.getMetadata().getUsage() == null
                    ? null : (int) last.getMetadata().getUsage().getCompletionTokens();
            log.info("Agent调用成功 traceId={} agent={} phone={} model={} tokens={}/{}",
                    traceId, agentKey, user.getPhone(), config.getModel(), inTokens, outTokens);
            saveLog(user, agentKey, true, null, null, traceId, inTokens, outTokens, inputBytes);
            saveResult(agentKey, subjectKey, promptHash, json, traceId);
            return new AgentRawResult(json, config.getModel(), traceId, inTokens, outTokens, false);
        } catch (ApiException e) {
            log.warn("Agent调用失败 traceId={} agent={} phone={} code={} message={}",
                    traceId, agentKey, user.getPhone(), e.getCode(), e.getMessage());
            saveLog(user, agentKey, false, e.getCode(), e.getMessage(), traceId, null, null, inputBytes);
            throw e;
        } catch (Exception e) {
            log.error("Agent调用异常 traceId={} agent={} phone={}", traceId, agentKey, user.getPhone(), e);
            saveLog(user, agentKey, false, 502, e.getMessage(), traceId, null, null, inputBytes);
            throw new ApiException(502, "大模型调用失败：" + e.getMessage());
        } finally {
            if (log.isDebugEnabled()) {
                log.debug("[Agent] traceId={} agent={} 耗时={}ms", traceId, agentKey,
                        System.currentTimeMillis() - startMillis);
            }
        }
    }

    private record AgentCall(JsonNode json, ChatResponse response) {}

    private AgentCall callWithRetry(ChatModel chatModel, AiAgentConfig config, String userPrompt,
                                    byte[] imageBytes, MimeType imageMime) {
        ChatResponse first = callOnce(chatModel, config, userPrompt, imageBytes, imageMime);
        JsonNode json = tryParse(textOf(first));
        if (json != null) {
            return new AgentCall(json, first);
        }
        log.warn("Agent 首次输出非合法 JSON，追加约束重试一次 agent={}", config.getAgentKey());
        ChatResponse retry = callOnce(chatModel, config, userPrompt + JSON_NUDGE, imageBytes, imageMime);
        JsonNode retryJson = tryParse(textOf(retry));
        if (retryJson == null) {
            throw new ApiException(502, "大模型输出解析失败，请重试");
        }
        return new AgentCall(retryJson, retry);
    }

    private String textOf(ChatResponse response) {
        return response.getResult() == null || response.getResult().getOutput() == null
                ? null : response.getResult().getOutput().getText();
    }

    private ChatResponse callOnce(ChatModel chatModel, AiAgentConfig config, String userPrompt,
                            byte[] imageBytes, MimeType imageMime) {
        UserMessage userMessage;
        if (imageBytes != null && imageBytes.length > 0) {
            userMessage = UserMessage.builder()
                    .text(userPrompt)
                    .media(Media.builder().mimeType(imageMime).data(imageBytes).build())
                    .build();
        } else {
            userMessage = new UserMessage(userPrompt);
        }
        boolean multimodal = imageBytes != null && imageBytes.length > 0;
        var options = DashScopeChatOptions.builder()
                .withModel(config.getModel())
                .withTemperature(config.getTemperature())
                .withMaxToken(config.getMaxTokens())
                // 带图请求必须走多模态端点，否则 DashScope 返回 url error
                .withMultiModel(multimodal)
                .build();
        ChatResponse response = chatModel.call(new Prompt(
                java.util.List.of(new SystemMessage(config.getSystemPrompt()), userMessage), options));
        if (textOf(response) == null) {
            throw new ApiException(502, "大模型未返回内容，请重试");
        }
        return response;
    }

    private JsonNode tryParse(String text) {
        if (text == null) {
            return null;
        }
        String cleaned = text.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("^```[a-zA-Z]*\\s*", "").replaceAll("```\\s*$", "").trim();
        }
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return objectMapper.readTree(cleaned.substring(start, end + 1));
        } catch (Exception e) {
            return null;
        }
    }

    private String render(String template, Map<String, String> vars) {
        String rendered = template;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            rendered = rendered.replace("{" + entry.getKey() + "}",
                    entry.getValue() == null ? "" : entry.getValue());
        }
        return rendered;
    }

    private void saveResult(String agentKey, String subjectKey, String promptHash, JsonNode json, String traceId) {
        try {
            AiAgentResult entity = resultRepository.findByAgentKeyAndSubjectKeyAndPromptHash(agentKey, subjectKey, promptHash).orElseGet(AiAgentResult::new);
            entity.setAgentKey(agentKey);
            entity.setSubjectKey(subjectKey);
            entity.setPromptHash(promptHash);
            var mini = com.yingying.cuotiku.server.mini.MiniAgentContext.current();
            if (mini != null) {
                entity.setUserId(mini.userId()); entity.setStudentId(mini.studentId());
                entity.setEntryId(mini.entryId()); entity.setInputRevisionId(mini.revisionId());
                entity.setInputContentHash(mini.inputHash()); entity.setResultType(agentKey);
                entity.setProvider("DASHSCOPE"); entity.setStatus("SUCCEEDED");
                var config = configService.require(agentKey);
                entity.setModel(config.getModel()); entity.setConfigVersion(config.getConfigVersion());
            }
            entity.setResultJson(json.toString());
            entity.setTraceId(traceId);
            resultRepository.save(entity);
        } catch (RuntimeException e) {
            log.warn("[Agent] 结果缓存写入失败 subjectKey={}: {}", subjectKey, e.getMessage());
        }
    }

    private String subjectKey(String entryId, byte[] imageBytes) {
        if (entryId != null && !entryId.isBlank()) {
            return "entry:" + entryId.trim();
        }
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(imageBytes == null ? new byte[0] : imageBytes);
            return "img:" + java.util.HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return "img:" + UUID.randomUUID();
        }
    }

    private String promptHash(AiAgentConfig config, String renderedUserPrompt) {
        String source = config.getSystemPrompt() + "\n" + renderedUserPrompt + "\n"
                + config.getModel() + "\n" + config.getTemperature();
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(source.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return String.valueOf(source.hashCode());
        }
    }

    private void saveLog(User user, String agentKey, boolean success, Integer errorCode, String errorMessage,
                         String traceId, Integer inputTokens, Integer outputTokens, long inputBytes) {
        try {
            AiCallLog entity = new AiCallLog();
            entity.setUserId(user.getId());
            entity.setPhone(user.getPhone());
            entity.setAiType(agentKey);
            entity.setSuccess(success);
            entity.setErrorCode(errorCode);
            entity.setErrorMessage(errorMessage == null ? null
                    : errorMessage.substring(0, Math.min(errorMessage.length(), 500)));
            entity.setTraceId(traceId);
            entity.setInputTokens(inputTokens);
            entity.setOutputTokens(outputTokens);
            var mini = com.yingying.cuotiku.server.mini.MiniAgentContext.current();
            if (mini != null) {
                entity.setStudentId(mini.studentId()); entity.setEntryId(mini.entryId());
                entity.setInputAssetId(mini.assetId()); entity.setProvider("DASHSCOPE");
                entity.setApiAction(agentKey); entity.setStatus(success ? "SUCCEEDED" : "FAILED");
                var config = configService.require(agentKey);
                entity.setModel(config.getModel());
                entity.setConfigSnapshotJson("{\"configVersion\":" + config.getConfigVersion() + "}");
            }
            entity.setInputBytes(inputBytes);
            entity.setOutputBytes(0);
            entity.setDurationMs(0);
            callLogRepository.save(entity);
        } catch (RuntimeException e) {
            log.warn("[Agent] 调用流水写入失败 traceId={}: {}", traceId, e.getMessage());
        }
    }
}
