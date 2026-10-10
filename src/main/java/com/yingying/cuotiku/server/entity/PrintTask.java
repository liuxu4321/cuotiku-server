package com.yingying.cuotiku.server.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 打印任务。关联按标量 ID 写入，Ref 属性仅提供只读延迟导航。 */
@Entity
@Table(name = "print_task", indexes = {
        @Index(name = "idx_print_task_owner", columnList = "user_id,student_id,created_at"),
        @Index(name = "idx_print_task_status", columnList = "status,created_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_print_task_0", columnNames = {"user_id", "client_request_id"}),
        @UniqueConstraint(name = "uk_print_task_1", columnNames = {"task_no"})
})
@org.hibernate.annotations.Check(constraints = "item_count > 0 and copies > 0 and (page_count is null or page_count > 0)")
public class PrintTask {

    /** 任务 ID，卡片左上角显示 */
    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    /** 可选可读编号 */
    @Column(name = "task_no", length = 64)
    private String taskNo;

    /** 提交账号 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 所有题目必须同学生 */
    @Column(name = "student_id", nullable = false, length = 36)
    private String studentId;

    /** 冻结使用的模板版本 */
    @Column(name = "template_version_id", nullable = false, length = 36)
    private String templateVersionId;

    /** 纸张、布局、代码摘要与引擎配置快照 */
    @Column(name = "template_snapshot_json", nullable = false, columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    private String templateSnapshotJson;

    /** COLLECTION / CAPTURE / RANDOM */
    @Column(name = "source_type", nullable = false, length = 16)
    private String sourceType;

    /** QUEUED / RENDERING / READY / FAILED / CANCELLED / PRINT_CONFIRMED */
    @Column(name = "status", nullable = false, length = 24)
    private String status = "QUEUED";

    /** 实际题目项数，大于 0 */
    @Column(name = "item_count", nullable = false)
    private Integer itemCount;

    /** 打印份数，限制合理最大值 */
    @Column(name = "copies", nullable = false)
    private Integer copies = 1;

    /** 实际生成页数 */
    @Column(name = "page_count")
    private Integer pageCount;

    /** 生成 PDF 资产 */
    @Column(name = "output_pdf_asset_id", length = 36)
    private String outputPdfAssetId;

    /** 提交幂等键 */
    @Column(name = "client_request_id", nullable = false, length = 64)
    private String clientRequestId;

    /** 来源、顺序、模板、份数摘要 */
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    /** 生成失败码 */
    @Column(name = "error_code", length = 64)
    private String errorCode;

    /** 脱敏失败原因 */
    @Column(name = "error_message", length = 512)
    private String errorMessage;

    /** 生成／失败／取消终态时间 */
    @Column(name = "finished_at")
    private Instant finishedAt;

    /** 真实打印回执或用户明确确认时间 */
    @Column(name = "print_confirmed_at")
    private Instant printConfirmedAt;

    /** 任务创建时间 */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** 状态更新时间 */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** 用户隐藏任务时间，不触发即时对象删除 */
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

    public String getTaskNo() { return taskNo; }
    public void setTaskNo(String taskNo) { this.taskNo = taskNo; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getTemplateVersionId() { return templateVersionId; }
    public void setTemplateVersionId(String templateVersionId) { this.templateVersionId = templateVersionId; }

    public String getTemplateSnapshotJson() { return templateSnapshotJson; }
    public void setTemplateSnapshotJson(String templateSnapshotJson) { this.templateSnapshotJson = templateSnapshotJson; }

    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getItemCount() { return itemCount; }
    public void setItemCount(Integer itemCount) { this.itemCount = itemCount; }

    public Integer getCopies() { return copies; }
    public void setCopies(Integer copies) { this.copies = copies; }

    public Integer getPageCount() { return pageCount; }
    public void setPageCount(Integer pageCount) { this.pageCount = pageCount; }

    public String getOutputPdfAssetId() { return outputPdfAssetId; }
    public void setOutputPdfAssetId(String outputPdfAssetId) { this.outputPdfAssetId = outputPdfAssetId; }

    public String getClientRequestId() { return clientRequestId; }
    public void setClientRequestId(String clientRequestId) { this.clientRequestId = clientRequestId; }

    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String requestHash) { this.requestHash = requestHash; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }

    public Instant getPrintConfirmedAt() { return printConfirmedAt; }
    public void setPrintConfirmedAt(Instant printConfirmedAt) { this.printConfirmedAt = printConfirmedAt; }

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_version_id", insertable = false, updatable = false)
    private PrintTemplateVersion templateVersionRef;

    public PrintTemplateVersion getTemplateVersionRef() { return templateVersionRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "output_pdf_asset_id", insertable = false, updatable = false)
    private MediaAsset outputPdfAssetRef;

    public MediaAsset getOutputPdfAssetRef() { return outputPdfAssetRef; }
}
