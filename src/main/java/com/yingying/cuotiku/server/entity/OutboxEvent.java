package com.yingying.cuotiku.server.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 事务投递事件。关联按标量 ID 写入，Ref 属性仅提供只读延迟导航。 */
@Entity
@Table(name = "outbox_event", indexes = {
        @Index(name = "idx_outbox_event_status", columnList = "status,created_at")
})
public class OutboxEvent {

    /** 事件主键 */
    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    /** PROCESSING_JOB / PRINT_TASK */
    @Column(name = "aggregate_type", nullable = false, length = 32)
    private String aggregateType;

    /** 关联业务主键 */
    @Column(name = "aggregate_id", nullable = false, length = 36)
    private String aggregateId;

    /** 需要触发的异步工作类型 */
    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    /** 业务 ID 等最小消息，不含图片 Base64／密钥 */
    @Column(name = "payload_json", nullable = false, columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    private String payloadJson;

    /** PENDING / SENT / FAILED */
    @Column(name = "status", nullable = false, length = 16)
    private String status = "PENDING";

    /** 发送尝试数 */
    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    /** 下次重试时间 */
    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    /** 与业务任务同事务创建时间 */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** 成功投递时间 */
    @Column(name = "sent_at")
    private Instant sentAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getAggregateType() { return aggregateType; }
    public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }

    public String getAggregateId() { return aggregateId; }
    public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getAttemptCount() { return attemptCount; }
    public void setAttemptCount(Integer attemptCount) { this.attemptCount = attemptCount; }

    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(Instant nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }


}
