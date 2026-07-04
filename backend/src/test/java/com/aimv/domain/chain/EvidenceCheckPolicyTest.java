package com.aimv.domain.chain;

import java.util.List;
import org.junit.jupiter.api.Test;

class EvidenceCheckPolicyTest {

    @Test
    void passesWhenAllCriticalClaimsHaveCitations() {
        EvidenceClaim claim = new EvidenceClaim("I20 handoff uses RAG evidence", true,
            List.of("chunk-001"), List.of("StageCatalog:I20"), true);

        EvidenceCheckReport report = EvidenceCheckPolicy.check(List.of(claim));

        org.assertj.core.api.Assertions.assertThat(report.passed()).isTrue();
        org.assertj.core.api.Assertions.assertThat(report.groundednessScore()).isEqualTo(100);
        org.assertj.core.api.Assertions.assertThat(report.unsupportedCriticalClaims()).isZero();
        org.assertj.core.api.Assertions.assertThat(report.schemaCompliance()).isEqualTo(100);
    }

    @Test
    void rejectsUnsupportedCriticalClaims() {
        EvidenceClaim claim = new EvidenceClaim("unsupported stage fact", true,
            List.of(), List.of(), false);

        EvidenceCheckReport report = EvidenceCheckPolicy.check(List.of(claim));

        org.assertj.core.api.Assertions.assertThat(report.passed()).isFalse();
        org.assertj.core.api.Assertions.assertThat(report.groundednessScore()).isZero();
        org.assertj.core.api.Assertions.assertThat(report.unsupportedCriticalClaims()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(report.unsupportedClaims())
            .containsExactly("unsupported stage fact");
    }
}
