package com.yingying.cuotiku.server.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 打印模板版本与代码。关联按标量 ID 写入，Ref 属性仅提供只读延迟导航。 */
@Entity
@Table(name = "print_template_version", indexes = {
        @Index(name = "idx_print_template_version_template_id", columnList = "template_id"),
        @Index(name = "idx_print_template_version_status", columnList = "status,created_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_print_template_version_0", columnNames = {"template_id", "version_no"})
})
@org.hibernate.annotations.Check(constraints = "paper_width_mm > 0 and paper_height_mm > 0 and slots_per_page > 0")
public class PrintTemplateVersion {

    /** 模板版本主键 */
    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    /** 所属模板 */
    @Column(name = "template_id", nullable = false, length = 36)
    private String templateId;

    /** 递增版本号 */
    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    /** DRAFT / PUBLISHED / RETIRED */
    @Column(name = "status", nullable = false, length = 16)
    private String status = "DRAFT";

    /** 纸张宽度毫米 */
    @Column(name = "paper_width_mm", nullable = false, precision = 8, scale = 2)
    private BigDecimal paperWidthMm;

    /** 纸张高度毫米 */
    @Column(name = "paper_height_mm", nullable = false, precision = 8, scale = 2)
    private BigDecimal paperHeightMm;

    /** PORTRAIT / LANDSCAPE */
    @Column(name = "orientation", nullable = false, length = 16)
    private String orientation;

    /** 版式目标题框数，正整数 */
    @Column(name = "slots_per_page", nullable = false)
    private Integer slotsPerPage;

    /** 边距、题框、缩放、分页、答题与订正区定义 */
    @Column(name = "layout_json", nullable = false, columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    private String layoutJson;

    /** DECLARATIVE / HTML_CSS 等受控渲染类型 */
    @Column(name = "renderer_type", nullable = false, length = 32)
    private String rendererType;

    /** 渲染引擎版本，复现时使用 */
    @Column(name = "renderer_version", nullable = false, length = 64)
    private String rendererVersion;

    /** 需要代码模板时存 HTML／CSS 等受控片段；声明式布局可空 */
    @Column(name = "code_content", columnDefinition = "longtext")
    private String codeContent;

    /** 代码内容摘要 */
    @Column(name = "code_hash", length = 64)
    private String codeHash;

    /** media_asset 中的 SVG 示意图 */
    @Column(name = "preview_svg_asset_id", length = 36)
    private String previewSvgAssetId;

    /** 发布时间 */
    @Column(name = "published_at")
    private Instant publishedAt;

    /** 创建时间 */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }

    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getPaperWidthMm() { return paperWidthMm; }
    public void setPaperWidthMm(BigDecimal paperWidthMm) { this.paperWidthMm = paperWidthMm; }

    public BigDecimal getPaperHeightMm() { return paperHeightMm; }
    public void setPaperHeightMm(BigDecimal paperHeightMm) { this.paperHeightMm = paperHeightMm; }

    public String getOrientation() { return orientation; }
    public void setOrientation(String orientation) { this.orientation = orientation; }

    public Integer getSlotsPerPage() { return slotsPerPage; }
    public void setSlotsPerPage(Integer slotsPerPage) { this.slotsPerPage = slotsPerPage; }

    public String getLayoutJson() { return layoutJson; }
    public void setLayoutJson(String layoutJson) { this.layoutJson = layoutJson; }

    public String getRendererType() { return rendererType; }
    public void setRendererType(String rendererType) { this.rendererType = rendererType; }

    public String getRendererVersion() { return rendererVersion; }
    public void setRendererVersion(String rendererVersion) { this.rendererVersion = rendererVersion; }

    public String getCodeContent() { return codeContent; }
    public void setCodeContent(String codeContent) { this.codeContent = codeContent; }

    public String getCodeHash() { return codeHash; }
    public void setCodeHash(String codeHash) { this.codeHash = codeHash; }

    public String getPreviewSvgAssetId() { return previewSvgAssetId; }
    public void setPreviewSvgAssetId(String previewSvgAssetId) { this.previewSvgAssetId = previewSvgAssetId; }

    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", insertable = false, updatable = false)
    private PrintTemplate templateRef;

    public PrintTemplate getTemplateRef() { return templateRef; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preview_svg_asset_id", insertable = false, updatable = false)
    private MediaAsset previewSvgAssetRef;

    public MediaAsset getPreviewSvgAssetRef() { return previewSvgAssetRef; }
}
