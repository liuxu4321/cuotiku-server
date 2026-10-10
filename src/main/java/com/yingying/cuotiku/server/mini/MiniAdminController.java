package com.yingying.cuotiku.server.mini;

import com.yingying.cuotiku.server.dto.ApiResponse;
import com.yingying.cuotiku.server.entity.User;
import com.yingying.cuotiku.server.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Admin业务HTTP入口；事务与归属校验均在Service层。 */
@RestController
public class MiniAdminController {
  private final MiniAdminService service;

  public MiniAdminController(MiniAdminService service) {
    this.service = service;
  }

  /** ADM-01：管理查询普通／会员账号。 */
  @GetMapping("/api/admin/mini/v1/accounts")
  public ApiResponse<Object> adm01(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.accounts(query));
  }

  /** ADM-02：管理查看账号详情。 */
  @GetMapping("/api/admin/mini/v1/accounts/{userId}")
  public ApiResponse<Object> adm02(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String userId,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.accountDetail(userId));
  }

  /** ADM-03：管理创建账号。 */
  @PostMapping("/api/admin/mini/v1/accounts")
  public ApiResponse<Object> adm03(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.createAccount(body.payload()));
  }

  /** ADM-04：更新资料、权益和AI授权。 */
  @PatchMapping("/api/admin/mini/v1/accounts/{userId}")
  public ApiResponse<Object> adm04(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String userId,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.updateAccount(user, userId, body.payload()));
  }

  /** ADM-05：管理重置密码并撤销会话。 */
  @PutMapping("/api/admin/mini/v1/accounts/{userId}/password")
  public ApiResponse<Object> adm05(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String userId,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.password(user, userId, body.payload()));
  }

  /** ADM-06：管理撤销会员权益。 */
  @DeleteMapping("/api/admin/mini/v1/accounts/{userId}/membership")
  public ApiResponse<Object> adm06(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String userId,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.removeMembership(user, userId));
  }

  /** ADM-07：只读微信绑定排查。 */
  @GetMapping("/api/admin/mini/v1/accounts/{userId}/identities")
  public ApiResponse<Object> adm07(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String userId,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.identities(userId));
  }

  /** ADM-08：只读账号学生诊断。 */
  @GetMapping("/api/admin/mini/v1/accounts/{userId}/students")
  public ApiResponse<Object> adm08(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String userId,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.students(userId, query));
  }

  /** ADM-09：查询归属与迁移诊断。 */
  @GetMapping("/api/admin/mini/v1/integrity-checks")
  public ApiResponse<Object> adm09(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.integrity(query));
  }

  /** ADM-21：查询模型和提示词配置。 */
  @GetMapping("/api/admin/mini/v1/agents")
  public ApiResponse<Object> adm21(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.agents());
  }

  /** ADM-22：维护模型提示词并使缓存失效。 */
  @PutMapping("/api/admin/mini/v1/agents/{agentKey}")
  public ApiResponse<Object> adm22(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String agentKey,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.updateAgent(agentKey, body.payload()));
  }

  /** ADM-23：逐次调用日志查询。 */
  @GetMapping("/api/admin/mini/v1/ai-calls")
  public ApiResponse<Object> adm23(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.aiCalls(query));
  }

  /** ADM-24：AI流量及成功失败统计。 */
  @GetMapping("/api/admin/mini/v1/ai-statistics")
  public ApiResponse<Object> adm24(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.statistics(query));
  }

  /** ADM-25：后台查询处理任务。 */
  @GetMapping("/api/admin/mini/v1/processing-jobs")
  public ApiResponse<Object> adm25(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.jobs(query));
  }

  /** ADM-26：后台查看任务固定输入／快照。 */
  @GetMapping("/api/admin/mini/v1/processing-jobs/{taskId}")
  public ApiResponse<Object> adm26(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String taskId,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.job(taskId));
  }

  /** ADM-27：后台有原因重试失败任务。 */
  @PostMapping("/api/admin/mini/v1/processing-jobs/{taskId}/retry")
  public ApiResponse<Object> adm27(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String taskId,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.retryJob(user, taskId, body.payload()));
  }

  /** ADM-28：后台查询打印任务。 */
  @GetMapping("/api/admin/mini/v1/print-tasks")
  public ApiResponse<Object> adm28(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.prints(query));
  }

  /** ADM-29：后台查看任务固定输入／快照。 */
  @GetMapping("/api/admin/mini/v1/print-tasks/{taskId}")
  public ApiResponse<Object> adm29(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String taskId,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.print(taskId));
  }

  /** ADM-30：后台有原因重试失败任务。 */
  @PostMapping("/api/admin/mini/v1/print-tasks/{taskId}/retry")
  public ApiResponse<Object> adm30(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String taskId,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.retryPrint(user, taskId, body.payload()));
  }

  /** ADM-31：管理查询资产与清理候选。 */
  @GetMapping("/api/admin/mini/v1/assets")
  public ApiResponse<Object> adm31(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.assets(query));
  }

  /** ADM-32：请求无引用资产清理。 */
  @PostMapping("/api/admin/mini/v1/assets/{assetId}/cleanup")
  public ApiResponse<Object> adm32(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String assetId,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.cleanup(user, assetId, body.payload()));
  }

  /** ADM-33：管理查询反馈。 */
  @GetMapping("/api/admin/mini/v1/feedbacks")
  public ApiResponse<Object> adm33(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.feedbacks(query));
  }

  /** ADM-34：处理反馈并保存内部备注。 */
  @PatchMapping("/api/admin/mini/v1/feedbacks/{feedbackId}")
  public ApiResponse<Object> adm34(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String feedbackId,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.updateFeedback(feedbackId, body.payload()));
  }

  /** ADM-35：查询队列投递和失败重试。 */
  @GetMapping("/api/admin/mini/v1/outbox-events")
  public ApiResponse<Object> adm35(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.events(query));
  }

  /** ADM-36：恢复任务消息投递。 */
  @PostMapping("/api/admin/mini/v1/outbox-events/{eventId}/retry")
  public ApiResponse<Object> adm36(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String eventId,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.retryEvent(eventId, body.payload()));
  }
}
