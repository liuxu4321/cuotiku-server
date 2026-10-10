package com.yingying.cuotiku.server.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ai_agent_config")
public class AiAgentConfig {

    @Id
    @Column(name = "agent_key", length = 32)
    private String agentKey;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Column(name = "system_prompt", nullable = false, columnDefinition = "TEXT")
    private String systemPrompt;

    @Column(name = "user_prompt_template", nullable = false, columnDefinition = "TEXT")
    private String userPromptTemplate;

    @Column(name = "model", nullable = false, length = 64)
    private String model;

    @Column(name = "temperature", nullable = false)
    private Double temperature = 0.7;

    @Column(name = "max_tokens", nullable = false)
    private Integer maxTokens = 4096;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void onTouch() {
        updatedAt = Instant.now();
    }

    public String getAgentKey() { return agentKey; }
    public void setAgentKey(String agentKey) { this.agentKey = agentKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    public String getUserPromptTemplate() { return userPromptTemplate; }
    public void setUserPromptTemplate(String userPromptTemplate) { this.userPromptTemplate = userPromptTemplate; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }
    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Instant getUpdatedAt() { return updatedAt; }

    // Target model extensions remain nullable for pre-student historical rows.
    /** 模型供应商，保留当前 DashScope 实现 */
    @Column(name = "provider", length = 32)
    private String provider = "DASHSCOPE";

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    /** 配置修订号，用于追溯和缓存失效 */
    @Column(name = "config_version")
    private Integer configVersion = 1;

    public Integer getConfigVersion() { return configVersion; }
    public void setConfigVersion(Integer configVersion) { this.configVersion = configVersion; }



}
