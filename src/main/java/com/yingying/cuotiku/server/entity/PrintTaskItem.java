package com.yingying.cuotiku.server.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 打印任务题目快照。关联按标量 ID 写入，Ref 属性仅提供只读延迟导航。 */
@Entity
@org.hibernate.annotations.Immutable
@Table(name = "print_task_item", indexes = {
        @Index(name = "idx_print_task_item_task_id", columnList = "task_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_print_task_item_0", columnNames = {"task_id", "sort_order"})
})
@org.hibernate.annotations.Check(constraints = "((source_entry_id is not null) + (source_region_id is not null) + (source_photo_id is not null)) = 1")
public class PrintTaskItem {

    /** 打印项主键 */
    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    /** 所属 print_task，继承账号学生作用域 */
    @Column(name = "task_id", nullable = false, length = 36)
    private String taskId;

    /** 打印顺序 */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /** 源错题 */
    @Column(name = "source_entry_id", length = 36)
    private String sourceEntryId;

    /** 直接打印题框 */
    @Column(name = "source_region_id", length = 36)
    private String sourceRegionId;

    /** 直接打印整张照片 */
    @Column(name = "source_photo_id", length = 36)
    private String sourcePhotoId;

    /** 冻结题图资产 */
    @Column(name = "image_asset_id", nullable = false, length = 36)
    private String imageAssetId;

    /** 当时科目／主题／错误类型 ID 和名称、答案、尺寸及年级学期 */
    @Column(name = "content_snapshot_json", nullable = false, columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    private String contentSnapshotJson;

    /** 快照创建时间 */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public String getSourceEntryId() { return sourceEntryId; }
    public void setSourceEntryId(String sourceEntryId) { this.sourceEntryId = sourceEntryId; }

    public String getSourceRegionId() { return sourceRegionId; }
    public void setSourceRegionId(String sourceRegionId) { this.sourceRegionId = sourceRegionId; }

    public String getSourcePhotoId() { return sourcePhotoId; }
    public void setSourcePhotoId(String sourcePhotoId) { this.sourcePhotoId = sourcePhotoId; }

    public String getImageAssetId() { return imageAssetId; }
    public void setImageAssetId(String imageAssetId) { this.imageAssetId = imageAssetId; }

    public String getContentSnapshotJson() { return contentSnapshotJson; }
    public void setContentSnapshotJson(String contentSnapshotJson) { this.contentSnapshotJson = contentSnapshotJson; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", insertable = false, updatable = false)
    private PrintTask taskRef;

    public PrintTask getTaskRef() { return taskRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_entry_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private BookEntry sourceEntryRef;

    public BookEntry getSourceEntryRef() { return sourceEntryRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_region_id", insertable = false, updatable = false)
    private QuestionRegion sourceRegionRef;

    public QuestionRegion getSourceRegionRef() { return sourceRegionRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_photo_id", insertable = false, updatable = false)
    private CapturePhoto sourcePhotoRef;

    public CapturePhoto getSourcePhotoRef() { return sourcePhotoRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "image_asset_id", insertable = false, updatable = false)
    private MediaAsset imageAssetRef;

    public MediaAsset getImageAssetRef() { return imageAssetRef; }
}
