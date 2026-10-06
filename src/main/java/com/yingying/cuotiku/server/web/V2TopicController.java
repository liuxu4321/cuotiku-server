package com.yingying.cuotiku.server.web;

import com.yingying.cuotiku.server.dto.ApiResponse;
import com.yingying.cuotiku.server.dto.TaxonomyDto.TopicDto;
import com.yingying.cuotiku.server.dto.TaxonomyDto.UpdateTopicRequest;
import com.yingying.cuotiku.server.security.AuthenticatedUser;
import com.yingying.cuotiku.server.service.SubjectTopicService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/topics")
public class V2TopicController {

    private final SubjectTopicService service;

    public V2TopicController(SubjectTopicService service) {
        this.service = service;
    }

    @PutMapping("/{id}")
    public ApiResponse<TopicDto> update(@PathVariable Long id,
                                        @Valid @RequestBody UpdateTopicRequest request,
                                        @AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(service.updateTopic(principal.user(), id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id,
                                    @AuthenticationPrincipal AuthenticatedUser principal) {
        service.deleteTopic(principal.user(), id);
        return ApiResponse.ok(null);
    }
}
