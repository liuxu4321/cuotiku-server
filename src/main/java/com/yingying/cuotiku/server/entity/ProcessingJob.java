package com.yingying.cuotiku.server.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 图片处理任务。关联按标量 ID 写入，Ref 属性仅提供只读延迟导航。 */
@Entity
@Table(name = "processing_job", indexes = {
        @Index(name = "idx_processing_job_owner", columnList = "user_id,student_id,created_at"),
        @Index(name = "idx_processing_job_batch_id", columnList = "batch_id"),
        @Index(name = "idx_processing_job_status", columnList = "status,created_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_processing_job_0", columnNames = {"user_id", "request_key"})
})
public class ProcessingJob {

    /** 任务主键 */
    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    /** 提交账号 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 目标学生 */
    @Column(name = "student_id", nullable = false, length = 36)
    private String studentId;

    /** 所属采集批次 */
    @Column(name = "batch_id", nullable = false, length = 36)
    private String batchId;

    /** CORRECT / ENHANCE / ERASE / SPLIT / PAPER_PROCESS */
    @Column(name = "operation", nullable = false, length = 32)
    private String operation;

    /** CURRENT / ALL */
    @Column(name = "apply_scope", nullable = false, length = 16)
    private String applyScope;

    /** 工具参数与组合流水线策略 */
    @Column(name = "parameters_json", nullable = false, columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    private String parametersJson;

    /** QUEUED / RUNNING / SUCCEEDED / PARTIAL_SUCCESS / FAILED / CANCELLED */
    @Column(name = "status", nullable = false, length = 24)
    private String status = "QUEUED";

    /** 幂等键 */
    @Column(name = "request_key", nullable = false, length = 64)
    private String requestKey;

    /** 请求内容摘要，防同键不同内容 */
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    /** 处理链追踪标识 */
    @Column(name = "trace_id", nullable = false, length = 64)
    private String traceId;

    /** 提交时间 */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** 最近状态更新时间 */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** 终态时间 */
    @Column(name = "finished_at")
    private Instant finishedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }

    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }

    public String getApplyScope() { return applyScope; }
    public void setApplyScope(String applyScope) { this.applyScope = applyScope; }

    public String getParametersJson() { return parametersJson; }
    public void setParametersJson(String parametersJson) { this.parametersJson = parametersJson; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String requestKey) { this.requestKey = requestKey; }

    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String requestHash) { this.requestHash = requestHash; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private User userRef;

    public User getUserRef() { return userRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", insertable = false, updatable = false)
    private StudentProfile studentRef;

    public StudentProfile getStudentRef() { return studentRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", insertable = false, updatable = false)
    private CaptureBatch batchRef;

    public CaptureBatch getBatchRef() { return batchRef; }
}
