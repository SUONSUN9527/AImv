package com.aimv.application.agent;

import com.aimv.domain.agent.AgentDefinition;

public interface AgentNodeFactory {

    BaseAgentNode create(AgentDefinition definition, String chainRunId, String stageRunId, String nodeRunId);
}
