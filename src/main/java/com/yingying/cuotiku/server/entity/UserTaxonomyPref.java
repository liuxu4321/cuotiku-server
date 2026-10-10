package com.yingying.cuotiku.server.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_taxonomy_pref")
public class UserTaxonomyPref {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "default_subject_id")
    private Long defaultSubjectId;

    @Column(name = "default_topic_id")
    private Long defaultTopicId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void onTouch() {
        updatedAt = Instant.now();
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getDefaultSubjectId() { return defaultSubjectId; }
    public void setDefaultSubjectId(Long v) { this.defaultSubjectId = v; }
    public Long getDefaultTopicId() { return defaultTopicId; }
    public void setDefaultTopicId(Long v) { this.defaultTopicId = v; }
    public Instant getUpdatedAt() { return updatedAt; }

    // Target model extensions remain nullable for pre-student historical rows.

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_subject_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private UserSubject defaultSubjectRef;

    public UserSubject getDefaultSubjectRef() { return defaultSubjectRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_topic_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private SubjectTopic defaultTopicRef;

    public SubjectTopic getDefaultTopicRef() { return defaultTopicRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private StudentProfile studentRef;

    public StudentProfile getStudentRef() { return studentRef; }

}
