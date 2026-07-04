package com.aimv.interfaces.chain;

import com.aimv.domain.chain.ChainRun;
import com.aimv.domain.chain.ChainRunStatus;
import com.aimv.domain.shared.ChainType;
import java.time.Instant;
import java.util.List;

public record ChainRunDto(
        String chainRunId,
        String projectId,
        ChainType chainType,
        String userGoal,
        ChainRunStatus status,
        String currentStageCode,
        List<StageRunDto> stageRuns,
        List<ArtifactDto> artifacts,
        String blockingReason,
        Instant createdAt,
        Instant updatedAt
) {

    static ChainRunDto from(ChainRun chainRun) {
        return new ChainRunDto(chainRun.chainRunId(), chainRun.projectId(), chainRun.chainType(),
            chainRun.userGoal(), chainRun.status(), chainRun.currentStageCode(),
            chainRun.stageRuns().stream().map(StageRunDto::from).toList(),
            chainRun.artifacts().stream().map(ArtifactDto::from).toList(), chainRun.blockingReason(),
            chainRun.createdAt(), chainRun.updatedAt());
    }
}
