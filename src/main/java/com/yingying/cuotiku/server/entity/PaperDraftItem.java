package com.yingying.cuotiku.server.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** 草稿选题项。关联按标量 ID 写入，Ref 属性仅提供只读延迟导航。 */
@Entity
@Table(
    name = "paper_draft_item",
    indexes = {@Index(name = "idx_paper_draft_item_draft_id", columnList = "draft_id")},
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_paper_draft_item_0",
          columnNames = {"draft_id", "sort_order"})
    })
@org.hibernate.annotations.Check(
    constraints =
        "((source_entry_id is not null) + (source_region_id is not null) + (source_photo_id is not"
            + " null)) = 1")
public class PaperDraftItem {

  /** 草稿项主键 */
  @Id
  @Column(name = "id", nullable = false, length = 36)
  private String id;

  /** 所属草稿 */
  @Column(name = "draft_id", nullable = false, length = 36)
  private String draftId;

  /** 选题顺序 */
  @Column(name = "sort_order", nullable = false)
  private Integer sortOrder = 0;

  /** 源错题 */
  @Column(name = "source_entry_id", length = 36)
  private String sourceEntryId;

  /** 源题框 */
  @Column(name = "source_region_id", length = 36)
  private String sourceRegionId;

  /** 源照片 */
  @Column(name = "source_photo_id", length = 36)
  private String sourcePhotoId;

  /** 加入时间 */
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  /** PHOTO草稿固定输入版本，后续切图不会改变打印来源。 */
  @Column(name = "input_revision_id", length = 36)
  private String inputRevisionId;

  public String getInputRevisionId() {
    return inputRevisionId;
  }

  public void setInputRevisionId(String value) {
    inputRevisionId = value;
  }

  @PrePersist
  void onCreate() {
    if (id == null) id = UUID.randomUUID().toString();
    if (createdAt == null) createdAt = Instant.now();
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getDraftId() {
    return draftId;
  }

  public void setDraftId(String draftId) {
    this.draftId = draftId;
  }

  public Integer getSortOrder() {
    return sortOrder;
  }

  public void setSortOrder(Integer sortOrder) {
    this.sortOrder = sortOrder;
  }

  public String getSourceEntryId() {
    return sourceEntryId;
  }

  public void setSourceEntryId(String sourceEntryId) {
    this.sourceEntryId = sourceEntryId;
  }

  public String getSourceRegionId() {
    return sourceRegionId;
  }

  public void setSourceRegionId(String sourceRegionId) {
    this.sourceRegionId = sourceRegionId;
  }

  public String getSourcePhotoId() {
    return sourcePhotoId;
  }

  public void setSourcePhotoId(String sourcePhotoId) {
    this.sourcePhotoId = sourcePhotoId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "draft_id", insertable = false, updatable = false)
  private PaperDraft draftRef;

  public PaperDraft getDraftRef() {
    return draftRef;
  }

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(
      name = "source_entry_id",
      insertable = false,
      updatable = false,
      foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
  private BookEntry sourceEntryRef;

  public BookEntry getSourceEntryRef() {
    return sourceEntryRef;
  }

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "source_region_id", insertable = false, updatable = false)
  private QuestionRegion sourceRegionRef;

  public QuestionRegion getSourceRegionRef() {
    return sourceRegionRef;
  }

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "source_photo_id", insertable = false, updatable = false)
  private CapturePhoto sourcePhotoRef;

  public CapturePhoto getSourcePhotoRef() {
    return sourcePhotoRef;
  }
}
