package com.yingying.cuotiku.server.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

public class BookV2Dto {

    public record BatchAddItem(
            @NotBlank(message = "clientId不能为空")
            @Size(max = 36, message = "clientId过长")
            String clientId,
            @NotNull(message = "年级不能为空")
            @Min(value = 1, message = "年级取值1-12")
            @Max(value = 12, message = "年级取值1-12")
            Integer grade,
            @Min(value = 1, message = "学期取值1或2")
            @Max(value = 2, message = "学期取值1或2")
            Integer term,
            @NotNull(message = "科目不能为空")
            Long subjectId,
            Long topicId,
            @NotBlank(message = "图片内容不能为空")
            @Size(max = 16_000_000, message = "错题图片过大")
            String imageBase64,
            String errorType,
            @Size(max = 1000, message = "备注最长1000字")
            String remark,
            @Size(max = 1000, message = "答案最长1000字")
            String answer) {}

    public record BatchAddRequest(
            @NotBlank(message = "requestId不能为空")
            @Size(max = 36, message = "requestId过长")
            String requestId,
            @NotEmpty(message = "题目列表不能为空")
            @Size(max = 20, message = "单次最多提交20题，请分块提交")
            @Valid
            List<BatchAddItem> items) {}

    public record ItemResultDto(String clientId, String status, String entryId, Integer code, String message) {}

    public record BatchAddResponse(List<ItemResultDto> results) {}

    public record ReclassifyItem(
            @NotBlank(message = "entryId不能为空")
            String entryId,
            @NotNull(message = "subjectId不能为空")
            Long subjectId,
            Long topicId) {}

    public record ReclassifyRequest(
            @NotBlank(message = "requestId不能为空")
            @Size(max = 36, message = "requestId过长")
            String requestId,
            @NotEmpty(message = "题目列表不能为空")
            @Size(max = 20, message = "单次最多提交20题，请分块提交")
            @Valid
            List<ReclassifyItem> items) {}

    public record ReclassifyResultDto(String entryId, String status, Integer code, String message) {}

    public record ReclassifyResponse(List<ReclassifyResultDto> results) {}

    public record EntryDtoV2(
            String id,
            int grade,
            Integer term,
            String subject,
            String errorType,
            long createdAt,
            String createdAtText,
            int width,
            int height,
            int practiceCount,
            String remark,
            String answer,
            String imageUrl,
            String thumbUrl,
            long recordCount,
            long correctCount,
            Double accuracy,
            Long lastPracticedAt,
            Long subjectId,
            String subjectName,
            Long topicId,
            String topicName,
            String subjectStatus,
            String topicStatus) {}
}
