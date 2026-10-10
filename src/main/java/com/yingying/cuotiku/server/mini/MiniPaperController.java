package com.yingying.cuotiku.server.mini;

import com.yingying.cuotiku.server.dto.ApiResponse;
import com.yingying.cuotiku.server.entity.User;
import com.yingying.cuotiku.server.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Paper业务HTTP入口；事务与归属校验均在Service层。 */
@RestController
public class MiniPaperController {
  private final MiniPaperService service;

  public MiniPaperController(MiniPaperService service) {
    this.service = service;
  }

  /** RND-01：查询各错误类型可抽题数。 */
  @PostMapping("/api/mini/v1/students/{studentId}/random-papers/availability")
  public ApiResponse<Object> rnd01(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.availability(user, studentId, body.payload()));
  }

  /** RND-02：随机组卷并返回有序来源。 */
  @PostMapping("/api/mini/v1/students/{studentId}/random-papers")
  public ApiResponse<Object> rnd02(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.random(user, studentId, body.payload()));
  }

  /** DRF-01：建立可恢复打印草稿（可选）。 */
  @PostMapping("/api/mini/v1/students/{studentId}/paper-drafts")
  public ApiResponse<Object> drf01(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.createDraft(user, studentId, body.payload()));
  }

  /** DRF-02：恢复有序选题草稿。 */
  @GetMapping("/api/mini/v1/students/{studentId}/paper-drafts/{draftId}")
  public ApiResponse<Object> drf02(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @PathVariable String draftId,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.getDraft(user, studentId, draftId));
  }

  /** DRF-03：选择草稿预选模板。 */
  @PatchMapping("/api/mini/v1/students/{studentId}/paper-drafts/{draftId}")
  public ApiResponse<Object> drf03(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @PathVariable String draftId,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.updateDraft(user, studentId, draftId, body.payload()));
  }

  /** DRF-04：调整草稿题序。 */
  @PutMapping("/api/mini/v1/students/{studentId}/paper-drafts/{draftId}/items/order")
  public ApiResponse<Object> drf04(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @PathVariable String draftId,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.order(user, studentId, draftId, body.payload()));
  }

  /** DRF-05：从草稿删除打印项。 */
  @DeleteMapping("/api/mini/v1/students/{studentId}/paper-drafts/{draftId}/items/{itemId}")
  public ApiResponse<Object> drf05(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @PathVariable String draftId,
      @PathVariable String itemId,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.deleteItem(user, studentId, draftId, itemId, query));
  }

  /** DRF-06：放弃草稿。 */
  @DeleteMapping("/api/mini/v1/students/{studentId}/paper-drafts/{draftId}")
  public ApiResponse<Object> drf06(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @PathVariable String draftId,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.deleteDraft(user, studentId, draftId, query));
  }
}
