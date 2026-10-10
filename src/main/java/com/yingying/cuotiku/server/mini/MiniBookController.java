package com.yingying.cuotiku.server.mini;

import com.yingying.cuotiku.server.dto.ApiResponse;
import com.yingying.cuotiku.server.entity.User;
import com.yingying.cuotiku.server.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Book业务HTTP入口；事务与归属校验均在Service层。 */
@RestController
public class MiniBookController {
  private final MiniBookService service;

  public MiniBookController(MiniBookService service) {
    this.service = service;
  }

  /** ENT-01：分类批量保存题框或照片。 */
  @PostMapping("/api/mini/v1/students/{studentId}/entries/batch")
  public ApiResponse<Object> ent01(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.batch(user, studentId, body.payload()));
  }

  /** ENT-02：筛选错题卡片列表。 */
  @GetMapping("/api/mini/v1/students/{studentId}/entries")
  public ApiResponse<Object> ent02(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.list(user, studentId, query));
  }

  /** ENT-03：统一错题详情。 */
  @GetMapping("/api/mini/v1/students/{studentId}/entries/{entryId}")
  public ApiResponse<Object> ent03(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @PathVariable String entryId,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.get(user, studentId, entryId));
  }

  /** ENT-04：修改错题分类或内容。 */
  @PatchMapping("/api/mini/v1/students/{studentId}/entries/{entryId}")
  public ApiResponse<Object> ent04(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @PathVariable String entryId,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.update(user, studentId, entryId, body.payload()));
  }

  /** ENT-05：软删除错题。 */
  @DeleteMapping("/api/mini/v1/students/{studentId}/entries/{entryId}")
  public ApiResponse<Object> ent05(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @PathVariable String entryId,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.delete(user, studentId, entryId, query));
  }

  /** PRA-01：提交做题结果。 */
  @PostMapping("/api/mini/v1/students/{studentId}/entries/{entryId}/practices")
  public ApiResponse<Object> pra01(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @PathVariable String entryId,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.practice(user, studentId, entryId, body.payload()));
  }

  /** PRA-02：查询单题练习历史。 */
  @GetMapping("/api/mini/v1/students/{studentId}/entries/{entryId}/practices")
  public ApiResponse<Object> pra02(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @PathVariable String entryId,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.practices(user, studentId, entryId, query));
  }

  /** PRA-03：查询学生跨题练习历史。 */
  @GetMapping("/api/mini/v1/students/{studentId}/practices")
  public ApiResponse<Object> pra03(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.practices(user, studentId, null, query));
  }
}
