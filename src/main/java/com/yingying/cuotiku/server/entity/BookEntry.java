package com.yingying.cuotiku.server.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "book_entry", indexes = {
        @Index(name = "idx_book_user_created", columnList = "userId,createdAt"),
        @Index(name = "idx_book_user_subject", columnList = "userId,subject"),
        @Index(name = "idx_book_user_grade", columnList = "userId,grade")
})
public class BookEntry {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private int grade;

    @Column
    private Integer term;

    @Column(nullable = false, length = 16)
    private String subject;

    @Column(nullable = false, length = 16)
    private String errorType;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    @Column(nullable = false)
    private int practiceCount = 0;

    @Column(nullable = false, length = 256)
    private String objectKey;

    @Column(nullable = false, length = 8)
    private String format = "png";

    @Column(nullable = false)
    private boolean hasThumb = true;

    @Column(nullable = false)
    private long sizeBytes = 0;

    @Column(length = 1000)
    private String remark;

    @Column(length = 1000)
    private String answer;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public int getGrade() { return grade; }
    public void setGrade(int grade) { this.grade = grade; }
    public Integer getTerm() { return term; }
    public void setTerm(Integer term) { this.term = term; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getErrorType() { return errorType; }
    public void setErrorType(String errorType) { this.errorType = errorType; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }
    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }
    public int getPracticeCount() { return practiceCount; }
    public void setPracticeCount(int practiceCount) { this.practiceCount = practiceCount; }
    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    public boolean isHasThumb() { return hasThumb; }
    public void setHasThumb(boolean hasThumb) { this.hasThumb = hasThumb; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
}
