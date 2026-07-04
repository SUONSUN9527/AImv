package com.aimv.interfaces.agent;

import com.aimv.domain.agent.AgentNodeRun;
import com.aimv.domain.agent.AgentNodeRunStatus;
import com.aimv.domain.capability.FreeModelGate;
import java.time.Instant;

public record AgentNodeRunDto(
        String nodeRunId,
        String chainRunId,
        String stageRunId,
        String stageCode,
        String nodeName,
        AgentNodeRunStatus status,
        String capabilityType,
        String provider,
        String model,
        String providerJobId,
        FreeModelGate freeModelGate,
        String retrievalRecordId,
        String outputSummary,
        Instant startedAt,
        Instant finishedAt
) {

    public static AgentNodeRunDto from(AgentNodeRun nodeRun) {
        return new AgentNodeRunDto(nodeRun.nodeRunId(), nodeRun.chainRunId(), nodeRun.stageRunId(),
            nodeRun.stageCode(), nodeRun.nodeName(), nodeRun.status(), nodeRun.capabilityType(), nodeRun.provider(),
            nodeRun.model(), nodeRun.providerJobId(), nodeRun.freeModelGate(), nodeRun.retrievalRecordId(),
            nodeRun.outputSummary(), nodeRun.startedAt(), nodeRun.finishedAt());
    }
}
