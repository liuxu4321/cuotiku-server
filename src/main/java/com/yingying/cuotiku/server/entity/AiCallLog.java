package com.yingying.cuotiku.server.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ai_call_log", indexes = {
        @Index(name = "idx_ai_log_user_created", columnList = "userId,createdAt"),
        @Index(name = "idx_ai_log_phone", columnList = "phone"),
        @Index(name = "idx_ai_log_created", columnList = "createdAt")
})
public class AiCallLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, length = 32)
    private String aiType;

    @Column(nullable = false)
    private boolean success;

    private Integer errorCode;

    @Column(length = 512)
    private String errorMessage;

    @Column(length = 32)
    private String traceId;

    @Column(length = 64)
    private String requestId;

    @Column(nullable = false)
    private long inputBytes;

    @Column(nullable = false)
    private long outputBytes;

    @Column(nullable = false)
    private long durationMs;

    private Integer inputTokens;

    private Integer outputTokens;

    @Column(nullable = false)
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
}
