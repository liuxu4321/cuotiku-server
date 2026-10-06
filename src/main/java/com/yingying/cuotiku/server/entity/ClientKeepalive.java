package com.yingying.cuotiku.server.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "client_keepalive", indexes = {
        @Index(name = "idx_keepalive_last_seen", columnList = "lastSeenAt"),
        @Index(name = "idx_keepalive_phone", columnList = "phone")
})
public class ClientKeepalive {

    @Id
    @Column(length = 64)
    private String clientId;

    @Column(length = 20)
    private String phone;

    private Long userId;

    @Column(nullable = false)
    private boolean loggedIn = false;

    @Column(length = 32)
    private String appVersion;

    @Column(length = 32)
    private String platform;

    @Column(length = 64)
    private String osVersion;

    @Column(length = 32)
    private String state;

    @Column(length = 256)
    private String detail;

    @Column(nullable = false)
    private long reportCount = 0;

    @Column(nullable = false)
    private Instant firstSeenAt;

    @Column(nullable = false)
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
}
