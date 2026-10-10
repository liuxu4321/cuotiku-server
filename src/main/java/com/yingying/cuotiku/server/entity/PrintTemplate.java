package com.yingying.cuotiku.server.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** 打印模板。关联按标量 ID 写入，Ref 属性仅提供只读延迟导航。 */
@Entity
@Table(name = "print_template", indexes = {
        @Index(name = "idx_print_template_status", columnList = "status,created_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_print_template_0", columnNames = {"code"})
})
public class PrintTemplate {

    /** 模板主键 */
    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    /** 所属模板分类 */
    @Column(name = "category_id", nullable = false, length = 36)
    private String categoryId;

    /** 模板稳定代码 */
    @Column(name = "code", nullable = false, length = 64)
    private String code;

    /** 模板名称 */
    @Column(name = "name", nullable = false, length = 128)
    private String name;

    /** 同分类排序 */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /** DRAFT / PUBLISHED / DISABLED */
    @Column(name = "status", nullable = false, length = 16)
    private String status = "DRAFT";

    /** 当前已发布版本，同模板校验 */
    @Column(name = "current_version_id", length = 36)
    private String currentVersionId;

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

    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCurrentVersionId() { return currentVersionId; }
    public void setCurrentVersionId(String currentVersionId) { this.currentVersionId = currentVersionId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", insertable = false, updatable = false)
    private PrintTemplateCategory categoryRef;

    public PrintTemplateCategory getCategoryRef() { return categoryRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_version_id", insertable = false, updatable = false)
    private PrintTemplateVersion currentVersionRef;

    public PrintTemplateVersion getCurrentVersionRef() { return currentVersionRef; }
}
