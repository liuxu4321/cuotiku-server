package com.yingying.cuotiku.server.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ai_agent_result", indexes = {
        @Index(name = "idx_agent_result_key", columnList = "agentKey,subjectKey")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_agent_result", columnNames = {"agentKey", "subjectKey", "promptHash"})
})
public class AiAgentResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String agentKey;

    @Column(nullable = false, length = 80)
    private String subjectKey;

    @Column(nullable = false, length = 64)
    private String promptHash;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String resultJson;

    @Column(length = 32)
    private String traceId;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getAgentKey() { return agentKey; }
    public void setAgentKey(String agentKey) { this.agentKey = agentKey; }
    public String getSubjectKey() { return subjectKey; }
    public void setSubjectKey(String subjectKey) { this.subjectKey = subjectKey; }
    public String getPromptHash() { return promptHash; }
    public void setPromptHash(String promptHash) { this.promptHash = promptHash; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public Instant getCreatedAt() { return createdAt; }
}
