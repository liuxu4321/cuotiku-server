package com.yingying.cuotiku.server.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "book_entry", indexes = {
        @Index(name = "idx_book_student_created", columnList = "user_id,student_id,deleted_at,created_at,id"),
        @Index(name = "idx_book_student_filter", columnList = "user_id,student_id,subject_id,topic_id,error_type_id,created_at"),
        @Index(name = "idx_book_student_random", columnList = "user_id,student_id,subject_id,error_type_id,practice_count,id"),

        @Index(name = "idx_book_user_created", columnList = "user_id,created_at"),
        @Index(name = "idx_book_user_subject", columnList = "user_id,subject"),
        @Index(name = "idx_book_user_grade", columnList = "user_id,grade"),
        @Index(name = "idx_book_user_subject_topic", columnList = "user_id,subject_id,topic_id")
})
public class BookEntry {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "grade", nullable = false)
    private int grade;

    @Column(name = "term")
    private Integer term;

    @Column(name = "subject_id")
    private Long subjectId;

    @Column(name = "topic_id")
    private Long topicId;

    @Column(name = "subject", nullable = false, length = 64)
    private String subject;

    @Column(name = "error_type", nullable = false, length = 64)
    private String errorType;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "width", nullable = false)
    private int width;

    @Column(name = "height", nullable = false)
    private int height;

    @Column(name = "practice_count", nullable = false)
    private int practiceCount = 0;

    @Column(name = "object_key", nullable = false, length = 256)
    private String objectKey;

    @Column(name = "format", nullable = false, length = 8)
    private String format = "png";

    @Column(name = "has_thumb", nullable = false)
    private boolean hasThumb = true;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes = 0;

    @Column(name = "remark", length = 1000)
    private String remark;

    @Column(name = "answer", length = 1000)
    private String answer;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public int getGrade() { return grade; }
    public void setGrade(int grade) { this.grade = grade; }
    public Integer getTerm() { return term; }
    public void setTerm(Integer term) { this.term = term; }
    public Long getSubjectId() { return subjectId; }
    public void setSubjectId(Long subjectId) { this.subjectId = subjectId; }
    public Long getTopicId() { return topicId; }
    public void setTopicId(Long topicId) { this.topicId = topicId; }
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

    // Target model extensions remain nullable for pre-student historical rows.
    /** 所属学生 */
    @Column(name = "student_id", length = 36)
    private String studentId;

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    /** 自定义 error_type 的稳定引用 */
    @Column(name = "error_type_id", length = 36)
    private String errorTypeId;

    public String getErrorTypeId() { return errorTypeId; }
    public void setErrorTypeId(String errorTypeId) { this.errorTypeId = errorTypeId; }

    /** 来源题框；整图／旧数据为空 */
    @Column(name = "source_region_id", length = 36)
    private String sourceRegionId;

    public String getSourceRegionId() { return sourceRegionId; }
    public void setSourceRegionId(String sourceRegionId) { this.sourceRegionId = sourceRegionId; }

    /** 来源照片 */
    @Column(name = "source_photo_id", length = 36)
    private String sourcePhotoId;

    public String getSourcePhotoId() { return sourcePhotoId; }
    public void setSourcePhotoId(String sourcePhotoId) { this.sourcePhotoId = sourcePhotoId; }

    /** 来源图片版本 */
    @Column(name = "image_revision_id", length = 36)
    private String imageRevisionId;

    public String getImageRevisionId() { return imageRevisionId; }
    public void setImageRevisionId(String imageRevisionId) { this.imageRevisionId = imageRevisionId; }

    /** 可打印题图资产 */
    @Column(name = "image_asset_id", length = 36)
    private String imageAssetId;

    public String getImageAssetId() { return imageAssetId; }
    public void setImageAssetId(String imageAssetId) { this.imageAssetId = imageAssetId; }

    /** 列表缩略图 */
    @Column(name = "thumbnail_asset_id", length = 36)
    private String thumbnailAssetId;

    public String getThumbnailAssetId() { return thumbnailAssetId; }
    public void setThumbnailAssetId(String thumbnailAssetId) { this.thumbnailAssetId = thumbnailAssetId; }

    /** UNPRACTICED / PRACTICING / MASTERED */
    @Column(name = "mastery_status", length = 16)
    private String masteryStatus = "UNPRACTICED";

    public String getMasteryStatus() { return masteryStatus; }
    public void setMasteryStatus(String masteryStatus) { this.masteryStatus = masteryStatus; }

    /** 正确次数缓存，来源练习记录 */
    @Column(name = "correct_count")
    private Integer correctCount = 0;

    public Integer getCorrectCount() { return correctCount; }
    public void setCorrectCount(Integer correctCount) { this.correctCount = correctCount; }

    /** 最近提交练习时间 */
    @Column(name = "last_practiced_at")
    private Instant lastPracticedAt;

    public Instant getLastPracticedAt() { return lastPracticedAt; }
    public void setLastPracticedAt(Instant lastPracticedAt) { this.lastPracticedAt = lastPracticedAt; }

    /** 最近修改时间 */
    @Column(name = "updated_at")
    private Instant updatedAt;

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    /** 逻辑删除时间 */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }

    /** 乐观锁版本 */
    @Column(name = "version", columnDefinition = "integer default 0")
    @Version
    private Integer version = 0;

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private User userRef;

    public User getUserRef() { return userRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private UserSubject subjectRef;

    public UserSubject getSubjectRef() { return subjectRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private SubjectTopic topicRef;

    public SubjectTopic getTopicRef() { return topicRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private StudentProfile studentRef;

    public StudentProfile getStudentRef() { return studentRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "error_type_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private ErrorType errorTypeRef;

    public ErrorType getErrorTypeRef() { return errorTypeRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_region_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private QuestionRegion sourceRegionRef;

    public QuestionRegion getSourceRegionRef() { return sourceRegionRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_photo_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private CapturePhoto sourcePhotoRef;

    public CapturePhoto getSourcePhotoRef() { return sourcePhotoRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "image_revision_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private ImageRevision imageRevisionRef;

    public ImageRevision getImageRevisionRef() { return imageRevisionRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "image_asset_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private MediaAsset imageAssetRef;

    public MediaAsset getImageAssetRef() { return imageAssetRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thumbnail_asset_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private MediaAsset thumbnailAssetRef;

    public MediaAsset getThumbnailAssetRef() { return thumbnailAssetRef; }

}
