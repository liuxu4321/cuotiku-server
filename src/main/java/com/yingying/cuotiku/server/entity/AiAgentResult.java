package com.yingying.cuotiku.server.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ai_agent_result", indexes = {
        @Index(name = "idx_agent_result_key", columnList = "agent_key,subject_key")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_agent_result_cache", columnNames = {"cache_key"})
})
public class AiAgentResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "agent_key", nullable = false, length = 32)
    private String agentKey;

    @Column(name = "subject_key", nullable = false, length = 80)
    private String subjectKey;

    @Column(name = "prompt_hash", nullable = false, length = 64)
    private String promptHash;

    @Column(name = "result_json", nullable = false, columnDefinition = "TEXT")
    private String resultJson;

    @Column(name = "trace_id", length = 32)
    private String traceId;

    @Column(name = "created_at", nullable = false)
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

    // Target model extensions remain nullable for pre-student historical rows.
    /** 所属账号 */
    @Column(name = "user_id")
    private Long userId;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    /** 所属学生 */
    @Column(name = "student_id", length = 36)
    private String studentId;

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    /** 所属错题 */
    @Column(name = "entry_id", length = 36)
    private String entryId;

    public String getEntryId() { return entryId; }
    public void setEntryId(String entryId) { this.entryId = entryId; }

    /** 输入题图版本 */
    @Column(name = "input_revision_id", length = 36)
    private String inputRevisionId;

    public String getInputRevisionId() { return inputRevisionId; }
    public void setInputRevisionId(String inputRevisionId) { this.inputRevisionId = inputRevisionId; }

    /** 图像、题干、参考答案等输入摘要 */
    @Column(name = "input_content_hash", length = 64)
    private String inputContentHash;

    public String getInputContentHash() { return inputContentHash; }
    public void setInputContentHash(String inputContentHash) { this.inputContentHash = inputContentHash; }

    /** EXPLAIN / ANALOGY */
    @Column(name = "result_type", length = 16)
    private String resultType;

    public String getResultType() { return resultType; }
    public void setResultType(String resultType) { this.resultType = resultType; }

    /** DASHSCOPE 等实际供应商 */
    @Column(name = "provider", length = 32)
    private String provider;

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    /** 实际调用模型 */
    @Column(name = "model", length = 64)
    private String model;

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    /** 生成结果的配置版本 */
    @Column(name = "config_version")
    private Integer configVersion;

    public Integer getConfigVersion() { return configVersion; }
    public void setConfigVersion(Integer configVersion) { this.configVersion = configVersion; }

    /** 成功结果缓存状态；失败不当作有效命中 */
    @Column(name = "status", length = 16)
    private String status = "SUCCEEDED";

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private User userRef;

    public User getUserRef() { return userRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private StudentProfile studentRef;

    public StudentProfile getStudentRef() { return studentRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entry_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private BookEntry entryRef;

    public BookEntry getEntryRef() { return entryRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "input_revision_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private ImageRevision inputRevisionRef;

    public ImageRevision getInputRevisionRef() { return inputRevisionRef; }

    /** Includes student and input-content version; null legacy dimensions retain the old cache identity. */
    @Column(name = "cache_key", insertable = false, updatable = false,
            columnDefinition = "char(64) generated always as (sha2(concat(coalesce(cast(user_id as char), ''), char(0), coalesce(student_id, ''), char(0), agent_key, char(0), subject_key, char(0), prompt_hash, char(0), coalesce(input_content_hash, '')), 256)) stored")
    private String cacheKey;

    public String getCacheKey() { return cacheKey; }
}
