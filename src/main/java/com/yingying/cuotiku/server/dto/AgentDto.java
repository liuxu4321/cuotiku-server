package com.yingying.cuotiku.server.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;

public class AgentDto {

    public record RunRequest(
            String entryId,
            @Size(max = 14_000_000, message = "图片过大（Base64需≤10M）")
            String imageBase64,
            String subject,
            @Min(value = 1, message = "年级取值1-9")
            @Max(value = 9, message = "年级取值1-9")
            Integer grade,
            @Min(value = 1, message = "学期取值1或2")
            @Max(value = 2, message = "学期取值1或2")
            Integer term,
            String errorType,
            @Min(value = 1, message = "变式题数量1-10")
            @Max(value = 10, message = "变式题数量1-10")
            Integer count) {}

    public record AnalogyItem(String stem, List<String> options, String answer, String analysis, Integer difficulty) {}

    public record AnalogyResponse(String traceId, String model, List<AnalogyItem> items, boolean cached) {}

    public record ExplainStep(String title, String content) {}

    public record ExplainResponse(
            String traceId, String model, String analysis,
            List<ExplainStep> steps, List<String> knowledgePoints,
            List<String> commonMistakes, String summary, boolean cached) {}
}
