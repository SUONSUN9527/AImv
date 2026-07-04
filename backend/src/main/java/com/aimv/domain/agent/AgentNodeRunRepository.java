package com.aimv.domain.agent;

import java.util.Optional;

public interface AgentNodeRunRepository {

    AgentNodeRun save(AgentNodeRun agentNodeRun);

    Optional<AgentNodeRun> findById(String nodeRunId);
}
