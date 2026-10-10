package com.yingying.cuotiku.server.mini;

import com.yingying.cuotiku.server.dto.ApiResponse;
import com.yingying.cuotiku.server.entity.User;
import com.yingying.cuotiku.server.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Taxonomy业务HTTP入口；事务与归属校验均在Service层。 */
@RestController
public class MiniTaxonomyController {
  private final MiniTaxonomyService service;

  public MiniTaxonomyController(MiniTaxonomyService service) {
    this.service = service;
  }

  /** SUB-01：查询科目列表。 */
  @GetMapping("/api/mini/v1/students/{studentId}/subjects")
  public ApiResponse<Object> sub01(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.list(user, studentId, "subject", null, query));
  }

  /** SUB-02：增加科目。 */
  @PostMapping("/api/mini/v1/students/{studentId}/subjects")
  public ApiResponse<Object> sub02(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.create(user, studentId, "subject", null, body.payload()));
  }

  /** SUB-03：更新／停用科目。 */
  @PatchMapping("/api/mini/v1/students/{studentId}/subjects/{id}")
  public ApiResponse<Object> sub03(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @PathVariable String id,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.update(user, studentId, "subject", id, body.payload()));
  }

  /** SUB-04：调整科目顺序。 */
  @PutMapping("/api/mini/v1/students/{studentId}/subjects/order")
  public ApiResponse<Object> sub04(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.order(user, studentId, "subject", null, body.payload()));
  }

  /** SUB-05：删除无引用科目。 */
  @DeleteMapping("/api/mini/v1/students/{studentId}/subjects/{id}")
  public ApiResponse<Object> sub05(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @PathVariable String id,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.delete(user, studentId, "subject", id));
  }

  /** TOP-01：读取科目主题。 */
  @GetMapping("/api/mini/v1/students/{studentId}/subjects/{subjectId}/topics")
  public ApiResponse<Object> top01(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @PathVariable String subjectId,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.list(user, studentId, "topic", subjectId, query));
  }

  /** TOP-02：增加科目主题。 */
  @PostMapping("/api/mini/v1/students/{studentId}/subjects/{subjectId}/topics")
  public ApiResponse<Object> top02(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @PathVariable String subjectId,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.create(user, studentId, "topic", subjectId, body.payload()));
  }

  /** TOP-03：编辑／停用主题。 */
  @PatchMapping("/api/mini/v1/students/{studentId}/topics/{id}")
  public ApiResponse<Object> top03(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @PathVariable String id,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.update(user, studentId, "topic", id, body.payload()));
  }

  /** TOP-04：调整主题顺序。 */
  @PutMapping("/api/mini/v1/students/{studentId}/subjects/{subjectId}/topics/order")
  public ApiResponse<Object> top04(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @PathVariable String subjectId,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.order(user, studentId, "topic", subjectId, body.payload()));
  }

  /** TOP-05：删除无引用主题。 */
  @DeleteMapping("/api/mini/v1/students/{studentId}/topics/{id}")
  public ApiResponse<Object> top05(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @PathVariable String id,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.delete(user, studentId, "topic", id));
  }

  /** ERR-01：查询错误类型列表。 */
  @GetMapping("/api/mini/v1/students/{studentId}/error-types")
  public ApiResponse<Object> err01(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.list(user, studentId, "error", null, query));
  }

  /** ERR-02：增加错误类型。 */
  @PostMapping("/api/mini/v1/students/{studentId}/error-types")
  public ApiResponse<Object> err02(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.create(user, studentId, "error", null, body.payload()));
  }

  /** ERR-03：更新／停用错误类型。 */
  @PatchMapping("/api/mini/v1/students/{studentId}/error-types/{id}")
  public ApiResponse<Object> err03(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @PathVariable String id,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.update(user, studentId, "error", id, body.payload()));
  }

  /** ERR-04：调整错误类型顺序。 */
  @PutMapping("/api/mini/v1/students/{studentId}/error-types/order")
  public ApiResponse<Object> err04(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.order(user, studentId, "error", null, body.payload()));
  }

  /** ERR-05：删除无引用错误类型。 */
  @DeleteMapping("/api/mini/v1/students/{studentId}/error-types/{id}")
  public ApiResponse<Object> err05(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @PathVariable String id,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.delete(user, studentId, "error", id));
  }

  /** PREF-01：查询学生分类与模板偏好。 */
  @GetMapping("/api/mini/v1/students/{studentId}/preferences")
  public ApiResponse<Object> pref01(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.preferences(user, studentId));
  }

  /** PREF-02：保存学生偏好。 */
  @PutMapping("/api/mini/v1/students/{studentId}/preferences")
  public ApiResponse<Object> pref02(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.preferences(user, studentId, body.payload()));
  }
}
