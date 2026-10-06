package com.yingying.cuotiku.server.web;

import com.yingying.cuotiku.server.dto.AdminUserDto.AgentConfigDto;
import com.yingying.cuotiku.server.dto.AdminUserDto.UpdateAgentRequest;
import com.yingying.cuotiku.server.dto.ApiResponse;
import com.yingying.cuotiku.server.service.AgentConfigService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/agents")
public class AdminAgentController {

    private final AgentConfigService agentConfigService;

    public AdminAgentController(AgentConfigService agentConfigService) {
        this.agentConfigService = agentConfigService;
    }

    @GetMapping
    public ApiResponse<List<AgentConfigDto>> list() {
        return ApiResponse.ok(agentConfigService.list());
    }

    @PutMapping("/{key}")
    public ApiResponse<AgentConfigDto> update(@PathVariable String key,
                                              @Valid @RequestBody UpdateAgentRequest request) {
        return ApiResponse.ok(agentConfigService.update(key.toUpperCase(), request));
    }

    @PostMapping("/{key}/reset")
    public ApiResponse<AgentConfigDto> reset(@PathVariable String key) {
        return ApiResponse.ok(agentConfigService.reset(key.toUpperCase()));
    }

    @DeleteMapping("/results")
    public ApiResponse<Map<String, Long>> purgeResults(
            @RequestParam(required = false) String agentKey,
            @RequestParam(required = false) String subjectKey) {
        return ApiResponse.ok(Map.of("deleted", agentConfigService.purgeResults(agentKey, subjectKey)));
    }
}
