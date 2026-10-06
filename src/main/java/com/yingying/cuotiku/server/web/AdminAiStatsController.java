package com.yingying.cuotiku.server.web;

import com.yingying.cuotiku.server.dto.AdminUserDto.AiStatsResponse;
import com.yingying.cuotiku.server.dto.ApiResponse;
import com.yingying.cuotiku.server.service.AiStatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/ai-stats")
public class AdminAiStatsController {

    private final AiStatsService aiStatsService;

    public AdminAiStatsController(AiStatsService aiStatsService) {
        this.aiStatsService = aiStatsService;
    }

    @GetMapping
    public ApiResponse<AiStatsResponse> query(
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String aiType,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(aiStatsService.query(phone, aiType, start, end, page, size));
    }
}
