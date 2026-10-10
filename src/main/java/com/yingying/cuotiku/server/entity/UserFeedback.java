package com.yingying.cuotiku.server.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** 用户意见反馈。关联按标量 ID 写入，Ref 属性仅提供只读延迟导航。 */
@Entity
@Table(name = "user_feedback", indexes = {
        @Index(name = "idx_user_feedback_user", columnList = "user_id,created_at"),
        @Index(name = "idx_user_feedback_status", columnList = "status,created_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_feedback_0", columnNames = {"user_id", "client_request_id"})
})
public class UserFeedback {

    /** 反馈主键 */
    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    /** 提交会员账号 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 意见内容，长度上限由接口限制 */
    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    /** 提交幂等键 */
    @Column(name = "client_request_id", nullable = false, length = 64)
    private String clientRequestId;

    /** 小程序版本 */
    @Column(name = "app_version", length = 32)
    private String appVersion;

    /** 客户端平台 */
    @Column(name = "platform", length = 32)
    private String platform;

    /** NEW / REVIEWING / RESOLVED / CLOSED */
    @Column(name = "status", nullable = false, length = 16)
    private String status = "NEW";

    /** 内部处理备注，用户不可改 */
    @Column(name = "admin_note", columnDefinition = "text")
    private String adminNote;

    /** 提交时间 */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** 处理更新时间 */
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

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getClientRequestId() { return clientRequestId; }
    public void setClientRequestId(String clientRequestId) { this.clientRequestId = clientRequestId; }

    public String getAppVersion() { return appVersion; }
    public void setAppVersion(String appVersion) { this.appVersion = appVersion; }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAdminNote() { return adminNote; }
    public void setAdminNote(String adminNote) { this.adminNote = adminNote; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private User userRef;

    public User getUserRef() { return userRef; }
}
