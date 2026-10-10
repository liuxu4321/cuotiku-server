package com.yingying.cuotiku.server.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** 后台写操作审计，不保存密码、令牌、验证码或云密钥。 */
@Entity
@Table(
    name = "mini_admin_audit",
    indexes = @Index(name = "idx_mini_audit_actor", columnList = "actor_id,created_at"))
public class MiniAdminAudit {
  @Id
  @Column(length = 36)
  private String id;

  @Column(name = "actor_id", nullable = false)
  private Long actorId;

  @Column(nullable = false, length = 16)
  private String method;

  @Column(name = "target_path", nullable = false, length = 300)
  private String targetPath;

  @Column(name = "http_status")
  private Integer httpStatus;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(length = 500)
  private String reason;

  public String getReason() {
    return reason;
  }

  public void setReason(String v) {
    reason = v;
  }

  public String getId() {
    return id;
  }

  public void setId(String v) {
    id = v;
  }

  public Long getActorId() {
    return actorId;
  }

  public void setActorId(Long v) {
    actorId = v;
  }

  public String getMethod() {
    return method;
  }

  public void setMethod(String v) {
    method = v;
  }

  public String getTargetPath() {
    return targetPath;
  }

  public void setTargetPath(String v) {
    targetPath = v;
  }

  public Integer getHttpStatus() {
    return httpStatus;
  }

  public void setHttpStatus(Integer v) {
    httpStatus = v;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant v) {
    createdAt = v;
  }
}
