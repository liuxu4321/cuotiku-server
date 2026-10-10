package com.yingying.cuotiku.server.mini;

import com.yingying.cuotiku.server.dto.ApiResponse;
import com.yingying.cuotiku.server.entity.User;
import com.yingying.cuotiku.server.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Account业务HTTP入口；事务与归属校验均在Service层。 */
@RestController
public class MiniAccountController {
  private final MiniAccountService service;

  public MiniAccountController(MiniAccountService service) {
    this.service = service;
  }

  /** AUTH-02：协商小程序接口能力。 */
  @GetMapping("/api/mini/v1/capabilities")
  public ApiResponse<Object> auth02(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.capabilities());
  }

  /** AUTH-03：交换微信身份会话。 */
  @PostMapping("/api/mini/v1/auth/wechat")
  public ApiResponse<Object> auth03(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.exchange(body.payload()));
  }

  /** AUTH-04：微信身份绑定会员账号并登录。 */
  @PostMapping("/api/mini/v1/auth/member-bind")
  public ApiResponse<Object> auth04(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.bind(body.payload()));
  }

  /** AUTH-05：登录已绑定微信账号。 */
  @PostMapping("/api/mini/v1/auth/login")
  public ApiResponse<Object> auth05(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestParam Map<String, String> query,
      @Valid @RequestBody MiniBody body) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.login(body.payload()));
  }

  /** AUTH-07：读取账号、权益及最近学生。 */
  @GetMapping("/api/mini/v1/me")
  public ApiResponse<Object> auth07(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.me(user));
  }

  /** PUBLIC-01：读取使用帮助或关于内容。 */
  @GetMapping("/api/mini/v1/content/{key}")
  public ApiResponse<Object> public01(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String key,
      @RequestParam Map<String, String> query) {
    User user = principal == null ? null : principal.user();
    return ApiResponse.ok(service.content(key));
  }
}
