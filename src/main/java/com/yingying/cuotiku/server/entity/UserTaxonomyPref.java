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

    @Column(nullable = false)
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
}
