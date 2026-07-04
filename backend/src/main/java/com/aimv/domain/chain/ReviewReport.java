package com.aimv.domain.chain;

public record ReviewReport(
        boolean passed,
        int overallScore,
        String rubricVersion,
        String summary
) {
}
