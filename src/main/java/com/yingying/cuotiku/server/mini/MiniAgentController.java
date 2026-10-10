package com.yingying.cuotiku.server.mini;

import com.yingying.cuotiku.server.dto.ApiResponse;
import com.yingying.cuotiku.server.entity.User;
import com.yingying.cuotiku.server.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Agent业务HTTP入口；事务与归属校验均在Service层。 */
@RestController
public class MiniAgentController {
  private final MiniAgentService service;

  public MiniAgentController(MiniAgentService service) {
    this.service = service;
  }

  /** AGT-01：请求讲解。 */
  @PostMapping("/api/mini/v1/students/{studentId}/entries/{entryId}/explanation")
  public ApiResponse<Object> agt01(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @PathVariable String entryId,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.run(user, studentId, entryId, "EXPLAIN", body.payload()));
  }

  /** AGT-02：请求举一反三。 */
  @PostMapping("/api/mini/v1/students/{studentId}/entries/{entryId}/analogies")
  public ApiResponse<Object> agt02(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @PathVariable String entryId,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.run(user, studentId, entryId, "ANALOGY", body.payload()));
  }
}
