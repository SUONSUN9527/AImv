package com.aimv.domain.chain;

import com.aimv.domain.shared.ChainType;
import java.util.List;

public record NextStageContext(
        String schemaVersion,
        String chainRunId,
        ChainType chainType,
        String fromStage,
        String toStage,
        List<String> outputArtifactIds,
        String reviewReportId,
        List<String> evidenceChunkIds,
        List<EvidenceClaim> claims,
        EvidenceCheckReport evidenceCheck,
        StageCoordinationResult stageOutput,
        List<String> assumptions
) {

    public NextStageContext {
        requireNonBlank(schemaVersion, "schemaVersion");
        requireNonBlank(chainRunId, "chainRunId");
        if (chainType == null) {
            throw new IllegalArgumentException("chainType must not be null");
        }
        requireNonBlank(fromStage, "fromStage");
        requireNonBlank(toStage, "toStage");
        requireNonBlank(reviewReportId, "reviewReportId");
        outputArtifactIds = copy(outputArtifactIds);
        evidenceChunkIds = copy(evidenceChunkIds);
        claims = claims == null ? List.of() : List.copyOf(claims);
        if (evidenceCheck == null) {
            throw new IllegalArgumentException("evidenceCheck must not be null");
        }
        if (stageOutput == null) {
            throw new IllegalArgumentException("stageOutput must not be null");
        }
        assumptions = copy(assumptions);
    }

    private static List<String> copy(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return List.copyOf(values);
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
