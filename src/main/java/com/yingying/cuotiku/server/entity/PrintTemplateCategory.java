package com.yingying.cuotiku.server.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** 模板分类。关联按标量 ID 写入，Ref 属性仅提供只读延迟导航。 */
@Entity
@Table(name = "print_template_category", uniqueConstraints = {
        @UniqueConstraint(name = "uk_print_template_category_0", columnNames = {"code"})
})
public class PrintTemplateCategory {

    /** 分类主键 */
    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    /** A4 / B5 等稳定分类码 */
    @Column(name = "code", nullable = false, length = 32)
    private String code;

    /** 显示名称 */
    @Column(name = "name", nullable = false, length = 64)
    private String name;

    /** 分组顺序 */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /** 是否展示并允许使用 */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

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

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }


}
