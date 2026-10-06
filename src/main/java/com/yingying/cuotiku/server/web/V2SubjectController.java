package com.yingying.cuotiku.server.web;

import com.yingying.cuotiku.server.dto.ApiResponse;
import com.yingying.cuotiku.server.dto.TaxonomyDto.*;
import com.yingying.cuotiku.server.security.AuthenticatedUser;
import com.yingying.cuotiku.server.service.SubjectTopicService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v2/subjects")
public class V2SubjectController {

    private final SubjectTopicService service;

    public V2SubjectController(SubjectTopicService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<SubjectDto>> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(service.listSubjects(principal.user()));
    }

    @PostMapping
    public ApiResponse<SubjectDto> create(@Valid @RequestBody NameRequest request,
                                          @AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(service.createSubject(principal.user(), request));
    }

    @PutMapping("/reorder")
    public ApiResponse<List<SubjectDto>> reorder(@Valid @RequestBody ReorderRequest request,
                                                 @AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(service.reorderSubjects(principal.user(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<SubjectDto> update(@PathVariable Long id,
                                          @Valid @RequestBody UpdateSubjectRequest request,
                                          @AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(service.updateSubject(principal.user(), id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id,
                                    @AuthenticationPrincipal AuthenticatedUser principal) {
        service.deleteSubject(principal.user(), id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{subjectId}/topics")
    public ApiResponse<List<TopicDto>> listTopics(@PathVariable Long subjectId,
                                                  @AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(service.listTopics(principal.user(), subjectId));
    }

    @PostMapping("/{subjectId}/topics")
    public ApiResponse<TopicDto> createTopic(@PathVariable Long subjectId,
                                             @Valid @RequestBody NameRequest request,
                                             @AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(service.createTopic(principal.user(), subjectId, request));
    }

    @PutMapping("/{subjectId}/topics/reorder")
    public ApiResponse<List<TopicDto>> reorderTopics(@PathVariable Long subjectId,
                                                     @Valid @RequestBody ReorderRequest request,
                                                     @AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(service.reorderTopics(principal.user(), subjectId, request));
    }
}
