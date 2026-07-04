package com.aimv.domain.knowledge;

public record RagCoverage(
        boolean goal,
        boolean stageMap,
        boolean currentStage,
        boolean previousHandoff,
        boolean previousReviewReport,
        boolean passed
) {
}
