package com.aimv.infrastructure.agent;

import com.aimv.domain.agent.AgentNodeRun;
import com.aimv.domain.agent.AgentNodeRunRepository;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!postgres")
public class InMemoryAgentNodeRunRepository implements AgentNodeRunRepository {

    private final Map<String, AgentNodeRun> nodeRuns = new ConcurrentHashMap<>();

    @Override
    public AgentNodeRun save(AgentNodeRun agentNodeRun) {
        nodeRuns.put(agentNodeRun.nodeRunId(), agentNodeRun);
        return agentNodeRun;
    }

    @Override
    public Optional<AgentNodeRun> findById(String nodeRunId) {
        return Optional.ofNullable(nodeRuns.get(nodeRunId));
    }
}
