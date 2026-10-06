package com.yingying.cuotiku.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class AdminUserDto {

    public record CreateUserRequest(
            @NotBlank(message = "手机号不能为空")
            @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
            String phone,
            @Size(max = 64, message = "会员号最长64位")
            String memberNo,
            String memberExpireAt,
            @NotBlank(message = "初始密码不能为空")
            @Size(min = 6, max = 64, message = "密码长度需为6-64位")
            String password,
            Boolean aiEnabled,
            Boolean enabled,
            String role) {}

    public record UpdateUserRequest(
            @Size(max = 64, message = "会员号最长64位")
            String memberNo,
            String memberExpireAt,
            @Size(min = 6, max = 64, message = "密码长度需为6-64位")
            String password,
            Boolean aiEnabled,
            Boolean enabled,
            String role) {}

    public record ResetPasswordRequest(
            @NotBlank(message = "新密码不能为空")
            @Size(min = 6, max = 64, message = "密码长度需为6-64位")
            String password) {}

    public record PageResponse<T>(java.util.List<T> items, long total, int page, int size) {}

    public record AiCallLogDto(
            Long id,
            String phone,
            String aiType,
            boolean success,
            Integer errorCode,
            String errorMessage,
            String traceId,
            String requestId,
            long inputBytes,
            long outputBytes,
            long durationMs,
            Integer inputTokens,
            Integer outputTokens,
            String createdAt) {}

    public record AgentConfigDto(
            String key,
            String name,
            String systemPrompt,
            String userPromptTemplate,
            String model,
            Double temperature,
            Integer maxTokens,
            boolean enabled,
            String updatedAt,
            java.util.List<String> variables) {}

    public record UpdateAgentRequest(
            @jakarta.validation.constraints.NotBlank(message = "系统提示词不能为空")
            String systemPrompt,
            @jakarta.validation.constraints.NotBlank(message = "用户提示词模板不能为空")
            String userPromptTemplate,
            @jakarta.validation.constraints.NotBlank(message = "模型不能为空")
            @jakarta.validation.constraints.Size(max = 64, message = "模型名过长")
            String model,
            @jakarta.validation.constraints.DecimalMin(value = "0.0", message = "温度取值0-2")
            @jakarta.validation.constraints.DecimalMax(value = "2.0", message = "温度取值0-2")
            Double temperature,
            @jakarta.validation.constraints.Min(value = 1, message = "maxTokens取值1-32000")
            @jakarta.validation.constraints.Max(value = 32000, message = "maxTokens取值1-32000")
            Integer maxTokens,
            Boolean enabled) {}

    public record AiStatsSummary(long total, long success, long failed) {}

    public record KeepaliveDto(
            String clientId,
            String phone,
            boolean loggedIn,
            String appVersion,
            String platform,
            String osVersion,
            String state,
            String detail,
            long reportCount,
            String firstSeenAt,
            String lastSeenAt,
            boolean online) {}

    public record AiStatsResponse(
            java.util.List<AiCallLogDto> items,
            long total,
            int page,
            int size,
            AiStatsSummary summary) {}
}
