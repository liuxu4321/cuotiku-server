package com.yingying.cuotiku.server.web;

import com.yingying.cuotiku.server.dto.AiDto.CropEnhanceRequest;
import com.yingying.cuotiku.server.dto.AiDto.CropEnhanceResponse;
import com.yingying.cuotiku.server.dto.AiDto.PaperProcessRequest;
import com.yingying.cuotiku.server.dto.AiDto.PaperProcessResponse;
import com.yingying.cuotiku.server.dto.AiDto.SplitQuestionsRequest;
import com.yingying.cuotiku.server.dto.AiDto.SplitQuestionsResponse;
import com.yingying.cuotiku.server.dto.AiDto.EraseRequest;
import com.yingying.cuotiku.server.dto.AiDto.EraseResponse;
import com.yingying.cuotiku.server.dto.ApiResponse;
import com.yingying.cuotiku.server.security.AuthenticatedUser;
import com.yingying.cuotiku.server.service.AiEraseService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiEraseService aiEraseService;

    public AiController(AiEraseService aiEraseService) {
        this.aiEraseService = aiEraseService;
    }

    @PostMapping("/erase")
    public ApiResponse<EraseResponse> erase(@Valid @RequestBody EraseRequest request,
                                            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(aiEraseService.erase(request, principal.user()));
    }

    @PostMapping("/crop-enhance")
    public ApiResponse<CropEnhanceResponse> cropEnhance(@Valid @RequestBody CropEnhanceRequest request,
                                                        @AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(aiEraseService.cropEnhance(request, principal.user()));
    }

    @PostMapping("/paper-process")
    public ApiResponse<PaperProcessResponse> paperProcess(@Valid @RequestBody PaperProcessRequest request,
                                                          @AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(aiEraseService.paperProcess(request, principal.user()));
    }

    @PostMapping("/split-questions")
    public ApiResponse<SplitQuestionsResponse> splitQuestions(@Valid @RequestBody SplitQuestionsRequest request,
                                                              @AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(aiEraseService.splitQuestions(request, principal.user()));
    }
}
