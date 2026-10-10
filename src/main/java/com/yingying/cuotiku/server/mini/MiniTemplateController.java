package com.yingying.cuotiku.server.mini;

import com.yingying.cuotiku.server.dto.ApiResponse;
import com.yingying.cuotiku.server.entity.User;
import com.yingying.cuotiku.server.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Template业务HTTP入口；事务与归属校验均在Service层。 */
@RestController
public class MiniTemplateController {
  private final MiniTemplateService service;

  public MiniTemplateController(MiniTemplateService service) {
    this.service = service;
  }

  /** TPL-01：分类加载模板示意图。 */
  @GetMapping("/api/mini/v1/template-categories")
  public ApiResponse<Object> tpl01(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.categories(false, query));
  }

  /** TPL-02：横向加载同分类更多模板。 */
  @GetMapping("/api/mini/v1/template-categories/{categoryId}/templates")
  public ApiResponse<Object> tpl02(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String categoryId,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.templates(categoryId, query, false));
  }

  /** TPL-03：读取指定发布模板版本。 */
  @GetMapping("/api/mini/v1/templates/{templateId}/versions/{versionId}")
  public ApiResponse<Object> tpl03(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String templateId,
      @PathVariable String versionId,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.versionDto(templateId, versionId));
  }

  /** TPL-04：获取模板SVG示意图。 */
  @GetMapping("/api/mini/v1/templates/{templateId}/preview")
  public ApiResponse<Object> tpl04(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String templateId,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.preview(templateId, query));
  }

  /** ADM-10：管理模板分类列表。 */
  @GetMapping("/api/admin/mini/v1/template-categories")
  public ApiResponse<Object> adm10(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.categories(true, query));
  }

  /** ADM-11：管理创建模板分类。 */
  @PostMapping("/api/admin/mini/v1/template-categories")
  public ApiResponse<Object> adm11(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.createCategory(body.payload()));
  }

  /** ADM-12：管理修改分类显示／启停。 */
  @PatchMapping("/api/admin/mini/v1/template-categories/{categoryId}")
  public ApiResponse<Object> adm12(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String categoryId,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.updateCategory(categoryId, body.payload()));
  }

  /** ADM-13：管理模板列表。 */
  @GetMapping("/api/admin/mini/v1/templates")
  public ApiResponse<Object> adm13(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.templates(query.get("categoryId"), query, true));
  }

  /** ADM-14：管理创建模板身份。 */
  @PostMapping("/api/admin/mini/v1/templates")
  public ApiResponse<Object> adm14(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.createTemplate(body.payload()));
  }

  /** ADM-15：修改模板摘要／停用。 */
  @PatchMapping("/api/admin/mini/v1/templates/{templateId}")
  public ApiResponse<Object> adm15(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String templateId,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.updateTemplate(templateId, body.payload()));
  }

  /** ADM-16：新建模板草稿版本。 */
  @PostMapping("/api/admin/mini/v1/templates/{templateId}/versions")
  public ApiResponse<Object> adm16(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String templateId,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.createVersion(templateId, body.payload()));
  }

  /** ADM-17：管理读取版本及代码。 */
  @GetMapping("/api/admin/mini/v1/templates/{templateId}/versions")
  public ApiResponse<Object> adm17(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String templateId,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.versions(templateId));
  }

  /** ADM-18：修改未发布完整版本。 */
  @PutMapping("/api/admin/mini/v1/templates/{templateId}/versions/{versionId}")
  public ApiResponse<Object> adm18(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String templateId,
      @PathVariable String versionId,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.updateVersion(templateId, versionId, body.payload()));
  }

  /** ADM-19：上传受控系统SVG预览资产。 */
  @PostMapping("/api/admin/mini/v1/assets")
  public ApiResponse<Object> adm19(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.uploadPreview(body.payload()));
  }

  /** ADM-20：校验并发布不可变模板版本。 */
  @PostMapping("/api/admin/mini/v1/templates/{templateId}/versions/{versionId}/publish")
  public ApiResponse<Object> adm20(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String templateId,
      @PathVariable String versionId,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.publish(templateId, versionId));
  }
}
