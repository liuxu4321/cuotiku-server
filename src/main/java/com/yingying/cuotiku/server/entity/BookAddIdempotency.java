package com.yingying.cuotiku.server.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "book_add_idempotency", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_request_client", columnNames = {"user_id", "request_id", "client_id"})
})
public class BookAddIdempotency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "request_id", nullable = false, length = 36)
    private String requestId;

    @Column(name = "client_id", nullable = false, length = 36)
    private String clientId;

    @Column(name = "entry_id", nullable = false, length = 36)
    private String entryId;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getEntryId() { return entryId; }
    public void setEntryId(String entryId) { this.entryId = entryId; }
    public Instant getCreatedAt() { return createdAt; }
}
