package com.yingying.cuotiku.server.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** 学生档案。关联按标量 ID 写入，Ref 属性仅提供只读延迟导航。 */
@Entity
@Table(name = "student_profile", indexes = {
        @Index(name = "idx_student_profile_user", columnList = "user_id,created_at"),
        @Index(name = "idx_student_profile_status", columnList = "status,created_at")
})
@org.hibernate.annotations.Check(constraints = "grade between 1 and 12 and term in (1,2)")
public class StudentProfile {

    /** 学生主键 */
    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    /** 唯一所属账号，关联 sys_user.id */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 孩子姓名或昵称，可重复 */
    @Column(name = "nickname", nullable = false, length = 64)
    private String nickname;

    /** 学生头像资产 */
    @Column(name = "avatar_asset_id", length = 36)
    private String avatarAssetId;

    /** 当前年级：1～6 小学，7～9 初中，10～12 高中 */
    @Column(name = "grade", nullable = false, columnDefinition = "smallint")
    private Integer grade;

    /** 1 上学期／2 下学期 */
    @Column(name = "term", nullable = false, columnDefinition = "smallint")
    private Integer term = 1;

    /** ACTIVE / ARCHIVED */
    @Column(name = "status", nullable = false, length = 16)
    private String status = "ACTIVE";

    /** 学生卡片显示顺序 */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /** 可选模板偏好，不要求当前 UI 提供配置入口 */
    @Column(name = "default_template_id", length = 36)
    private String defaultTemplateId;

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

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getAvatarAssetId() { return avatarAssetId; }
    public void setAvatarAssetId(String avatarAssetId) { this.avatarAssetId = avatarAssetId; }

    public Integer getGrade() { return grade; }
    public void setGrade(Integer grade) { this.grade = grade; }

    public Integer getTerm() { return term; }
    public void setTerm(Integer term) { this.term = term; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public String getDefaultTemplateId() { return defaultTemplateId; }
    public void setDefaultTemplateId(String defaultTemplateId) { this.defaultTemplateId = defaultTemplateId; }

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
    @JoinColumn(name = "avatar_asset_id", insertable = false, updatable = false)
    private MediaAsset avatarAssetRef;

    public MediaAsset getAvatarAssetRef() { return avatarAssetRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_template_id", insertable = false, updatable = false)
    private PrintTemplate defaultTemplateRef;

    public PrintTemplate getDefaultTemplateRef() { return defaultTemplateRef; }
}
