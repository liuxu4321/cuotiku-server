package com.yingying.cuotiku.server.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "client_keepalive", indexes = {
        @Index(name = "idx_keepalive_last_seen", columnList = "last_seen_at"),
        @Index(name = "idx_keepalive_phone", columnList = "phone")
})
public class ClientKeepalive {

    @Id
    @Column(name = "client_id", length = 64)
    private String clientId;

    @Column(name = "phone", length = 20)
    private String phone;    @Column(name = "user_id")
    private Long userId;

    @Column(name = "logged_in", nullable = false)
    private boolean loggedIn = false;

    @Column(name = "app_version", length = 32)
    private String appVersion;

    @Column(name = "platform", length = 32)
    private String platform;

    @Column(name = "os_version", length = 64)
    private String osVersion;

    @Column(name = "state", length = 32)
    private String state;

    @Column(name = "detail", length = 256)
    private String detail;

    @Column(name = "report_count", nullable = false)
    private long reportCount = 0;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public boolean isLoggedIn() { return loggedIn; }
    public void setLoggedIn(boolean loggedIn) { this.loggedIn = loggedIn; }
    public String getAppVersion() { return appVersion; }
    public void setAppVersion(String appVersion) { this.appVersion = appVersion; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public String getOsVersion() { return osVersion; }
    public void setOsVersion(String osVersion) { this.osVersion = osVersion; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public long getReportCount() { return reportCount; }
    public void setReportCount(long reportCount) { this.reportCount = reportCount; }
    public Instant getFirstSeenAt() { return firstSeenAt; }
    public void setFirstSeenAt(Instant firstSeenAt) { this.firstSeenAt = firstSeenAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }

    // Target model extensions remain nullable for pre-student historical rows.

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private User userRef;

    public User getUserRef() { return userRef; }

}
