package com.yingying.cuotiku.server.mini;

import com.yingying.cuotiku.server.dto.ApiResponse;
import com.yingying.cuotiku.server.entity.User;
import com.yingying.cuotiku.server.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Processing业务HTTP入口；事务与归属校验均在Service层。 */
@RestController
public class MiniProcessingController {
  private final MiniProcessingService service;

  public MiniProcessingController(MiniProcessingService service) {
    this.service = service;
  }

  /** JOB-01：提交单张／整批智能处理。 */
  @PostMapping("/api/mini/v1/students/{studentId}/processing-jobs")
  @ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
  public ApiResponse<Object> job01(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.create(user, studentId, body.payload()));
  }

  /** JOB-02：轮询处理状态。 */
  @GetMapping("/api/mini/v1/students/{studentId}/processing-jobs/{jobId}")
  public ApiResponse<Object> job02(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @PathVariable String jobId,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.get(user, studentId, jobId));
  }

  /** JOB-03：重试失败的处理项。 */
  @PostMapping("/api/mini/v1/students/{studentId}/processing-jobs/{jobId}/retry")
  public ApiResponse<Object> job03(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @PathVariable String jobId,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.retryRequest(user, studentId, jobId, body.payload()));
  }

  /** JOB-04：请求取消处理任务。 */
  @PostMapping("/api/mini/v1/students/{studentId}/processing-jobs/{jobId}/cancel")
  public ApiResponse<Object> job04(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @PathVariable String jobId,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.cancel(user, studentId, jobId));
  }
}
