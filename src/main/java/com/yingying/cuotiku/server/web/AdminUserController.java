package com.yingying.cuotiku.server.web;

import com.yingying.cuotiku.server.dto.AdminUserDto.*;
import com.yingying.cuotiku.server.dto.ApiResponse;
import com.yingying.cuotiku.server.dto.AuthDto.UserDto;
import com.yingying.cuotiku.server.security.AuthenticatedUser;
import com.yingying.cuotiku.server.service.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public ApiResponse<PageResponse<UserDto>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(adminUserService.list(keyword, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<UserDto> get(@PathVariable Long id) {
        return ApiResponse.ok(adminUserService.get(id));
    }

    @PostMapping
    public ApiResponse<UserDto> create(@Valid @RequestBody CreateUserRequest request,
                                       @AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(adminUserService.create(request, principal.user()));
    }

    @PutMapping("/{id}")
    public ApiResponse<UserDto> update(@PathVariable Long id,
                                       @Valid @RequestBody UpdateUserRequest request,
                                       @AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(adminUserService.update(id, request, principal.user()));
    }

    @PutMapping("/{id}/password")
    public ApiResponse<Void> resetPassword(@PathVariable Long id,
                                           @Valid @RequestBody ResetPasswordRequest request,
                                           @AuthenticationPrincipal AuthenticatedUser principal) {
        adminUserService.resetPassword(id, request, principal.user());
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}/membership")
    public ApiResponse<UserDto> cancelMembership(@PathVariable Long id,
                                                 @AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(adminUserService.cancelMembership(id, principal.user()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id,
                                    @AuthenticationPrincipal AuthenticatedUser principal) {
        adminUserService.delete(id, principal.user());
        return ApiResponse.ok(null);
    }
}
