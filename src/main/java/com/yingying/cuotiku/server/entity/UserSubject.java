package com.yingying.cuotiku.server.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_subject", indexes = {
        @Index(name = "idx_subject_student_sort", columnList = "user_id,student_id,sort_order,id"),

        @Index(name = "idx_user_sort", columnList = "user_id,sort_order,id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_subject_scope_name", columnNames = {"scope_key", "normalized_name"}),
        @UniqueConstraint(name = "uk_subject_scope_system", columnNames = {"scope_key", "system_key"})
})
public class UserSubject {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "name", nullable = false, length = 30)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 30)
    private String normalizedName;

    @Column(name = "system_key", length = 16)
    private String systemKey;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "status", nullable = false, length = 10)
    private String status = STATUS_ACTIVE;

    @Column(name = "revision", nullable = false)
    private int revision = 0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
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
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNormalizedName() { return normalizedName; }
    public void setNormalizedName(String v) { this.normalizedName = v; }
    public String getSystemKey() { return systemKey; }
    public void setSystemKey(String systemKey) { this.systemKey = systemKey; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getRevision() { return revision; }
    public void setRevision(int revision) { this.revision = revision; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    // Target model extensions remain nullable for pre-student historical rows.
    /** 所属学生，关联 student_profile.id */
    @Column(name = "student_id", length = 36)
    private String studentId;

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    /** 逻辑删除时间 */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private User userRef;

    public User getUserRef() { return userRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private StudentProfile studentRef;

    public StudentProfile getStudentRef() { return studentRef; }

    /** MySQL generated scope preserves legacy account-only uniqueness and permits per-student names. */
    @Column(name = "scope_key", insertable = false, updatable = false,
            columnDefinition = "varchar(80) generated always as (case when student_id is null then concat('user:', user_id) else concat('student:', student_id) end) stored")
    private String scopeKey;

    public String getScopeKey() { return scopeKey; }
}
