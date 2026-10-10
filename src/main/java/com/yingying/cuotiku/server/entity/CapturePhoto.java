package com.yingying.cuotiku.server.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** 采集照片。关联按标量 ID 写入，Ref 属性仅提供只读延迟导航。 */
@Entity
@Table(name = "capture_photo", indexes = {
        @Index(name = "idx_capture_photo_owner", columnList = "user_id,student_id,created_at"),
        @Index(name = "idx_capture_photo_batch_id", columnList = "batch_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_capture_photo_0", columnNames = {"batch_id", "sort_order"})
})
public class CapturePhoto {

    /** 照片主键 */
    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    /** 所属 capture_batch */
    @Column(name = "batch_id", nullable = false, length = 36)
    private String batchId;

    /** 继承批次账号 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 继承批次学生 */
    @Column(name = "student_id", nullable = false, length = 36)
    private String studentId;

    /** 不可覆盖的原始资产 */
    @Column(name = "original_asset_id", nullable = false, length = 36)
    private String originalAssetId;

    /** 当前 image_revision，同照片校验 */
    @Column(name = "current_revision_id", length = 36)
    private String currentRevisionId;

    /** 批次内顺序 */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /** CAMERA / ALBUM */
    @Column(name = "source", nullable = false, length = 16)
    private String source;

    /** 拍摄时间，不能信任设备时间用于审计 */
    @Column(name = "captured_at")
    private Instant capturedAt;

    /** 服务端上传确认时间 */
    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    /** 创建时间 */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** 更新时间 */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** 删除照片的时间 */
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

    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getOriginalAssetId() { return originalAssetId; }
    public void setOriginalAssetId(String originalAssetId) { this.originalAssetId = originalAssetId; }

    public String getCurrentRevisionId() { return currentRevisionId; }
    public void setCurrentRevisionId(String currentRevisionId) { this.currentRevisionId = currentRevisionId; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public Instant getCapturedAt() { return capturedAt; }
    public void setCapturedAt(Instant capturedAt) { this.capturedAt = capturedAt; }

    public Instant getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(Instant uploadedAt) { this.uploadedAt = uploadedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", insertable = false, updatable = false)
    private CaptureBatch batchRef;

    public CaptureBatch getBatchRef() { return batchRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private User userRef;

    public User getUserRef() { return userRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", insertable = false, updatable = false)
    private StudentProfile studentRef;

    public StudentProfile getStudentRef() { return studentRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_asset_id", insertable = false, updatable = false)
    private MediaAsset originalAssetRef;

    public MediaAsset getOriginalAssetRef() { return originalAssetRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_revision_id", insertable = false, updatable = false)
    private ImageRevision currentRevisionRef;

    public ImageRevision getCurrentRevisionRef() { return currentRevisionRef; }
}
