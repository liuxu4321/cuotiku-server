package com.yingying.cuotiku.server.web;

import com.yingying.cuotiku.server.dto.AdminUserDto.KeepaliveDto;
import com.yingying.cuotiku.server.dto.AdminUserDto.PageResponse;
import com.yingying.cuotiku.server.dto.ApiResponse;
import com.yingying.cuotiku.server.service.KeepaliveService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/keepalives")
public class AdminKeepaliveController {

    private final KeepaliveService keepaliveService;

    public AdminKeepaliveController(KeepaliveService keepaliveService) {
        this.keepaliveService = keepaliveService;
    }

    @GetMapping
    public ApiResponse<PageResponse<KeepaliveDto>> list(
            @RequestParam(defaultValue = "false") boolean onlineOnly,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(keepaliveService.list(onlineOnly, keyword, page, size));
    }
}
