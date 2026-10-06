package com.yingying.cuotiku.server.dto;

import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Map;

public class BookDto {

    public record AddEntryRequest(
            @NotNull(message = "年级不能为空")
            @Min(value = 1, message = "年级取值1-9")
            @Max(value = 9, message = "年级取值1-9")
            Integer grade,
            @Min(value = 1, message = "学期取值1(上学期)或2(下学期)")
            @Max(value = 2, message = "学期取值1(上学期)或2(下学期)")
            Integer term,
            @NotBlank(message = "科目不能为空")
            String subject,
            @NotBlank(message = "图片内容不能为空")
            @Size(max = 16_000_000, message = "错题图片过大")
            String imageBase64,
            String errorType,
            @Size(max = 1000, message = "备注最长1000字")
            String remark,
            @Size(max = 1000, message = "答案最长1000字")
            String answer) {}

    public record UpdateEntryRequest(
            @Min(value = 1, message = "年级取值1-9")
            @Max(value = 9, message = "年级取值1-9")
            Integer grade,
            @Min(value = 1, message = "学期取值1(上学期)或2(下学期)")
            @Max(value = 2, message = "学期取值1(上学期)或2(下学期)")
            Integer term,
            String subject,
            String errorType,
            @Size(max = 1000, message = "备注最长1000字")
            String remark,
            @Size(max = 1000, message = "答案最长1000字")
            String answer) {}

    public record ImagesRequest(
            @NotEmpty(message = "错题ID列表不能为空")
            @Size(max = 50, message = "单次最多获取50张图片")
            List<@NotBlank String> ids,
            String kind) {}

    public record ImageItem(String id, String contentType, String imageBase64) {}

    public record AbilityDimensionDto(
            String key,
            String label,
            Integer score,
            int totalCount,
            int practiceCount,
            double weightedCount) {}

    public record AbilityModelDto(
            String subject,
            int sampleSize,
            Double overall,
            List<AbilityDimensionDto> dimensions) {}

    public record AbilityResponseDto(
            AbilityModelDto overall,
            List<AbilityModelDto> subjects) {}

    public record RandomPaperRequest(
            @Min(value = 1, message = "年级取值1-9")
            @Max(value = 9, message = "年级取值1-9")
            Integer grade,
            @Min(value = 1, message = "学期取值1(上学期)或2(下学期)")
            @Max(value = 2, message = "学期取值1(上学期)或2(下学期)")
            Integer term,
            String subject,
            @NotEmpty(message = "请指定各错误类型的抽取数量")
            Map<String, @NotNull Integer> counts) {}

    public record TypeStat(int requested, int selected, int poolSize) {}

    public record RandomPaperResponse(
            List<EntryDto> items,
            int requested,
            int selected,
            Map<String, TypeStat> byType) {}

    public record PracticeRequest(
            @NotEmpty(message = "错题ID列表不能为空")
            @Size(max = 200, message = "单次最多200条")
            List<@NotBlank String> ids) {}

    public record EntryDto(
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
            Long lastPracticedAt) {}

    public record PracticeRecordRequest(
            @Size(max = 2000, message = "答案内容最长2000字")
            String answerContent,
            @NotNull(message = "correct不能为空")
            Boolean correct,
            Long practicedAt) {}

    public record PracticeRecordDto(
            Long id,
            String entryId,
            long practicedAt,
            String practicedAtText,
            String answerContent,
            boolean correct) {}

    public record PracticeHistoryDto(
            List<PracticeRecordDto> items,
            long total,
            int page,
            int size,
            long recordCount,
            long correctCount,
            Double accuracy,
            Long lastPracticedAt) {}
}
