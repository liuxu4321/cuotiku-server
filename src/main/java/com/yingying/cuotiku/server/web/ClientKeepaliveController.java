package com.yingying.cuotiku.server.web;

import com.yingying.cuotiku.server.dto.ApiResponse;
import com.yingying.cuotiku.server.dto.AuthDto.KeepaliveRequest;
import com.yingying.cuotiku.server.security.AuthenticatedUser;
import com.yingying.cuotiku.server.service.KeepaliveService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/client/keepalive")
public class ClientKeepaliveController {

    private final KeepaliveService keepaliveService;

    public ClientKeepaliveController(KeepaliveService keepaliveService) {
        this.keepaliveService = keepaliveService;
    }

    @PostMapping
    public ApiResponse<Void> report(@Valid @RequestBody KeepaliveRequest request,
                                    @AuthenticationPrincipal AuthenticatedUser principal) {
        keepaliveService.report(request, principal == null ? null : principal.user());
        return ApiResponse.ok(null);
    }
}
