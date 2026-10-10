package com.yingying.cuotiku.server.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** 媒体资产。关联按标量 ID 写入，Ref 属性仅提供只读延迟导航。 */
@Entity
@Table(name = "media_asset", indexes = {
        @Index(name = "idx_media_asset_owner", columnList = "user_id,student_id,created_at"),
        @Index(name = "idx_media_asset_status", columnList = "status,created_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_media_asset_0", columnNames = {"object_locator_hash"})
})
@org.hibernate.annotations.Check(constraints = "size_bytes >= 0 and (width is null or width > 0) and (height is null or height > 0)")
public class MediaAsset {

    /** 资产主键 */
    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    /** 私有资产所属账号；系统模板资产可空 */
    @Column(name = "user_id")
    private Long userId;

    /** 学生业务资产必填；账号头像或公共资产可空 */
    @Column(name = "student_id", length = 36)
    private String studentId;

    /** COS / LOCAL */
    @Column(name = "storage_provider", nullable = false, length = 16)
    private String storageProvider;

    /** 实际 COS 桶名 */
    @Column(name = "bucket", nullable = false, length = 128)
    private String bucket = "";

    /** COS 地域 */
    @Column(name = "region", length = 64)
    private String region;

    /** 长期对象键，不存带有效期签名 URL */
    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    /** 启用 COS 版本控制时的版本标识 */
    @Column(name = "object_version_id", nullable = false, length = 256)
    private String objectVersionId = "";

    /** 真实对象类型；ZIP 为 application/zip */
    @Column(name = "mime_type", nullable = false, length = 128)
    private String mimeType;

    /** png / jpeg / pdf / svg / zip 等 */
    @Column(name = "format", length = 16)
    private String format;

    /** 像素宽度 */
    @Column(name = "width")
    private Integer width;

    /** 像素高度 */
    @Column(name = "height")
    private Integer height;

    /** 实际对象字节数，不是 Base64 字符串长度 */
    @Column(name = "size_bytes")
    private Long sizeBytes;

    /** 服务端确认的内容摘要 */
    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    /** COS 返回 ETag，不作为所有上传方式的 SHA-256 */
    @Column(name = "etag", length = 128)
    private String etag;

    /** ORIGINAL / PROCESSED / CROP / THUMBNAIL / PDF / TEMPLATE_PREVIEW / LEGACY_PACKAGE / AVATAR */
    @Column(name = "purpose", nullable = false, length = 32)
    private String purpose;

    /** PENDING_UPLOAD / AVAILABLE / FAILED / DELETE_PENDING / DELETED */
    @Column(name = "status", nullable = false, length = 24)
    private String status = "PENDING_UPLOAD";

    /** 未引用临时资产清理候选时间，不是立即删除指令 */
    @Column(name = "expires_at")
    private Instant expiresAt;

    /** 创建时间 */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** 更新时间 */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** 确认对象删除时间 */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = createdAt;
        if (bucket == null) bucket = "";
        if (objectVersionId == null) objectVersionId = "";
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getStorageProvider() { return storageProvider; }
    public void setStorageProvider(String storageProvider) { this.storageProvider = storageProvider; }

    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }

    public String getObjectVersionId() { return objectVersionId; }
    public void setObjectVersionId(String objectVersionId) { this.objectVersionId = objectVersionId; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }

    public Integer getWidth() { return width; }
    public void setWidth(Integer width) { this.width = width; }

    public Integer getHeight() { return height; }
    public void setHeight(Integer height) { this.height = height; }

    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }

    public String getChecksumSha256() { return checksumSha256; }
    public void setChecksumSha256(String checksumSha256) { this.checksumSha256 = checksumSha256; }

    public String getEtag() { return etag; }
    public void setEtag(String etag) { this.etag = etag; }

    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

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
    /** Compact uniqueness key avoids MySQL utf8mb4 composite-index length limits. */
    @Column(name = "object_locator_hash", insertable = false, updatable = false,
            columnDefinition = "char(64) generated always as (sha2(concat(storage_provider, char(0), bucket, char(0), object_key, char(0), object_version_id), 256)) stored")
    private String objectLocatorHash;

    public String getObjectLocatorHash() { return objectLocatorHash; }
}
