package com.aimv.interfaces.chain;

import com.aimv.domain.chain.ReviewReport;

public record ReviewReportDto(
        boolean passed,
        int overallScore,
        String rubricVersion,
        String summary
) {

    static ReviewReportDto from(ReviewReport reviewReport) {
        return new ReviewReportDto(reviewReport.passed(), reviewReport.overallScore(), reviewReport.rubricVersion(),
            reviewReport.summary());
    }
}
