package com.yingying.cuotiku.server.service;

import com.yingying.cuotiku.server.dto.AdminUserDto.AgentConfigDto;
import com.yingying.cuotiku.server.dto.AdminUserDto.UpdateAgentRequest;
import com.yingying.cuotiku.server.entity.AiAgentConfig;
import com.yingying.cuotiku.server.repository.AiAgentConfigRepository;
import com.yingying.cuotiku.server.repository.AiAgentResultRepository;
import com.yingying.cuotiku.server.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class AgentConfigService {

    private static final Logger log = LoggerFactory.getLogger(AgentConfigService.class);
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    public static final String ANALOGY = "ANALOGY";
    public static final String EXPLAIN = "EXPLAIN";

    public static final Map<String, List<String>> VARIABLES = Map.of(
            ANALOGY, List.of("subject", "grade", "term", "errorType", "count"),
            EXPLAIN, List.of("subject", "grade", "term", "errorType"));

    private static final Map<String, String[]> DEFAULTS = Map.of(
            ANALOGY, new String[]{"举一反三", "qwen-vl-max",
                    """
                    你是一位资深的小学初中学科教研员，擅长根据错题图片诊断错因，并命制同考点、同题型的变式题。\
                    要求：1. 变式题必须与原题考查同一知识点，但情境与数字不同；2. 难度梯度递进；3. 语言符合学生年级；\
                    4. 只输出合法 JSON，不要输出任何解释性文字或代码块标记。""",
                    """
                    科目：{subject}；年级：{grade}年级；学期：{term}；学生错因：{errorType}。
                    附件图片是一道错题。请基于该错题生成 {count} 道举一反三变式题。
                    只输出如下 JSON：
                    {"items":[{"stem":"题干","options":["选项A","选项B"],"answer":"答案","analysis":"解析","difficulty":1}]}
                    说明：options 仅选择题需要，填空题可为空数组；difficulty 取 1-3。"""},
            EXPLAIN, new String[]{"做题精讲", "qwen-vl-max",
                    """
                    你是一位耐心细致的名师，负责为学生精讲错题：先讲思路，再分步讲解，最后归纳知识点与易错提醒。\
                    要求：1. 讲解贴合学生年级认知；2. 步骤清晰、每步一句话要点；3. 只输出合法 JSON，不要输出任何解释性文字或代码块标记。""",
                    """
                    科目：{subject}；年级：{grade}年级；学期：{term}；学生错因：{errorType}。
                    附件图片是一道错题。请精讲这道题。
                    只输出如下 JSON：
                    {"analysis":"整体思路","steps":[{"title":"步骤标题","content":"步骤讲解"}],"knowledgePoints":["知识点"],"commonMistakes":["易错点"],"summary":"一句话总结"}"""});

    private final AiAgentConfigRepository repository;
    private final AiAgentResultRepository resultRepository;

    public AgentConfigService(AiAgentConfigRepository repository, AiAgentResultRepository resultRepository) {
        this.repository = repository;
        this.resultRepository = resultRepository;
    }

    @Transactional
    public long purgeResults(String agentKey, String subjectKey) {
        if (agentKey != null && !agentKey.isBlank() && subjectKey != null && !subjectKey.isBlank()) {
            return resultRepository.deleteByAgentKeyAndSubjectKey(agentKey.toUpperCase(), subjectKey.trim());
        }
        if (agentKey != null && !agentKey.isBlank()) {
            return resultRepository.deleteByAgentKey(agentKey.toUpperCase());
        }
        long total = resultRepository.count();
        resultRepository.deleteAll();
        return total;
    }

    @Bean
    public ApplicationRunner seedAgents() {
        return args -> {
            for (Map.Entry<String, String[]> entry : DEFAULTS.entrySet()) {
                if (repository.existsById(entry.getKey())) {
                    continue;
                }
                String[] d = entry.getValue();
                AiAgentConfig config = new AiAgentConfig();
                config.setAgentKey(entry.getKey());
                config.setName(d[0]);
                config.setModel(d[1]);
                config.setSystemPrompt(d[2]);
                config.setUserPromptTemplate(d[3]);
                repository.save(config);
                log.info("已初始化 Agent 配置：{} ({})", d[0], entry.getKey());
            }
        };
    }

    @Transactional(readOnly = true)
    public List<AgentConfigDto> list() {
        return repository.findAll().stream().map(AgentConfigService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public AiAgentConfig require(String key) {
        return repository.findById(key)
                .orElseThrow(() -> ApiException.notFound("Agent 不存在：" + key));
    }

    @Transactional
    public AgentConfigDto update(String key, UpdateAgentRequest request) {
        AiAgentConfig config = require(key);
        config.setSystemPrompt(request.systemPrompt());
        config.setUserPromptTemplate(request.userPromptTemplate());
        config.setModel(request.model().trim());
        if (request.temperature() != null) {
            config.setTemperature(request.temperature());
        }
        if (request.maxTokens() != null) {
            config.setMaxTokens(request.maxTokens());
        }
        if (request.enabled() != null) {
            config.setEnabled(request.enabled());
        }
        repository.save(config);
        log.info("Agent 配置已更新：{} model={} enabled={}", key, config.getModel(), config.isEnabled());
        return toDto(config);
    }

    @Transactional
    public AgentConfigDto reset(String key) {
        AiAgentConfig config = require(key);
        String[] d = DEFAULTS.get(key);
        if (d == null) {
            throw ApiException.badRequest("该 Agent 无内置默认配置");
        }
        config.setName(d[0]);
        config.setModel(d[1]);
        config.setSystemPrompt(d[2]);
        config.setUserPromptTemplate(d[3]);
        repository.save(config);
        log.info("Agent 配置已恢复默认：{}", key);
        return toDto(config);
    }

    public static AgentConfigDto toDto(AiAgentConfig config) {
        return new AgentConfigDto(
                config.getAgentKey(),
                config.getName(),
                config.getSystemPrompt(),
                config.getUserPromptTemplate(),
                config.getModel(),
                config.getTemperature(),
                config.getMaxTokens(),
                config.isEnabled(),
                config.getUpdatedAt() == null ? null : TIME_FORMAT.format(config.getUpdatedAt()),
                VARIABLES.getOrDefault(config.getAgentKey(), List.of()));
    }
}
