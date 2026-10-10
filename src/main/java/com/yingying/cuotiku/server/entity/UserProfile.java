package com.yingying.cuotiku.server.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** 账号展示档案。关联按标量 ID 写入，Ref 属性仅提供只读延迟导航。 */
@Entity
@Table(name = "user_profile", indexes = {
        @Index(name = "idx_user_profile_user", columnList = "user_id,created_at")
})
public class UserProfile {

    /** 关联 sys_user.id，一账号一份档案 */
    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 用户主动填写的账号昵称 */
    @Column(name = "nickname", length = 64)
    private String nickname;

    /** 账号头像，关联 media_asset.id，不归属学生 */
    @Column(name = "avatar_asset_id", length = 36)
    private String avatarAssetId;

    /** 最近使用学生，关联 student_profile.id */
    @Column(name = "last_student_id", length = 36)
    private String lastStudentId;

    /** 创建时间 */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** 更新时间 */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getAvatarAssetId() { return avatarAssetId; }
    public void setAvatarAssetId(String avatarAssetId) { this.avatarAssetId = avatarAssetId; }

    public String getLastStudentId() { return lastStudentId; }
    public void setLastStudentId(String lastStudentId) { this.lastStudentId = lastStudentId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private User userRef;

    public User getUserRef() { return userRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "avatar_asset_id", insertable = false, updatable = false)
    private MediaAsset avatarAssetRef;

    public MediaAsset getAvatarAssetRef() { return avatarAssetRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_student_id", insertable = false, updatable = false)
    private StudentProfile lastStudentRef;

    public StudentProfile getLastStudentRef() { return lastStudentRef; }
}
