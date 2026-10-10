package com.yingying.cuotiku.server.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** 采集批次。关联按标量 ID 写入，Ref 属性仅提供只读延迟导航。 */
@Entity
@Table(name = "capture_batch", indexes = {
        @Index(name = "idx_capture_batch_owner", columnList = "user_id,student_id,created_at"),
        @Index(name = "idx_capture_batch_status", columnList = "status,created_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_capture_batch_0", columnNames = {"user_id", "client_request_id"})
})
public class CaptureBatch {

    /** 批次主键 */
    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    /** 所属账号 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 所属学生 */
    @Column(name = "student_id", nullable = false, length = 36)
    private String studentId;

    /** SINGLE / MULTI */
    @Column(name = "mode", nullable = false, length = 16)
    private String mode;

    /** DRAFT / UPLOADING / READY / CLOSED / ABANDONED */
    @Column(name = "status", nullable = false, length = 16)
    private String status = "DRAFT";

    /** 批次创建幂等键 */
    @Column(name = "client_request_id", nullable = false, length = 64)
    private String clientRequestId;

    /** 用户结束拍摄时间 */
    @Column(name = "finished_at")
    private Instant finishedAt;

    /** 无引用工作区到期时间 */
    @Column(name = "expires_at")
    private Instant expiresAt;

    /** 创建时间 */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** 更新时间 */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** 逻辑删除时间 */
    @Column(name = "deleted_at")
    private Instant deletedAt;

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

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getClientRequestId() { return clientRequestId; }
    public void setClientRequestId(String clientRequestId) { this.clientRequestId = clientRequestId; }

    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private User userRef;

    public User getUserRef() { return userRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", insertable = false, updatable = false)
    private StudentProfile studentRef;

    public StudentProfile getStudentRef() { return studentRef; }
}
