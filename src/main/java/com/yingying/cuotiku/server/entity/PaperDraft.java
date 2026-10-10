package com.yingying.cuotiku.server.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 选题组卷草稿。关联按标量 ID 写入，Ref 属性仅提供只读延迟导航。 */
@Entity
@Table(name = "paper_draft", indexes = {
        @Index(name = "idx_paper_draft_owner", columnList = "user_id,student_id,created_at"),
        @Index(name = "idx_paper_draft_status", columnList = "status,created_at")
})
public class PaperDraft {

    /** 草稿主键 */
    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    /** 所属账号 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 固定学生 */
    @Column(name = "student_id", nullable = false, length = 36)
    private String studentId;

    /** COLLECTION / CAPTURE / RANDOM */
    @Column(name = "source_type", nullable = false, length = 16)
    private String sourceType;

    /** 当前预选模板 */
    @Column(name = "selected_template_id", length = 36)
    private String selectedTemplateId;

    /** 随机组卷科目／主题／错误类型题数与算法版本 */
    @Column(name = "filter_snapshot_json", columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    private String filterSnapshotJson;

    /** ACTIVE / SUBMITTED / EXPIRED */
    @Column(name = "status", nullable = false, length = 16)
    private String status = "ACTIVE";

    /** 乐观锁 */
    @Version
    @Column(name = "version", nullable = false)
    private Integer version = 0;

    /** 草稿过期时间 */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

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

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    public String getSelectedTemplateId() { return selectedTemplateId; }
    public void setSelectedTemplateId(String selectedTemplateId) { this.selectedTemplateId = selectedTemplateId; }

    public String getFilterSnapshotJson() { return filterSnapshotJson; }
    public void setFilterSnapshotJson(String filterSnapshotJson) { this.filterSnapshotJson = filterSnapshotJson; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private User userRef;

    public User getUserRef() { return userRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", insertable = false, updatable = false)
    private StudentProfile studentRef;

    public StudentProfile getStudentRef() { return studentRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_template_id", insertable = false, updatable = false)
    private PrintTemplate selectedTemplateRef;

    public PrintTemplate getSelectedTemplateRef() { return selectedTemplateRef; }
}
