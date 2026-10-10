package com.yingying.cuotiku.server.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 打印任务事件。关联按标量 ID 写入，Ref 属性仅提供只读延迟导航。 */
@Entity
@org.hibernate.annotations.Immutable
@Table(name = "print_task_event", indexes = {
        @Index(name = "idx_print_task_event_task_id", columnList = "task_id")
})
public class PrintTaskEvent {

    /** 事件主键 */
    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    /** 所属打印任务 */
    @Column(name = "task_id", nullable = false, length = 36)
    private String taskId;

    /** CREATED / RENDER_STARTED / PDF_READY / FAILED / RETRIED / DOWNLOADED / CANCELLED / PRINT_CONFIRMED */
    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    /** USER / WORKER / PRINT_PROVIDER */
    @Column(name = "actor_type", nullable = false, length = 16)
    private String actorType;

    /** 账号／工作节点／服务标识 */
    @Column(name = "actor_id", length = 64)
    private String actorId;

    /** 原状态 */
    @Column(name = "from_status", length = 24)
    private String fromStatus;

    /** 新状态 */
    @Column(name = "to_status", length = 24)
    private String toStatus;

    /** 失败码、回执编号等，不含敏感凭据 */
    @Column(name = "metadata_json", nullable = false, columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    private String metadataJson = "{}";

    /** 事件时间 */
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getActorType() { return actorType; }
    public void setActorType(String actorType) { this.actorType = actorType; }

    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }

    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }

    public String getToStatus() { return toStatus; }
    public void setToStatus(String toStatus) { this.toStatus = toStatus; }

    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }

    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", insertable = false, updatable = false)
    private PrintTask taskRef;

    public PrintTask getTaskRef() { return taskRef; }
}
