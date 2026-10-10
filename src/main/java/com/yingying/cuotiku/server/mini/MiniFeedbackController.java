package com.yingying.cuotiku.server.mini;

import com.yingying.cuotiku.server.dto.ApiResponse;
import com.yingying.cuotiku.server.entity.User;
import com.yingying.cuotiku.server.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Feedback业务HTTP入口；事务与归属校验均在Service层。 */
@RestController
public class MiniFeedbackController {
  private final MiniFeedbackService service;

  public MiniFeedbackController(MiniFeedbackService service) {
    this.service = service;
  }

  /** FDB-01：提交意见反馈。 */
  @PostMapping("/api/mini/v1/feedbacks")
  public ApiResponse<Object> fdb01(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.create(user, body.payload()));
  }

  /** FDB-02：查询本人反馈及处理状态。 */
  @GetMapping("/api/mini/v1/feedbacks")
  public ApiResponse<Object> fdb02(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.list(user, query));
  }
}
