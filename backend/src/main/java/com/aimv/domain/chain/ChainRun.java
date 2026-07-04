package com.aimv.domain.chain;

import com.aimv.domain.artifact.Artifact;
import com.aimv.domain.shared.ChainType;
import java.time.Instant;
import java.util.List;

public record ChainRun(
        String chainRunId,
        String projectId,
        ChainType chainType,
        String userGoal,
        ChainRunStatus status,
        String currentStageCode,
        List<StageRun> stageRuns,
        List<Artifact> artifacts,
        String blockingReason,
        Instant createdAt,
        Instant updatedAt
) {
}
