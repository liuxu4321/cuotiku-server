package com.yingying.cuotiku.server.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "book_practice_record", indexes = {
        @Index(name = "idx_practice_entry_time", columnList = "entryId,practicedAt"),
        @Index(name = "idx_practice_user_time", columnList = "userId,practicedAt")
})
public class BookPracticeRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 36)
    private String entryId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Instant practicedAt;

    @Column(length = 2000)
    private String answerContent;

    @Column(nullable = false)
    private boolean correct;

    public Long getId() { return id; }
    public String getEntryId() { return entryId; }
    public void setEntryId(String entryId) { this.entryId = entryId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Instant getPracticedAt() { return practicedAt; }
    public void setPracticedAt(Instant practicedAt) { this.practicedAt = practicedAt; }
    public String getAnswerContent() { return answerContent; }
    public void setAnswerContent(String answerContent) { this.answerContent = answerContent; }
    public boolean isCorrect() { return correct; }
    public void setCorrect(boolean correct) { this.correct = correct; }
}
