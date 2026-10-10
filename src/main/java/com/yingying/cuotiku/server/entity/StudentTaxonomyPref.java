package com.yingying.cuotiku.server.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** 学生分类偏好。关联按标量 ID 写入，Ref 属性仅提供只读延迟导航。 */
@Entity
@Table(name = "student_taxonomy_pref", indexes = {
        @Index(name = "idx_student_taxonomy_pref_owner", columnList = "user_id,student_id,created_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_student_taxonomy_pref_0", columnNames = {"user_id", "student_id"})
})
public class StudentTaxonomyPref {

    /** 偏好主键 */
    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    /** 所属账号 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 所属学生 */
    @Column(name = "student_id", nullable = false, length = 36)
    private String studentId;

    /** 默认科目 */
    @Column(name = "default_subject_id")
    private Long defaultSubjectId;

    /** 默认主题 */
    @Column(name = "default_topic_id")
    private Long defaultTopicId;

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

    public Long getDefaultSubjectId() { return defaultSubjectId; }
    public void setDefaultSubjectId(Long defaultSubjectId) { this.defaultSubjectId = defaultSubjectId; }

    public Long getDefaultTopicId() { return defaultTopicId; }
    public void setDefaultTopicId(Long defaultTopicId) { this.defaultTopicId = defaultTopicId; }

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
    @JoinColumn(name = "default_subject_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private UserSubject defaultSubjectRef;

    public UserSubject getDefaultSubjectRef() { return defaultSubjectRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_topic_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private SubjectTopic defaultTopicRef;

    public SubjectTopic getDefaultTopicRef() { return defaultTopicRef; }
}
