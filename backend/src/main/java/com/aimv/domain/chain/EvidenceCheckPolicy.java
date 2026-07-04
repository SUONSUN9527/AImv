package com.aimv.domain.chain;

import java.util.List;

public final class EvidenceCheckPolicy {

    private static final int MINIMUM_GROUNDEDNESS_SCORE = 95;
    private static final int FULL_SCHEMA_COMPLIANCE = 100;

    private EvidenceCheckPolicy() {
    }

    public static EvidenceCheckReport check(List<EvidenceClaim> claims) {
        List<EvidenceClaim> safeClaims = claims == null ? List.of() : List.copyOf(claims);
        int totalClaims = safeClaims.size();
        int supportedClaims = Math.toIntExact(safeClaims.stream()
            .filter(EvidenceClaim::supported)
            .count());
        int groundednessScore = totalClaims == 0 ? FULL_SCHEMA_COMPLIANCE
            : supportedClaims * FULL_SCHEMA_COMPLIANCE / totalClaims;
        List<String> unsupportedCriticalClaims = safeClaims.stream()
            .filter(claim -> claim.critical() && !claim.supported())
            .map(EvidenceClaim::claim)
            .toList();
        boolean passed = groundednessScore >= MINIMUM_GROUNDEDNESS_SCORE
            && unsupportedCriticalClaims.isEmpty();
        return new EvidenceCheckReport(passed, groundednessScore, supportedClaims, totalClaims,
            unsupportedCriticalClaims.size(), FULL_SCHEMA_COMPLIANCE, unsupportedCriticalClaims);
    }
}
