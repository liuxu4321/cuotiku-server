package com.yingying.cuotiku.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class AuthDto {

    public record CaptchaResponse(String captchaId, String imageBase64, long expiresInSeconds) {}

    public record LoginRequest(
            @NotBlank(message = "手机号不能为空")
            @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
            String phone,
            @NotBlank(message = "密码不能为空")
            @Size(min = 6, max = 64, message = "密码长度需为6-64位")
            String password,
            @NotBlank(message = "验证码ID不能为空")
            String captchaId,
            @NotBlank(message = "验证码不能为空")
            @Size(min = 4, max = 6, message = "验证码格式不正确")
            String captchaCode,
            String clientLabel) {}

    public record LoginResponse(
            String token, long expiresIn, String refreshToken, long refreshExpiresIn, UserDto user) {}

    public record RefreshRequest(@NotBlank(message = "refreshToken不能为空") String refreshToken) {}

    public record ChangePasswordRequest(
            @NotBlank(message = "原密码不能为空")
            String oldPassword,
            @NotBlank(message = "新密码不能为空")
            @Size(min = 6, max = 64, message = "密码长度需为6-64位")
            String newPassword) {}

    public record RefreshResponse(String token, long expiresIn, String refreshToken, long refreshExpiresIn) {}

    public record UserDto(
            Long id,
            String phone,
            String memberNo,
            String memberExpireAt,
            boolean memberActive,
            String role,
            boolean aiEnabled,
            boolean enabled,
            boolean cancelled,
            String clientLabel,
            String lastLoginAt,
            String createdAt) {}

    public record KeepaliveRequest(
            @NotBlank(message = "clientId不能为空")
            @Size(max = 64, message = "clientId最长64位")
            String clientId,
            @Size(max = 32, message = "版本号过长")
            String appVersion,
            @Size(max = 32, message = "平台标识过长")
            String platform,
            @Size(max = 64, message = "系统版本过长")
            String osVersion,
            @Size(max = 32, message = "状态标识过长")
            String state,
            @Size(max = 256, message = "状态描述过长")
            String detail) {}

    public record MeResponse(
            String phone,
            String memberNo,
            String memberExpireAt,
            boolean memberActive,
            String role,
            boolean aiEnabled,
            String tokenExpiresAt) {}
}
