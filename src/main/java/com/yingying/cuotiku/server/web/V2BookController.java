package com.yingying.cuotiku.server.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.yingying.cuotiku.server.dto.ApiResponse;
import com.yingying.cuotiku.server.dto.BookV2Dto.BatchAddRequest;
import com.yingying.cuotiku.server.dto.BookV2Dto.BatchAddResponse;
import com.yingying.cuotiku.server.dto.BookV2Dto.EntryDtoV2;
import com.yingying.cuotiku.server.dto.BookV2Dto.ReclassifyRequest;
import com.yingying.cuotiku.server.dto.BookV2Dto.ReclassifyResponse;
import com.yingying.cuotiku.server.security.AuthenticatedUser;
import com.yingying.cuotiku.server.service.BookV2Service;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/book/entries")
public class V2BookController {

    private static final long MAX_BATCH_BYTES = 20L * 1024 * 1024;

    private final BookV2Service bookV2Service;

    public V2BookController(BookV2Service bookV2Service) {
        this.bookV2Service = bookV2Service;
    }

    @PostMapping("/batch")
    public ApiResponse<BatchAddResponse> batch(@Valid @RequestBody BatchAddRequest request,
                                               HttpServletRequest servletRequest,
                                               @AuthenticationPrincipal AuthenticatedUser principal) {
        int contentLength = servletRequest.getContentLength();
        if (contentLength > MAX_BATCH_BYTES) {
            throw ApiException.badRequest("单次提交数据过大");
        }
        return ApiResponse.ok(bookV2Service.batchAdd(principal.user(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<EntryDtoV2> edit(@PathVariable String id,
                                        @RequestBody JsonNode body,
                                        @AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(bookV2Service.edit(principal.user(), id, body));
    }

    @PostMapping("/reclassify")
    public ApiResponse<ReclassifyResponse> reclassify(@Valid @RequestBody ReclassifyRequest request,
                                                      @AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(bookV2Service.reclassify(principal.user(), request));
    }
}
