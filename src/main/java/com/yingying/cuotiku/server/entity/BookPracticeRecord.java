package com.yingying.cuotiku.server.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "book_practice_record", uniqueConstraints = {
        @UniqueConstraint(name = "uk_practice_request", columnNames = {"user_id", "client_request_id"})
}, indexes = {
        @Index(name = "idx_practice_student_time", columnList = "user_id,student_id,practiced_at"),

        @Index(name = "idx_practice_entry_time", columnList = "entry_id,practiced_at"),
        @Index(name = "idx_practice_user_time", columnList = "user_id,practiced_at")
})
public class BookPracticeRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "entry_id", nullable = false, length = 36)
    private String entryId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "practiced_at", nullable = false)
    private Instant practicedAt;

    @Column(name = "answer_content", length = 2000)
    private String answerContent;

    @Column(name = "correct", nullable = false)
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

    // Target model extensions remain nullable for pre-student historical rows.
    /** 与错题所属学生一致 */
    @Column(name = "student_id", length = 36)
    private String studentId;

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    /** 练习提交幂等键 */
    @Column(name = "client_request_id", length = 64)
    private String clientRequestId;

    public String getClientRequestId() { return clientRequestId; }
    public void setClientRequestId(String clientRequestId) { this.clientRequestId = clientRequestId; }

    /** PRACTICE / REVIEW */
    @Column(name = "mode", length = 16)
    private String mode = "PRACTICE";

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    /** 练习用时，非负 */
    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }

    /** 服务端记录创建时间 */
    @Column(name = "created_at")
    private Instant createdAt;

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entry_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private BookEntry entryRef;

    public BookEntry getEntryRef() { return entryRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private User userRef;

    public User getUserRef() { return userRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private StudentProfile studentRef;

    public StudentProfile getStudentRef() { return studentRef; }

}
