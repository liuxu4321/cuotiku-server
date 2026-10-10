package com.yingying.cuotiku.server.mini;

import com.yingying.cuotiku.server.dto.ApiResponse;
import com.yingying.cuotiku.server.entity.User;
import com.yingying.cuotiku.server.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Asset业务HTTP入口；事务与归属校验均在Service层。 */
@RestController
public class MiniAssetController {
  private final MiniAssetService service;

  public MiniAssetController(MiniAssetService service) {
    this.service = service;
  }

  /** AST-01：创建COS上传会话。 */
  @PostMapping("/api/mini/v1/students/{studentId}/uploads")
  public ApiResponse<Object> ast01(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.upload(user, studentId, body.payload()));
  }

  /** AST-02：确认上传并校验对象。 */
  @PostMapping("/api/mini/v1/students/{studentId}/uploads/{uploadId}/complete")
  public ApiResponse<Object> ast02(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @PathVariable String uploadId,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.complete(user, studentId, uploadId, body.payload()));
  }

  /** AST-03：读取资产元数据。 */
  @GetMapping("/api/mini/v1/students/{studentId}/assets/{assetId}")
  public ApiResponse<Object> ast03(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @PathVariable String assetId,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.get(user, studentId, assetId));
  }

  /** AST-04：申请私有图片或PDF访问地址。 */
  @PostMapping("/api/mini/v1/students/{studentId}/assets/{assetId}/access")
  public ApiResponse<Object> ast04(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @PathVariable String assetId,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.access(user, studentId, assetId, body.payload()));
  }
}
