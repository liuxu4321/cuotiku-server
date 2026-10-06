package com.yingying.cuotiku.server.web;

import com.yingying.cuotiku.server.dto.ApiResponse;
import com.yingying.cuotiku.server.security.AuthenticatedUser;
import com.yingying.cuotiku.server.service.TaxonomyService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v2/meta")
public class V2MetaController {

    private final TaxonomyService taxonomyService;

    public V2MetaController(TaxonomyService taxonomyService) {
        this.taxonomyService = taxonomyService;
    }

    @GetMapping("/capabilities")
    public ApiResponse<Map<String, Object>> capabilities(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(Map.of(
                "taxonomyV2Supported", true,
                "taxonomyV2EntryOpen", taxonomyService.entryOpen(),
                "taxonomyV2Activated", taxonomyService.activated(principal.user()),
                "serverVersion", taxonomyService.serverVersion()));
    }
}
