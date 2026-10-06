package com.yingying.cuotiku.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public class AiDto {

    public record EraseRequest(
            @NotBlank(message = "图片内容不能为空")
            @Size(max = 16_000_000, message = "图片过大")
            String imageBase64) {}

    public record EraseResponse(String imageBase64, String requestId, String traceId) {}

    public record CropEnhanceRequest(
            @NotBlank(message = "图片内容不能为空")
            @Size(max = 14_000_000, message = "图片过大（Base64需≤10M）")
            String imageBase64,
            Boolean crop,
            Boolean deskew,
            Boolean adjustOrientation,
            Boolean onlyPosition,
            Integer enhanceType) {}

    public record CropEnhanceResponse(
            String imageBase64,
            Integer width,
            Integer height,
            List<Integer> position,
            Integer angle,
            String requestId,
            String traceId) {}

    public record SplitQuestionsRequest(
            @NotBlank(message = "图片内容不能为空")
            @Size(max = 14_000_000, message = "图片过大（Base64需≤10M）")
            String imageBase64,
            Boolean useNewModel) {}

    public record SplitQuestionBoxDto(
            int index,
            int x, int y, int width, int height,
            double nx, double ny, double nWidth, double nHeight) {}

    public record SplitQuestionsResponse(
            int width,
            int height,
            List<SplitQuestionBoxDto> questions,
            String requestId,
            String traceId) {}

    public record PaperProcessRequest(
            @NotBlank(message = "图片内容不能为空")
            @Size(max = 14_000_000, message = "图片过大（Base64需≤10M）")
            String imageBase64,
            Boolean enhance,
            Boolean erase,
            Boolean split) {}

    public record PaperProcessStepDto(
            boolean applied,
            boolean success,
            String requestId,
            Integer errorCode,
            String errorMessage) {}

    public record PaperProcessResponse(
            String traceId,
            String imageBase64,
            String imageKind,
            int width,
            int height,
            List<SplitQuestionBoxDto> questions,
            PaperProcessStepDto enhance,
            PaperProcessStepDto erase,
            PaperProcessStepDto split) {}
}
