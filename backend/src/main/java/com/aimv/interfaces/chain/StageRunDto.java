package com.aimv.interfaces.chain;

import com.aimv.domain.chain.StageRun;
import com.aimv.domain.chain.StageRunStatus;
import java.time.Instant;
import java.util.List;

public record StageRunDto(
        String stageRunId,
        String chainRunId,
        String stageCode,
        String stageName,
        StageRunStatus status,
        ReviewReportDto reviewReport,
        String retrievalRecordId,
        String handoffContextId,
        List<String> agentNodeRunIds,
        List<String> freeModelGateIds,
        List<String> providerJobIds,
        Instant startedAt,
        Instant finishedAt
) {

    static StageRunDto from(StageRun stageRun) {
        return new StageRunDto(stageRun.stageRunId(), stageRun.chainRunId(), stageRun.stageCode(),
            stageRun.stageName(), stageRun.status(), ReviewReportDto.from(stageRun.reviewReport()),
            stageRun.retrievalRecordId(), stageRun.handoffContextId(), stageRun.agentNodeRunIds(),
            stageRun.freeModelGateIds(), stageRun.providerJobIds(), stageRun.startedAt(), stageRun.finishedAt());
    }
}
