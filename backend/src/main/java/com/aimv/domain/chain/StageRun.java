package com.aimv.domain.chain;

import java.time.Instant;

public record StageRun(
        String stageRunId,
        String chainRunId,
        String stageCode,
        String stageName,
        StageRunStatus status,
        ReviewReport reviewReport,
        String retrievalRecordId,
        String handoffContextId,
        java.util.List<String> agentNodeRunIds,
        java.util.List<String> freeModelGateIds,
        java.util.List<String> providerJobIds,
        Instant startedAt,
        Instant finishedAt
) {
}
