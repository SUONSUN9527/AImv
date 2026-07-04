package com.aimv.domain.agent;

import com.aimv.domain.capability.FreeModelGate;
import java.time.Instant;

public record AgentNodeRun(
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
}
