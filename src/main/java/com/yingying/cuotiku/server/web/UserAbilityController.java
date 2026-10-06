package com.yingying.cuotiku.server.web;

import com.yingying.cuotiku.server.dto.ApiResponse;
import com.yingying.cuotiku.server.dto.BookDto.AbilityResponseDto;
import com.yingying.cuotiku.server.security.AuthenticatedUser;
import com.yingying.cuotiku.server.service.AbilityService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user/ability")
public class UserAbilityController {

    private final AbilityService abilityService;

    public UserAbilityController(AbilityService abilityService) {
        this.abilityService = abilityService;
    }

    @GetMapping
    public ApiResponse<AbilityResponseDto> query(
            @RequestParam(required = false) Integer grade,
            @RequestParam(required = false) Integer term,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        AbilityService.Filters filters = AbilityService.parseFilters(grade, term, subject, start, end);
        return ApiResponse.ok(abilityService.query(principal.user().getId(), filters));
    }
}
