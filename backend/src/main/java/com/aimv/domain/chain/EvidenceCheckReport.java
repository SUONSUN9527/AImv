package com.aimv.domain.chain;

import java.util.List;

public record EvidenceCheckReport(
        boolean passed,
        int groundednessScore,
        int supportedClaims,
        int totalClaims,
        int unsupportedCriticalClaims,
        int schemaCompliance,
        List<String> unsupportedClaims
) {

    public EvidenceCheckReport {
        if (groundednessScore < 0 || groundednessScore > 100) {
            throw new IllegalArgumentException("groundednessScore must be between 0 and 100");
        }
        if (schemaCompliance < 0 || schemaCompliance > 100) {
            throw new IllegalArgumentException("schemaCompliance must be between 0 and 100");
        }
        if (supportedClaims < 0 || totalClaims < 0 || unsupportedCriticalClaims < 0) {
            throw new IllegalArgumentException("claim counts must not be negative");
        }
        if (supportedClaims > totalClaims) {
            throw new IllegalArgumentException("supportedClaims must not exceed totalClaims");
        }
        unsupportedClaims = unsupportedClaims == null ? List.of() : List.copyOf(unsupportedClaims);
    }
}
