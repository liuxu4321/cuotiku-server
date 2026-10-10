package com.yingying.cuotiku.server.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** 资产上传会话。关联按标量 ID 写入，Ref 属性仅提供只读延迟导航。 */
@Entity
@Table(
    name = "asset_upload_session",
    indexes = {
      @Index(name = "idx_asset_upload_session_owner", columnList = "user_id,student_id,created_at"),
      @Index(name = "idx_asset_upload_session_status", columnList = "status,created_at")
    },
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_asset_upload_session_0",
          columnNames = {"user_id", "client_request_id"})
    })
public class AssetUploadSession {

  /** 上传会话主键 */
  @Id
  @Column(name = "id", nullable = false, length = 36)
  private String id;

  /** 待上传资产 */
  @Column(name = "asset_id", nullable = false, length = 36)
  private String assetId;

  /** 上传账号 */
  @Column(name = "user_id", nullable = false)
  private Long userId;

  /** 业务资产所属学生 */
  @Column(name = "student_id", length = 36)
  private String studentId;

  /** 客户端幂等键 */
  @Column(name = "client_request_id", nullable = false, length = 64)
  private String clientRequestId;

  /** 允许上传的目标大小 */
  @Column(name = "expected_size_bytes", nullable = false)
  private Long expectedSizeBytes;

  /** 声明类型，完成时仍要检查内容 */
  @Column(name = "expected_mime_type", nullable = false, length = 128)
  private String expectedMimeType;

  /** 客户端声明摘要，仅用于与实际结果核对 */
  @Column(name = "expected_checksum", length = 64)
  private String expectedChecksum;

  /** ISSUED / COMPLETED / FAILED / EXPIRED */
  @Column(name = "status", nullable = false, length = 16)
  private String status = "ISSUED";

  /** 凭证／会话到期时间 */
  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  /** 完成对象 HEAD／内容校验的时间 */
  @Column(name = "completed_at")
  private Instant completedAt;

  /** 创建时间 */
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  /** 更新时间 */
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** 签名上传暂存键；完成后资产转存，避免PUT签名重放修改已发布图片。 */
  @Column(name = "staging_object_key", length = 512)
  private String stagingObjectKey;

  public String getStagingObjectKey() {
    return stagingObjectKey;
  }

  public void setStagingObjectKey(String v) {
    stagingObjectKey = v;
  }

  @PrePersist
  void onCreate() {
    if (id == null) id = UUID.randomUUID().toString();
    if (createdAt == null) createdAt = Instant.now();
    if (updatedAt == null) updatedAt = createdAt;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getAssetId() {
    return assetId;
  }

  public void setAssetId(String assetId) {
    this.assetId = assetId;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public String getStudentId() {
    return studentId;
  }

  public void setStudentId(String studentId) {
    this.studentId = studentId;
  }

  public String getClientRequestId() {
    return clientRequestId;
  }

  public void setClientRequestId(String clientRequestId) {
    this.clientRequestId = clientRequestId;
  }

  public Long getExpectedSizeBytes() {
    return expectedSizeBytes;
  }

  public void setExpectedSizeBytes(Long expectedSizeBytes) {
    this.expectedSizeBytes = expectedSizeBytes;
  }

  public String getExpectedMimeType() {
    return expectedMimeType;
  }

  public void setExpectedMimeType(String expectedMimeType) {
    this.expectedMimeType = expectedMimeType;
  }

  public String getExpectedChecksum() {
    return expectedChecksum;
  }

  public void setExpectedChecksum(String expectedChecksum) {
    this.expectedChecksum = expectedChecksum;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(Instant completedAt) {
    this.completedAt = completedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "asset_id", insertable = false, updatable = false)
  private MediaAsset assetRef;

  public MediaAsset getAssetRef() {
    return assetRef;
  }

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(
      name = "user_id",
      insertable = false,
      updatable = false,
      foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
  private User userRef;

  public User getUserRef() {
    return userRef;
  }

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "student_id", insertable = false, updatable = false)
  private StudentProfile studentRef;

  public StudentProfile getStudentRef() {
    return studentRef;
  }
}
