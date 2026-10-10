package com.yingying.cuotiku.server.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 图片处理版本。关联按标量 ID 写入，Ref 属性仅提供只读延迟导航。 */
@Entity
@org.hibernate.annotations.Immutable
@Table(name = "image_revision", indexes = {
        @Index(name = "idx_image_revision_owner", columnList = "user_id,student_id,created_at"),
        @Index(name = "idx_image_revision_photo_id", columnList = "photo_id")
})
public class ImageRevision {

    /** 图片版本主键 */
    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    /** 所属照片 */
    @Column(name = "photo_id", nullable = false, length = 36)
    private String photoId;

    /** 所属账号 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 所属学生 */
    @Column(name = "student_id", nullable = false, length = 36)
    private String studentId;

    /** 输入版本；原始版本为空 */
    @Column(name = "parent_revision_id", length = 36)
    private String parentRevisionId;

    /** 该版本的图片资产 */
    @Column(name = "asset_id", nullable = false, length = 36)
    private String assetId;

    /** ORIGINAL / CORRECT / ENHANCE / ERASE */
    @Column(name = "operation", nullable = false, length = 32)
    private String operation;

    /** 工具参数与处理策略快照 */
    @Column(name = "parameters_json", nullable = false, columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    private String parametersJson = "{}";

    /** 产生版本的处理子任务 */
    @Column(name = "job_item_id", length = 36)
    private String jobItemId;

    /** 透视／旋转映射，用于坐标版本转换 */
    @Column(name = "geometry_transform_json", columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    private String geometryTransformJson;

    /** 生成时间 */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPhotoId() { return photoId; }
    public void setPhotoId(String photoId) { this.photoId = photoId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getParentRevisionId() { return parentRevisionId; }
    public void setParentRevisionId(String parentRevisionId) { this.parentRevisionId = parentRevisionId; }

    public String getAssetId() { return assetId; }
    public void setAssetId(String assetId) { this.assetId = assetId; }

    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }

    public String getParametersJson() { return parametersJson; }
    public void setParametersJson(String parametersJson) { this.parametersJson = parametersJson; }

    public String getJobItemId() { return jobItemId; }
    public void setJobItemId(String jobItemId) { this.jobItemId = jobItemId; }

    public String getGeometryTransformJson() { return geometryTransformJson; }
    public void setGeometryTransformJson(String geometryTransformJson) { this.geometryTransformJson = geometryTransformJson; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "photo_id", insertable = false, updatable = false)
    private CapturePhoto photoRef;

    public CapturePhoto getPhotoRef() { return photoRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private User userRef;

    public User getUserRef() { return userRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", insertable = false, updatable = false)
    private StudentProfile studentRef;

    public StudentProfile getStudentRef() { return studentRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_revision_id", insertable = false, updatable = false)
    private ImageRevision parentRevisionRef;

    public ImageRevision getParentRevisionRef() { return parentRevisionRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", insertable = false, updatable = false)
    private MediaAsset assetRef;

    public MediaAsset getAssetRef() { return assetRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_item_id", insertable = false, updatable = false)
    private ProcessingJobItem jobItemRef;

    public ProcessingJobItem getJobItemRef() { return jobItemRef; }
}
