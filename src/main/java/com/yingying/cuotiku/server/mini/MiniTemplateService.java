package com.yingying.cuotiku.server.mini;

import static com.yingying.cuotiku.server.mini.MiniSupport.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.yingying.cuotiku.server.entity.*;
import com.yingying.cuotiku.server.web.ApiException;
import jakarta.persistence.LockModeType;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.*;

/** 模板身份与版本分离；发布后版本不可修改，JSON声明式布局不会执行后台任意代码。 */
@Service
@Transactional
public class MiniTemplateService {
  private final MiniSupport s;
  private final MiniAssetService assets;

  public MiniTemplateService(MiniSupport s, MiniAssetService assets) {
    this.s = s;
    this.assets = assets;
  }

  public PrintTemplate published(String id) {
    PrintTemplate row = s.get(PrintTemplate.class, id);
    PrintTemplateCategory category = s.get(PrintTemplateCategory.class, row.getCategoryId());
    if (!Boolean.TRUE.equals(category.getEnabled())
        || !"PUBLISHED".equals(row.getStatus())
        || row.getCurrentVersionId() == null) throw ApiException.conflict("模板不可用");
    return row;
  }

  public PrintTemplateVersion version(String templateId, String versionId, boolean publicOnly) {
    PrintTemplate row = s.get(PrintTemplate.class, templateId);
    PrintTemplateVersion version = s.get(PrintTemplateVersion.class, versionId);
    if (!version.getTemplateId().equals(row.getId())) throw ApiException.notFound("模板版本不存在");
    if (publicOnly && !"PUBLISHED".equals(version.getStatus()))
      throw ApiException.notFound("模板版本尚未发布");
    return version;
  }

  @Transactional(readOnly = true)
  public Object categories(boolean admin, Map<String, String> query) {
    Map<String, Object> filter = admin ? empty() : map("enabled", true);
    List<PrintTemplateCategory> rows = s.find(PrintTemplateCategory.class, filter);
    rows.sort(
        Comparator.comparing(PrintTemplateCategory::getSortOrder)
            .thenComparing(PrintTemplateCategory::getId));
    return map(
        "items",
        rows.stream()
            .map(
                row -> {
                  Map<String, Object> page =
                      (Map<String, Object>)
                          templates(
                              row.getId(),
                              Map.of("limit", query.getOrDefault("templatesPerCategory", "10")),
                              admin);
                  Map<String, Object> dto = categoryDto(row);
                  dto.putAll(
                      map(
                          "templates",
                          page.get("items"),
                          "nextTemplateCursor",
                          page.get("nextCursor"),
                          "hasMoreTemplates",
                          page.get("hasMore")));
                  if (admin) dto.put("enabled", row.getEnabled());
                  return dto;
                })
            .toList());
  }

  @Transactional(readOnly = true)
  public Object templates(String categoryId, Map<String, String> query, boolean admin) {
    Map<String, Object> f = empty();
    if (categoryId != null) {
      PrintTemplateCategory category = s.get(PrintTemplateCategory.class, categoryId);
      if (!admin && !Boolean.TRUE.equals(category.getEnabled()))
        throw ApiException.notFound("分类不可用");
      f.put("categoryId", categoryId);
    }
    if (!admin) f.put("status", "PUBLISHED");
    else if (query.containsKey("status"))
      f.put("status", oneOf(query.get("status"), "DRAFT", "PUBLISHED", "DISABLED"));
    return s.page(PrintTemplate.class, f, query, "sortOrder", false, this::templateDto);
  }

  @Transactional(readOnly = true)
  public Object versionDto(String templateId, String versionId) {
    return versionDto(version(templateId, versionId, true), false);
  }

  @Transactional(readOnly = true)
  public Object preview(String templateId, Map<String, String> query) {
    PrintTemplate row = s.get(PrintTemplate.class, templateId);
    String versionId = query.getOrDefault("versionId", row.getCurrentVersionId());
    if (versionId == null) throw ApiException.notFound("模板未发布");
    PrintTemplateVersion version = version(templateId, versionId, true);
    if (version.getPreviewSvgAssetId() == null) throw ApiException.notFound("模板示意图尚未配置");
    MediaAsset asset = s.get(MediaAsset.class, version.getPreviewSvgAssetId());
    if (asset.getUserId() != null
        || !"AVAILABLE".equals(asset.getStatus())
        || asset.getDeletedAt() != null
        || !"TEMPLATE_PREVIEW".equals(asset.getPurpose())) throw ApiException.notFound("示意图不存在");
    Map<String, Object> out = (Map<String, Object>) assets.access(asset);
    out.put("contentType", "image/svg+xml");
    return out;
  }

  public Object createCategory(JsonNode body) {
    PrintTemplateCategory row = new PrintTemplateCategory();
    row.setCode(machineCode(body, "code"));
    if (row.getCode().length() > 32) throw ApiException.badRequest("分类代码最长32字符");
    if (s.count(PrintTemplateCategory.class, map("code", row.getCode())) > 0)
      throw ApiException.conflict("分类代码重复");
    row.setName(required(body, "name", 64));
    row.setSortOrder(integer(body, "sortOrder", 0, 0, 100000));
    row.setEnabled(bool(body, "enabled", true));
    s.save(row);
    return categoryDto(row);
  }

  public Object updateCategory(String id, JsonNode body) {
    PrintTemplateCategory row = s.get(PrintTemplateCategory.class, id);
    if (body.has("name")) row.setName(required(body, "name", 64));
    if (body.has("sortOrder")) row.setSortOrder(integer(body, "sortOrder", 0, 0, 100000));
    if (body.has("enabled")) row.setEnabled(bool(body, "enabled", true));
    return categoryDto(row);
  }

  private String machineCode(JsonNode body, String key) {
    String code = required(body, key, 64);
    if (!code.matches("[A-Z0-9_]+")) throw ApiException.badRequest("代码仅允许大写字母、数字和下划线");
    return code;
  }

  public Object createTemplate(JsonNode body) {
    PrintTemplate row = new PrintTemplate();
    row.setCategoryId(s.get(PrintTemplateCategory.class, required(body, "categoryId", 36)).getId());
    row.setCode(machineCode(body, "code"));
    if (s.count(PrintTemplate.class, map("code", row.getCode())) > 0)
      throw ApiException.conflict("模板代码重复");
    row.setName(required(body, "name", 64));
    row.setSortOrder(integer(body, "sortOrder", 0, 0, 100000));
    s.save(row);
    return templateDto(row);
  }

  public Object updateTemplate(String id, JsonNode body) {
    PrintTemplate row = s.get(PrintTemplate.class, id);
    if (body.has("name")) row.setName(required(body, "name", 64));
    if (body.has("sortOrder")) row.setSortOrder(integer(body, "sortOrder", 0, 0, 100000));
    if (body.has("status")) {
      String status = oneOf(required(body, "status", 16), "DISABLED", "PUBLISHED");
      if (status.equals("PUBLISHED")
          && (row.getCurrentVersionId() == null
              || !"PUBLISHED"
                  .equals(
                      s.get(PrintTemplateVersion.class, row.getCurrentVersionId()).getStatus())))
        throw ApiException.conflict("需要先发布模板版本");
      row.setStatus(status);
    }
    return templateDto(row);
  }

  public Object createVersion(String templateId, JsonNode body) {
    PrintTemplate template = s.get(PrintTemplate.class, templateId);
    s.em.lock(template, LockModeType.PESSIMISTIC_WRITE);
    PrintTemplateVersion version = new PrintTemplateVersion();
    version.setTemplateId(templateId);
    version.setVersionNo(
        s.find(PrintTemplateVersion.class, map("templateId", templateId)).stream()
                .mapToInt(PrintTemplateVersion::getVersionNo)
                .max()
                .orElse(0)
            + 1);
    applyVersion(version, body);
    s.save(version);
    return versionDto(version, true);
  }

  @Transactional(readOnly = true)
  public Object versions(String templateId) {
    s.get(PrintTemplate.class, templateId);
    List<PrintTemplateVersion> versions =
        s.find(PrintTemplateVersion.class, map("templateId", templateId));
    versions.sort(Comparator.comparing(PrintTemplateVersion::getVersionNo).reversed());
    return map("items", versions.stream().map(v -> versionDto(v, true)).toList());
  }

  public Object updateVersion(String templateId, String id, JsonNode body) {
    PrintTemplateVersion version = version(templateId, id, false);
    s.em.lock(version, LockModeType.PESSIMISTIC_WRITE);
    if (!"DRAFT".equals(version.getStatus())) throw ApiException.conflict("已发布版本不可修改，请新建版本");
    applyVersion(version, body);
    return versionDto(version, true);
  }

  private void applyVersion(PrintTemplateVersion version, JsonNode body) {
    version.setPaperWidthMm(mm(body, "paperWidthMm"));
    version.setPaperHeightMm(mm(body, "paperHeightMm"));
    version.setOrientation(oneOf(required(body, "orientation", 16), "PORTRAIT", "LANDSCAPE"));
    version.setSlotsPerPage(integer(body, "slotsPerPage", -1, 1, 16));
    if (version.getSlotsPerPage() < 1) throw ApiException.badRequest("slotsPerPage必填");
    version.setRendererType(oneOf(required(body, "rendererType", 32), "DECLARATIVE"));
    version.setRendererVersion(oneOf(required(body, "rendererVersion", 32), "1"));
    JsonNode layout = body.get("layout");
    validateLayout(layout, version);
    version.setLayoutJson(s.encode(layout));
    version.setCodeContent(text(body, "codeContent"));
    version.setCodeHash(
        s.hash(
            map(
                "layout",
                layout,
                "codeContent",
                version.getCodeContent(),
                "rendererVersion",
                version.getRendererVersion())));
    String assetId = text(body, "previewSvgAssetId");
    if (assetId != null) {
      MediaAsset asset = s.get(MediaAsset.class, assetId);
      if (asset.getUserId() != null
          || !"AVAILABLE".equals(asset.getStatus())
          || !"TEMPLATE_PREVIEW".equals(asset.getPurpose())
          || asset.getDeletedAt() != null) throw ApiException.badRequest("无效的系统SVG资产");
    }
    version.setPreviewSvgAssetId(assetId);
  }

  private BigDecimal mm(JsonNode body, String key) {
    try {
      BigDecimal value =
          new BigDecimal(required(body, key, 20)).setScale(2, java.math.RoundingMode.UNNECESSARY);
      if (value.signum() <= 0 || value.compareTo(new BigDecimal("1000")) > 0)
        throw new IllegalArgumentException();
      return value;
    } catch (Exception e) {
      throw ApiException.badRequest(key + "必须为0～1000mm且至多两位小数");
    }
  }

  public static void validateLayout(JsonNode layout, PrintTemplateVersion version) {
    if (layout == null
        || !layout.isObject()
        || layout.path("schemaVersion").asInt() != 1
        || !"mm".equals(layout.path("unit").asText())
        || !"CONTAIN".equals(layout.path("imageFit").asText()))
      throw ApiException.badRequest("模板仅支持schemaVersion=1、mm、CONTAIN布局");
    if (layout.path("showAnswer").asBoolean())
      throw ApiException.badRequest("当前渲染器仅支持题图，showAnswer需为false");
    JsonNode paper = layout.path("paper");
    if (Math.abs(paper.path("width").asDouble() - version.getPaperWidthMm().doubleValue()) > .001
        || Math.abs(paper.path("height").asDouble() - version.getPaperHeightMm().doubleValue())
            > .001
        || !version.getOrientation().equals(paper.path("orientation").asText()))
      throw ApiException.badRequest("纸张布局与版本参数不一致");
    JsonNode slots = layout.path("slots");
    if (!slots.isArray() || slots.size() != version.getSlotsPerPage())
      throw ApiException.badRequest("题框数与slotsPerPage不一致");
    for (JsonNode slot : slots) {
      for (String key : List.of("x", "y", "width", "height"))
        if (!slot.path(key).isNumber() || !Double.isFinite(slot.path(key).asDouble()))
          throw ApiException.badRequest("布局坐标无效");
      double x = slot.path("x").asDouble(),
          y = slot.path("y").asDouble(),
          w = slot.path("width").asDouble(),
          h = slot.path("height").asDouble();
      if (x < 0
          || y < 0
          || w <= 0
          || h <= 0
          || x + w > version.getPaperWidthMm().doubleValue()
          || y + h > version.getPaperHeightMm().doubleValue())
        throw ApiException.badRequest("布局超出纸张");
    }
  }

  public Object publish(String templateId, String id) {
    PrintTemplate template = s.get(PrintTemplate.class, templateId);
    s.em.lock(template, LockModeType.PESSIMISTIC_WRITE);
    PrintTemplateVersion version = version(templateId, id, false);
    if ("DRAFT".equals(version.getStatus())) {
      validateLayout(s.decode(version.getLayoutJson()), version);
      if (version.getPreviewSvgAssetId() == null) throw ApiException.badRequest("发布前需要SVG示意图");
      version.setStatus("PUBLISHED");
      version.setPublishedAt(Instant.now());
    } else if (!"PUBLISHED".equals(version.getStatus())) throw ApiException.conflict("版本不可发布");
    template.setCurrentVersionId(id);
    template.setStatus("PUBLISHED");
    return versionDto(version, true);
  }

  public Object uploadPreview(JsonNode body) {
    oneOf(required(body, "purpose", 32), "TEMPLATE_PREVIEW");
    oneOf(required(body, "mimeType", 128), "image/svg+xml");
    String svg = required(body, "svg", 131072);
    validateSvg(svg);
    MediaAsset asset =
        assets.store(
            null, null, svg.getBytes(StandardCharsets.UTF_8), "TEMPLATE_PREVIEW", "image/svg+xml");
    return assets.dto(asset);
  }

  /** SVG白名单和关闭外部实体，禁止脚本、远程资源和内联事件。 */
  static void validateSvg(String svg) {
    try {
      DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
      f.setNamespaceAware(true);
      f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      f.setFeature("http://xml.org/sax/features/external-general-entities", false);
      f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      f.setXIncludeAware(false);
      f.setExpandEntityReferences(false);
      Document document =
          f.newDocumentBuilder()
              .parse(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)));
      if (!"svg".equals(document.getDocumentElement().getLocalName()))
        throw new IllegalArgumentException();
      Set<String> tags =
          Set.of(
              "svg",
              "g",
              "rect",
              "line",
              "path",
              "circle",
              "ellipse",
              "polyline",
              "polygon",
              "text",
              "tspan",
              "title",
              "desc");
      NodeList elements = document.getElementsByTagName("*");
      for (int i = 0; i < elements.getLength(); i++) {
        Element e = (Element) elements.item(i);
        if (!tags.contains(e.getLocalName())
            || !"http://www.w3.org/2000/svg".equals(e.getNamespaceURI()))
          throw new IllegalArgumentException();
        NamedNodeMap attrs = e.getAttributes();
        for (int j = 0; j < attrs.getLength(); j++) {
          Node attr = attrs.item(j);
          String name = attr.getNodeName().toLowerCase(Locale.ROOT),
              value = attr.getNodeValue().toLowerCase(Locale.ROOT);
          if (name.startsWith("on")
              || name.contains("href")
              || name.equals("style")
              || value.contains("url(")
              || value.contains("javascript:")
              || value.contains("data:")) throw new IllegalArgumentException();
        }
      }
    } catch (Exception e) {
      throw ApiException.badRequest("SVG包含不允许的内容或格式错误");
    }
  }

  public Map<String, Object> categoryDto(PrintTemplateCategory row) {
    return map(
        "id",
        row.getId(),
        "code",
        row.getCode(),
        "name",
        row.getName(),
        "sortOrder",
        row.getSortOrder());
  }

  public Object templateDto(PrintTemplate row) {
    PrintTemplateVersion version =
        row.getCurrentVersionId() == null
            ? null
            : s.get(PrintTemplateVersion.class, row.getCurrentVersionId());
    return map(
        "id",
        row.getId(),
        "categoryId",
        row.getCategoryId(),
        "code",
        row.getCode(),
        "name",
        row.getName(),
        "status",
        row.getStatus(),
        "sortOrder",
        row.getSortOrder(),
        "currentVersionId",
        row.getCurrentVersionId(),
        "previewSvgAssetId",
        version == null ? null : version.getPreviewSvgAssetId(),
        "slotsPerPage",
        version == null ? null : version.getSlotsPerPage());
  }

  public Map<String, Object> versionDto(PrintTemplateVersion v, boolean admin) {
    Map<String, Object> out =
        map(
            "id",
            v.getId(),
            "templateId",
            v.getTemplateId(),
            "versionNo",
            v.getVersionNo(),
            "status",
            v.getStatus(),
            "paperWidthMm",
            v.getPaperWidthMm().toPlainString(),
            "paperHeightMm",
            v.getPaperHeightMm().toPlainString(),
            "orientation",
            v.getOrientation(),
            "slotsPerPage",
            v.getSlotsPerPage(),
            "layout",
            s.decode(v.getLayoutJson()),
            "previewSvgAssetId",
            v.getPreviewSvgAssetId(),
            "rendererType",
            v.getRendererType(),
            "rendererVersion",
            v.getRendererVersion(),
            "codeHash",
            v.getCodeHash(),
            "publishedAt",
            v.getPublishedAt());
    if (admin) out.put("codeContent", v.getCodeContent());
    return out;
  }
}
