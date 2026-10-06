package com.yingying.cuotiku.server.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public class TaxonomyDto {

    public record SubjectDto(
            Long id,
            String name,
            String systemKey,
            int sortOrder,
            String status,
            int revision,
            long topicCount,
            long entryCount,
            String updatedAt) {}

    public record TopicDto(
            Long id,
            Long subjectId,
            String name,
            int sortOrder,
            String status,
            int revision,
            long entryCount,
            String updatedAt) {}

    public record NameRequest(
            @NotBlank(message = "名称不能为空")
            @Size(max = 60, message = "名称过长")
            String name) {}

    public record UpdateSubjectRequest(
            @Size(max = 60, message = "名称过长")
            String name,
            String status,
            Integer sortOrder,
            @NotNull(message = "revision不能为空")
            Integer revision) {}

    public record UpdateTopicRequest(
            @Size(max = 60, message = "名称过长")
            String name,
            String status,
            Integer sortOrder,
            @NotNull(message = "revision不能为空")
            Integer revision) {}

    public record ReorderItem(
            @NotNull(message = "id不能为空")
            Long id,
            @NotNull(message = "revision不能为空")
            Integer revision) {}

    public record PreferenceDto(Long defaultSubjectId, Long defaultTopicId) {}

    public record PreferenceRequest(Long defaultSubjectId, Long defaultTopicId) {}

    public record ReorderRequest(
            @NotEmpty(message = "排序列表不能为空")
            @Size(max = 200, message = "排序列表过大")
            @Valid
            List<ReorderItem> items) {}
}
