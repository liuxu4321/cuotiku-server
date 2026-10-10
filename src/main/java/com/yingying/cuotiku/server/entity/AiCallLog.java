package com.yingying.cuotiku.server.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ai_call_log", indexes = {
        @Index(name = "idx_ai_log_student", columnList = "user_id,student_id,created_at"),
        @Index(name = "idx_ai_log_job_item", columnList = "job_item_id"),

        @Index(name = "idx_ai_log_user_created", columnList = "user_id,created_at"),
        @Index(name = "idx_ai_log_phone", columnList = "phone"),
        @Index(name = "idx_ai_log_created", columnList = "created_at")
})
public class AiCallLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Column(name = "ai_type", nullable = false, length = 32)
    private String aiType;

    @Column(name = "success", nullable = false)
    private boolean success;    @Column(name = "error_code")
    private Integer errorCode;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    @Column(name = "trace_id", length = 32)
    private String traceId;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "input_bytes", nullable = false)
    private long inputBytes;

    @Column(name = "output_bytes", nullable = false)
    private long outputBytes;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;    @Column(name = "input_tokens")
    private Integer inputTokens;    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getAiType() { return aiType; }
    public void setAiType(String aiType) { this.aiType = aiType; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public Integer getErrorCode() { return errorCode; }
    public void setErrorCode(Integer errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public long getInputBytes() { return inputBytes; }
    public void setInputBytes(long inputBytes) { this.inputBytes = inputBytes; }
    public long getOutputBytes() { return outputBytes; }
    public void setOutputBytes(long outputBytes) { this.outputBytes = outputBytes; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public Integer getInputTokens() { return inputTokens; }
    public void setInputTokens(Integer inputTokens) { this.inputTokens = inputTokens; }
    public Integer getOutputTokens() { return outputTokens; }
    public void setOutputTokens(Integer outputTokens) { this.outputTokens = outputTokens; }
    public Instant getCreatedAt() { return createdAt; }

    // Target model extensions remain nullable for pre-student historical rows.
    /** 所属学生，旧日志可空 */
    @Column(name = "student_id", length = 36)
    private String studentId;

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    /** TENCENT_OCR / DASHSCOPE */
    @Column(name = "provider", length = 32)
    private String provider;

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    /** 实际腾讯云 Action 或模型调用动作 */
    @Column(name = "api_action", length = 64)
    private String apiAction;

    public String getApiAction() { return apiAction; }
    public void setApiAction(String apiAction) { this.apiAction = apiAction; }

    /** 腾讯云 API 版本等 */
    @Column(name = "api_version", length = 32)
    private String apiVersion;

    public String getApiVersion() { return apiVersion; }
    public void setApiVersion(String apiVersion) { this.apiVersion = apiVersion; }

    /** Agent 模型／上游公开模型标识 */
    @Column(name = "model", length = 64)
    private String model;

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    /** 关联图片处理子任务 */
    @Column(name = "job_item_id", length = 36)
    private String jobItemId;

    public String getJobItemId() { return jobItemId; }
    public void setJobItemId(String jobItemId) { this.jobItemId = jobItemId; }

    /** 关联错题 */
    @Column(name = "entry_id", length = 36)
    private String entryId;

    public String getEntryId() { return entryId; }
    public void setEntryId(String entryId) { this.entryId = entryId; }

    /** 实际输入资产 */
    @Column(name = "input_asset_id", length = 36)
    private String inputAssetId;

    public String getInputAssetId() { return inputAssetId; }
    public void setInputAssetId(String inputAssetId) { this.inputAssetId = inputAssetId; }

    /** 成功落 COS 的输出资产 */
    @Column(name = "output_asset_id", length = 36)
    private String outputAssetId;

    public String getOutputAssetId() { return outputAssetId; }
    public void setOutputAssetId(String outputAssetId) { this.outputAssetId = outputAssetId; }

    /** 本阶段第几次上游尝试 */
    @Column(name = "attempt_no")
    private Integer attemptNo = 1;

    public Integer getAttemptNo() { return attemptNo; }
    public void setAttemptNo(Integer attemptNo) { this.attemptNo = attemptNo; }

    /** 腾讯云字符串错误码，不挤入现有 Integer error_code */
    @Column(name = "upstream_error_code", length = 128)
    private String upstreamErrorCode;

    public String getUpstreamErrorCode() { return upstreamErrorCode; }
    public void setUpstreamErrorCode(String upstreamErrorCode) { this.upstreamErrorCode = upstreamErrorCode; }

    /** 脱敏调用参数、策略版本，不含图片 Base64／密钥 */
    @Column(name = "config_snapshot_json", columnDefinition = "json")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private String configSnapshotJson;

    public String getConfigSnapshotJson() { return configSnapshotJson; }
    public void setConfigSnapshotJson(String configSnapshotJson) { this.configSnapshotJson = configSnapshotJson; }

    /** 确需保留的大型原始响应资产 */
    @Column(name = "response_asset_id", length = 36)
    private String responseAssetId;

    public String getResponseAssetId() { return responseAssetId; }
    public void setResponseAssetId(String responseAssetId) { this.responseAssetId = responseAssetId; }

    /** SUCCEEDED / FAILED / TIMEOUT / UNKNOWN */
    @Column(name = "status", length = 24)
    private String status;

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
    @JoinColumn(name = "job_item_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private ProcessingJobItem jobItemRef;

    public ProcessingJobItem getJobItemRef() { return jobItemRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entry_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private BookEntry entryRef;

    public BookEntry getEntryRef() { return entryRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "input_asset_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private MediaAsset inputAssetRef;

    public MediaAsset getInputAssetRef() { return inputAssetRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "output_asset_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private MediaAsset outputAssetRef;

    public MediaAsset getOutputAssetRef() { return outputAssetRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "response_asset_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private MediaAsset responseAssetRef;

    public MediaAsset getResponseAssetRef() { return responseAssetRef; }

}
