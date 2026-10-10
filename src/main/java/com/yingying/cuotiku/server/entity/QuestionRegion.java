package com.yingying.cuotiku.server.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 题目框区域。关联按标量 ID 写入，Ref 属性仅提供只读延迟导航。 */
@Entity
@Table(name = "question_region", indexes = {
        @Index(name = "idx_question_region_owner", columnList = "user_id,student_id,created_at"),
        @Index(name = "idx_question_region_photo_id", columnList = "photo_id"),
        @Index(name = "idx_question_region_status", columnList = "status,created_at")
})
public class QuestionRegion {

    /** 题框主键 */
    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    /** 所属账号 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 所属学生 */
    @Column(name = "student_id", nullable = false, length = 36)
    private String studentId;

    /** 来源照片 */
    @Column(name = "photo_id", nullable = false, length = 36)
    private String photoId;

    /** 坐标绑定的确切版本 */
    @Column(name = "revision_id", nullable = false, length = 36)
    private String revisionId;

    /** 裁切图，生成后必填 */
    @Column(name = "crop_asset_id", length = 36)
    private String cropAssetId;

    /** 归一化多边形点或矩形 x/y/width/height，0～1 */
    @Column(name = "geometry_json", nullable = false, columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    private String geometryJson;

    /** 阅读／显示顺序 */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /** AUTO / MANUAL */
    @Column(name = "origin", nullable = false, length = 16)
    private String origin;

    /** ACTIVE / SUPERSEDED / DELETED */
    @Column(name = "status", nullable = false, length = 16)
    private String status = "ACTIVE";

    /** 手工提交幂等标识 */
    @Column(name = "client_region_id", length = 64)
    private String clientRegionId;

    /** 来源检测子任务 */
    @Column(name = "job_item_id", length = 36)
    private String jobItemId;

    /** 创建时间 */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** 更新时间 */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** 删除时间 */
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

    public String getPhotoId() { return photoId; }
    public void setPhotoId(String photoId) { this.photoId = photoId; }

    public String getRevisionId() { return revisionId; }
    public void setRevisionId(String revisionId) { this.revisionId = revisionId; }

    public String getCropAssetId() { return cropAssetId; }
    public void setCropAssetId(String cropAssetId) { this.cropAssetId = cropAssetId; }

    public String getGeometryJson() { return geometryJson; }
    public void setGeometryJson(String geometryJson) { this.geometryJson = geometryJson; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getClientRegionId() { return clientRegionId; }
    public void setClientRegionId(String clientRegionId) { this.clientRegionId = clientRegionId; }

    public String getJobItemId() { return jobItemId; }
    public void setJobItemId(String jobItemId) { this.jobItemId = jobItemId; }

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "photo_id", insertable = false, updatable = false)
    private CapturePhoto photoRef;

    public CapturePhoto getPhotoRef() { return photoRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revision_id", insertable = false, updatable = false)
    private ImageRevision revisionRef;

    public ImageRevision getRevisionRef() { return revisionRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "crop_asset_id", insertable = false, updatable = false)
    private MediaAsset cropAssetRef;

    public MediaAsset getCropAssetRef() { return cropAssetRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_item_id", insertable = false, updatable = false)
    private ProcessingJobItem jobItemRef;

    public ProcessingJobItem getJobItemRef() { return jobItemRef; }
}
