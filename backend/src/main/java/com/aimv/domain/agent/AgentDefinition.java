package com.aimv.domain.agent;

public record AgentDefinition(
        String nodeName,
        String collaborationMode,
        String capabilityType
) {
}
