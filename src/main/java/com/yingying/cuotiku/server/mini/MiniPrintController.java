package com.yingying.cuotiku.server.mini;

import com.yingying.cuotiku.server.dto.ApiResponse;
import com.yingying.cuotiku.server.entity.User;
import com.yingying.cuotiku.server.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Print业务HTTP入口；事务与归属校验均在Service层。 */
@RestController
public class MiniPrintController {
  private final MiniPrintService service;

  public MiniPrintController(MiniPrintService service) {
    this.service = service;
  }

  /** PRT-01：选模板后创建正式打印任务。 */
  @PostMapping("/api/mini/v1/students/{studentId}/print-tasks")
  @ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
  public ApiResponse<Object> prt01(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.create(user, studentId, body.payload()));
  }

  /** PRT-02：打印菜单任务列表。 */
  @GetMapping("/api/mini/v1/students/{studentId}/print-tasks")
  public ApiResponse<Object> prt02(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.list(user, studentId, query));
  }

  /** PRT-03：轮询任务或读取快照详情。 */
  @GetMapping("/api/mini/v1/students/{studentId}/print-tasks/{taskId}")
  public ApiResponse<Object> prt03(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @PathVariable String taskId,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.get(user, studentId, taskId));
  }

  /** PRT-04：原快照重试生成PDF。 */
  @PostMapping("/api/mini/v1/students/{studentId}/print-tasks/{taskId}/retry")
  public ApiResponse<Object> prt04(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @PathVariable String taskId,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.retryRequest(user, studentId, taskId, body.payload()));
  }

  /** PRT-05：取消可取消的打印任务。 */
  @PostMapping("/api/mini/v1/students/{studentId}/print-tasks/{taskId}/cancel")
  public ApiResponse<Object> prt05(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @PathVariable String taskId,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.cancel(user, studentId, taskId));
  }

  /** PRT-06：获得生成PDF下载地址。 */
  @PostMapping("/api/mini/v1/students/{studentId}/print-tasks/{taskId}/download")
  public ApiResponse<Object> prt06(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @PathVariable String taskId,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.download(user, studentId, taskId));
  }

  /** PRT-07：用户明确确认纸张已打印。 */
  @PostMapping("/api/mini/v1/students/{studentId}/print-tasks/{taskId}/confirm")
  public ApiResponse<Object> prt07(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @PathVariable String taskId,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.confirmRequest(user, studentId, taskId, body.payload()));
  }

  /** PRT-08：从用户列表隐藏历史任务。 */
  @DeleteMapping("/api/mini/v1/students/{studentId}/print-tasks/{taskId}")
  public ApiResponse<Object> prt08(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @PathVariable String taskId,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.hide(user, studentId, taskId));
  }

  /** PRT-09：查看本人打印任务状态历史。 */
  @GetMapping("/api/mini/v1/students/{studentId}/print-tasks/{taskId}/events")
  public ApiResponse<Object> prt09(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @PathVariable String taskId,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.events(user, studentId, taskId, query));
  }
}
