package com.yingying.cuotiku.server.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 单图处理子任务。关联按标量 ID 写入，Ref 属性仅提供只读延迟导航。 */
@Entity
@Table(name = "processing_job_item", indexes = {
        @Index(name = "idx_processing_job_item_photo_id", columnList = "photo_id"),
        @Index(name = "idx_processing_job_item_job_id", columnList = "job_id"),
        @Index(name = "idx_processing_job_item_status", columnList = "status,created_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_processing_job_item_0", columnNames = {"job_id", "photo_id"})
})
public class ProcessingJobItem {

    /** 子任务主键 */
    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    /** 所属 processing_job；归属从父任务继承 */
    @Column(name = "job_id", nullable = false, length = 36)
    private String jobId;

    /** 目标照片 */
    @Column(name = "photo_id", nullable = false, length = 36)
    private String photoId;

    /** 固定输入版本 */
    @Column(name = "input_revision_id", nullable = false, length = 36)
    private String inputRevisionId;

    /** 成功输出版本；纯框题可以为空 */
    @Column(name = "output_revision_id", length = 36)
    private String outputRevisionId;

    /** QUEUED / RUNNING / SUCCEEDED / FAILED / CANCELLED */
    @Column(name = "status", nullable = false, length = 24)
    private String status = "QUEUED";

    /** 执行次数 */
    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    /** 各阶段结果、降级标记及上游 RequestId */
    @Column(name = "step_results_json", columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    private String stepResultsJson;

    /** 处理业务错误码 */
    @Column(name = "error_code", length = 64)
    private String errorCode;

    /** 脱敏失败信息 */
    @Column(name = "error_message", length = 512)
    private String errorMessage;

    /** 首次执行时间 */
    @Column(name = "started_at")
    private Instant startedAt;

    /** 终态时间 */
    @Column(name = "finished_at")
    private Instant finishedAt;

    /** 创建时间 */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** 更新时间 */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public String getPhotoId() { return photoId; }
    public void setPhotoId(String photoId) { this.photoId = photoId; }

    public String getInputRevisionId() { return inputRevisionId; }
    public void setInputRevisionId(String inputRevisionId) { this.inputRevisionId = inputRevisionId; }

    public String getOutputRevisionId() { return outputRevisionId; }
    public void setOutputRevisionId(String outputRevisionId) { this.outputRevisionId = outputRevisionId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getAttemptCount() { return attemptCount; }
    public void setAttemptCount(Integer attemptCount) { this.attemptCount = attemptCount; }

    public String getStepResultsJson() { return stepResultsJson; }
    public void setStepResultsJson(String stepResultsJson) { this.stepResultsJson = stepResultsJson; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", insertable = false, updatable = false)
    private ProcessingJob jobRef;

    public ProcessingJob getJobRef() { return jobRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "photo_id", insertable = false, updatable = false)
    private CapturePhoto photoRef;

    public CapturePhoto getPhotoRef() { return photoRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "input_revision_id", insertable = false, updatable = false)
    private ImageRevision inputRevisionRef;

    public ImageRevision getInputRevisionRef() { return inputRevisionRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "output_revision_id", insertable = false, updatable = false)
    private ImageRevision outputRevisionRef;

    public ImageRevision getOutputRevisionRef() { return outputRevisionRef; }
}
