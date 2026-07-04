package com.aimv.application.agent;

import com.aimv.domain.agent.AgentNodeRun;
import java.util.List;
import java.util.Map;

public record AgentNodeResult(
        AgentNodeRun nodeRun,
        Map<String, Object> providerMetadata,
        List<String> artifactRefs
) {

    public AgentNodeResult(AgentNodeRun nodeRun, Map<String, Object> providerMetadata) {
        this(nodeRun, providerMetadata, List.of());
    }
}
