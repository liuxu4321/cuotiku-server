package com.yingying.cuotiku.server.web;

import com.yingying.cuotiku.server.dto.ApiResponse;
import com.yingying.cuotiku.server.dto.BookV2Dto.AbilityV2Response;
import com.yingying.cuotiku.server.security.AuthenticatedUser;
import com.yingying.cuotiku.server.service.AbilityService;
import com.yingying.cuotiku.server.service.BookV2QueryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/user/ability")
public class V2AbilityController {

    private final BookV2QueryService queryService;

    public V2AbilityController(BookV2QueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public ApiResponse<AbilityV2Response> ability(
            @RequestParam(required = false) Integer grade,
            @RequestParam(required = false) Integer term,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long topicId,
            @RequestParam(required = false) Boolean unclassified,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        AbilityService.Filters filters = AbilityService.parseFilters(grade, term, null, start, end);
        BookV2QueryService.Scope scope = new BookV2QueryService.Scope(
                grade, term, subjectId, topicId, unclassified, null, filters.start(), filters.end());
        return ApiResponse.ok(queryService.ability(principal.user(), scope));
    }
}
