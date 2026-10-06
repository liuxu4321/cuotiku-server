package com.yingying.cuotiku.server.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "sys_user", indexes = {
        @Index(name = "idx_user_phone", columnList = "phone", unique = true),
        @Index(name = "idx_user_member_no", columnList = "memberNo")
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String phone;

    @Column(length = 64)
    private String memberNo;

    @Column(length = 10)
    private LocalDate memberExpireAt;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role = Role.USER;

    @Column(nullable = false)
    private boolean aiEnabled = false;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private boolean cancelled = false;

    @Column(length = 64)
    private String sessionJti;

    @Column(length = 64)
    private String refreshJti;

    @Column(length = 64)
    private String prevSessionJti;

    @Column(length = 64)
    private String prevRefreshJti;

    @Column(length = 8)
    private String rotateReason;

    private Instant rotatedAt;

    @Column(length = 64)
    private String clientLabel;

    private Instant lastLoginAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getMemberNo() { return memberNo; }
    public void setMemberNo(String memberNo) { this.memberNo = memberNo; }
    public LocalDate getMemberExpireAt() { return memberExpireAt; }
    public void setMemberExpireAt(LocalDate memberExpireAt) { this.memberExpireAt = memberExpireAt; }
    public boolean isMemberActive() {
        return memberExpireAt != null && !memberExpireAt.isBefore(LocalDate.now());
    }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public boolean isAiEnabled() { return aiEnabled; }
    public void setAiEnabled(boolean aiEnabled) { this.aiEnabled = aiEnabled; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    public String getSessionJti() { return sessionJti; }
    public void setSessionJti(String sessionJti) { this.sessionJti = sessionJti; }
    public String getRefreshJti() { return refreshJti; }
    public void setRefreshJti(String refreshJti) { this.refreshJti = refreshJti; }
    public String getPrevSessionJti() { return prevSessionJti; }
    public void setPrevSessionJti(String v) { this.prevSessionJti = v; }
    public String getPrevRefreshJti() { return prevRefreshJti; }
    public void setPrevRefreshJti(String v) { this.prevRefreshJti = v; }
    public String getRotateReason() { return rotateReason; }
    public void setRotateReason(String v) { this.rotateReason = v; }
    public Instant getRotatedAt() { return rotatedAt; }
    public void setRotatedAt(Instant v) { this.rotatedAt = v; }
    public String getClientLabel() { return clientLabel; }
    public void setClientLabel(String clientLabel) { this.clientLabel = clientLabel; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
