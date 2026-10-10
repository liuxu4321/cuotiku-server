package com.yingying.cuotiku.server.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** 学生错误类型。关联按标量 ID 写入，Ref 属性仅提供只读延迟导航。 */
@Entity
@Table(name = "error_type", indexes = {
        @Index(name = "idx_error_type_owner", columnList = "user_id,student_id,created_at"),
        @Index(name = "idx_error_type_status", columnList = "status,created_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_error_type_0", columnNames = {"student_id", "code"}),
        @UniqueConstraint(name = "uk_error_type_1", columnNames = {"student_id", "normalized_name"})
})
public class ErrorType {

    /** 错误类型主键 */
    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    /** 所属账号 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 所属学生 */
    @Column(name = "student_id", nullable = false, length = 36)
    private String studentId;

    /** 稳定机器码，自定义类型由服务端生成 */
    @Column(name = "code", nullable = false, length = 64)
    private String code;

    /** 显示名称，例如计算错误 */
    @Column(name = "name", nullable = false, length = 64)
    private String name;

    /** 规范化名称，用于去重 */
    @Column(name = "normalized_name", nullable = false, length = 64)
    private String normalizedName;

    /** 兼容抽题大类 CARELESS / UNFAMILIAR / CONCEPT / OTHER */
    @Column(name = "draw_group_code", nullable = false, length = 16)
    private String drawGroupCode = "OTHER";

    /** 显示顺序 */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /** ACTIVE / ARCHIVED */
    @Column(name = "status", nullable = false, length = 16)
    private String status = "ACTIVE";

    /** 更新修订号 */
    @Column(name = "revision", nullable = false)
    private Integer revision = 0;

    /** 创建时间 */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** 更新时间 */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** 逻辑删除时间 */
    @Column(name = "deleted_at")
    private Instant deletedAt;

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

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getNormalizedName() { return normalizedName; }
    public void setNormalizedName(String normalizedName) { this.normalizedName = normalizedName; }

    public String getDrawGroupCode() { return drawGroupCode; }
    public void setDrawGroupCode(String drawGroupCode) { this.drawGroupCode = drawGroupCode; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getRevision() { return revision; }
    public void setRevision(Integer revision) { this.revision = revision; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private User userRef;

    public User getUserRef() { return userRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", insertable = false, updatable = false)
    private StudentProfile studentRef;

    public StudentProfile getStudentRef() { return studentRef; }
}
