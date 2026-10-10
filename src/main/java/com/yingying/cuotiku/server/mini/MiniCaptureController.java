package com.yingying.cuotiku.server.mini;

import com.yingying.cuotiku.server.dto.ApiResponse;
import com.yingying.cuotiku.server.entity.User;
import com.yingying.cuotiku.server.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Capture业务HTTP入口；事务与归属校验均在Service层。 */
@RestController
public class MiniCaptureController {
  private final MiniCaptureService service;

  public MiniCaptureController(MiniCaptureService service) {
    this.service = service;
  }

  /** CAP-01：建立单张／多页采集批次。 */
  @PostMapping("/api/mini/v1/students/{studentId}/capture-batches")
  public ApiResponse<Object> cap01(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.create(user, studentId, body.payload()));
  }

  /** CAP-02：最近拍摄批次列表。 */
  @GetMapping("/api/mini/v1/students/{studentId}/capture-batches")
  public ApiResponse<Object> cap02(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.list(user, studentId, query));
  }

  /** CAP-03：读取照片处理工作区。 */
  @GetMapping("/api/mini/v1/students/{studentId}/capture-batches/{batchId}")
  public ApiResponse<Object> cap03(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @PathVariable String batchId,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.get(user, studentId, batchId));
  }

  /** CAP-04：把上传成功照片加入批次。 */
  @PostMapping("/api/mini/v1/students/{studentId}/capture-batches/{batchId}/photos")
  public ApiResponse<Object> cap04(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @PathVariable String batchId,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.add(user, studentId, batchId, body.payload()));
  }

  /** CAP-05：结束拍摄进入处理。 */
  @PostMapping("/api/mini/v1/students/{studentId}/capture-batches/{batchId}/finish")
  public ApiResponse<Object> cap05(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @PathVariable String batchId,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.finish(user, studentId, batchId));
  }

  /** CAP-06：调整照片显示顺序。 */
  @PutMapping("/api/mini/v1/students/{studentId}/capture-batches/{batchId}/photos/order")
  public ApiResponse<Object> cap06(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @PathVariable String batchId,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.order(user, studentId, batchId, body.payload()));
  }

  /** CAP-07：删除工作区照片。 */
  @DeleteMapping("/api/mini/v1/students/{studentId}/photos/{photoId}")
  public ApiResponse<Object> cap07(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @PathVariable String photoId,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.deletePhoto(user, studentId, photoId));
  }

  /** CAP-08：放弃或移除采集工作区。 */
  @DeleteMapping("/api/mini/v1/students/{studentId}/capture-batches/{batchId}")
  public ApiResponse<Object> cap08(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @PathVariable String batchId,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.deleteBatch(user, studentId, batchId));
  }

  /** IMG-01：读取原图与处理版本。 */
  @GetMapping("/api/mini/v1/students/{studentId}/photos/{photoId}/revisions")
  public ApiResponse<Object> img01(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @PathVariable String photoId,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.revisions(user, studentId, photoId));
  }

  /** IMG-02：选择有效处理版本。 */
  @PutMapping("/api/mini/v1/students/{studentId}/photos/{photoId}/current-revision")
  public ApiResponse<Object> img02(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @PathVariable String photoId,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.selectRevision(user, studentId, photoId, body.payload()));
  }

  /** REG-01：读取指定图片版本题框。 */
  @GetMapping("/api/mini/v1/students/{studentId}/photos/{photoId}/regions")
  public ApiResponse<Object> reg01(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @PathVariable String photoId,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.regions(user, studentId, photoId, query));
  }

  /** REG-02：保存手动题框与修改。 */
  @PutMapping("/api/mini/v1/students/{studentId}/photos/{photoId}/regions")
  public ApiResponse<Object> reg02(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @PathVariable String photoId,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.replaceRegions(user, studentId, photoId, body.payload()));
  }
}
