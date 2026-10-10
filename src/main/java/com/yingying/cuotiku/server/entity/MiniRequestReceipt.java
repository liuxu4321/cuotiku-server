package com.yingying.cuotiku.server.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** 小程序幂等回执；保存结果快照而非依赖进程缓存，重启后仍可安全重放。 */
@Entity
@Table(
    name = "mini_request_receipt",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_mini_receipt",
            columnNames = {"user_id", "operation", "request_key"}))
public class MiniRequestReceipt {
  @Id
  @Column(length = 36)
  private String id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(nullable = false, length = 64)
  private String operation;

  @Column(name = "request_key", nullable = false, length = 128)
  private String requestKey;

  @Column(name = "request_hash", nullable = false, length = 64)
  private String requestHash;

  @Column(name = "response_json", nullable = false, columnDefinition = "longtext")
  private String responseJson;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  public String getId() {
    return id;
  }

  public void setId(String v) {
    id = v;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long v) {
    userId = v;
  }

  public String getOperation() {
    return operation;
  }

  public void setOperation(String v) {
    operation = v;
  }

  public String getRequestKey() {
    return requestKey;
  }

  public void setRequestKey(String v) {
    requestKey = v;
  }

  public String getRequestHash() {
    return requestHash;
  }

  public void setRequestHash(String v) {
    requestHash = v;
  }

  public String getResponseJson() {
    return responseJson;
  }

  public void setResponseJson(String v) {
    responseJson = v;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant v) {
    createdAt = v;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Instant v) {
    expiresAt = v;
  }
}
