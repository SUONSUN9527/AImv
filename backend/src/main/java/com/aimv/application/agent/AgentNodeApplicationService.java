package com.aimv.application.agent;

import com.aimv.domain.agent.AgentNodeRun;
import com.aimv.domain.agent.AgentNodeRunRepository;
import com.aimv.shared.error.ResourceNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AgentNodeApplicationService {

    private final AgentNodeRunRepository agentNodeRunRepository;

    public AgentNodeApplicationService(AgentNodeRunRepository agentNodeRunRepository) {
        this.agentNodeRunRepository = agentNodeRunRepository;
    }

    public AgentNodeRun get(String nodeRunId) {
        return agentNodeRunRepository.findById(nodeRunId)
            .orElseThrow(() -> new ResourceNotFoundException("Agent 节点运行不存在"));
    }
}
