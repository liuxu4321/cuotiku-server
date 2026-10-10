package com.yingying.cuotiku.server.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** 微信登录身份。关联按标量 ID 写入，Ref 属性仅提供只读延迟导航。 */
@Entity
@Table(name = "user_identity", indexes = {
        @Index(name = "idx_user_identity_user", columnList = "user_id,created_at"),
        @Index(name = "idx_user_identity_status", columnList = "status,created_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_identity_0", columnNames = {"provider", "app_id", "openid"})
})
public class UserIdentity {

    /** 微信身份主键 */
    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    /** 关联 sys_user.id；一条身份最多绑定一个会员账号 */
    @Column(name = "user_id")
    private Long userId;

    /** WECHAT_MINIAPP */
    @Column(name = "provider", nullable = false, length = 32)
    private String provider;

    /** 微信小程序 AppID */
    @Column(name = "app_id", nullable = false, length = 64)
    private String appId;

    /** 该 AppID 下的微信用户标识，仅由后端 code 换取 */
    @Column(name = "openid", nullable = false, length = 128)
    private String openid;

    /** 微信开放平台联合标识，不能作为首次登录必填 */
    @Column(name = "unionid", length = 128)
    private String unionid;

    /** UNBOUND / BOUND / DISABLED */
    @Column(name = "status", nullable = false, length = 16)
    private String status = "UNBOUND";

    /** 会员绑定成功时间 */
    @Column(name = "bound_at")
    private Instant boundAt;

    /** 最近微信登录时间 */
    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /** 创建时间 */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** 更新时间 */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }

    public String getOpenid() { return openid; }
    public void setOpenid(String openid) { this.openid = openid; }

    public String getUnionid() { return unionid; }
    public void setUnionid(String unionid) { this.unionid = unionid; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getBoundAt() { return boundAt; }
    public void setBoundAt(Instant boundAt) { this.boundAt = boundAt; }

    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private User userRef;

    public User getUserRef() { return userRef; }
}
