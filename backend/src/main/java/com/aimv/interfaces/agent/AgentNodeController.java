package com.aimv.interfaces.agent;

import com.aimv.application.agent.AgentNodeApplicationService;
import com.aimv.shared.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AgentNodeController {

    private final AgentNodeApplicationService agentNodeApplicationService;

    public AgentNodeController(AgentNodeApplicationService agentNodeApplicationService) {
        this.agentNodeApplicationService = agentNodeApplicationService;
    }

    @GetMapping("/node-runs/{nodeRunId}")
    public ApiResponse<AgentNodeRunDto> get(@PathVariable String nodeRunId) {
        return ApiResponse.ok(AgentNodeRunDto.from(agentNodeApplicationService.get(nodeRunId)));
    }
}
