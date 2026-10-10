package com.yingying.cuotiku.server.mini;

import com.yingying.cuotiku.server.dto.ApiResponse;
import com.yingying.cuotiku.server.entity.User;
import com.yingying.cuotiku.server.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Student业务HTTP入口；事务与归属校验均在Service层。 */
@RestController
public class MiniStudentController {
  private final MiniStudentService service;

  public MiniStudentController(MiniStudentService service) {
    this.service = service;
  }

  /** STU-01：学生卡片列表。 */
  @GetMapping("/api/mini/v1/students")
  public ApiResponse<Object> stu01(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.list(user, query));
  }

  /** STU-02：增加学生。 */
  @PostMapping("/api/mini/v1/students")
  public ApiResponse<Object> stu02(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.create(user, body.payload()));
  }

  /** STU-03：查看学生资料。 */
  @GetMapping("/api/mini/v1/students/{studentId}")
  public ApiResponse<Object> stu03(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.get(user, studentId));
  }

  /** STU-04：编辑学生资料或归档。 */
  @PatchMapping("/api/mini/v1/students/{studentId}")
  public ApiResponse<Object> stu04(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.update(user, studentId, body.payload()));
  }

  /** STU-05：记录最近使用学生。 */
  @PutMapping("/api/mini/v1/me/current-student")
  public ApiResponse<Object> stu05(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.current(user, body.payload()));
  }

  /** STU-06：读取首页学生错题摘要。 */
  @GetMapping("/api/mini/v1/students/{studentId}/summary")
  public ApiResponse<Object> stu06(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String studentId,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.summary(user, studentId, query));
  }
}
