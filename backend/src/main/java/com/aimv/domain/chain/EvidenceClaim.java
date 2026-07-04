package com.aimv.domain.chain;

import java.util.List;

public record EvidenceClaim(
        String claim,
        boolean critical,
        List<String> citationChunkIds,
        List<String> deterministicSourceRefs,
        boolean supported
) {

    public EvidenceClaim {
        requireNonBlank(claim, "claim");
        citationChunkIds = copy(citationChunkIds);
        deterministicSourceRefs = copy(deterministicSourceRefs);
        if (supported && citationChunkIds.isEmpty() && deterministicSourceRefs.isEmpty()) {
            throw new IllegalArgumentException("supported claim must have citation or deterministic source");
        }
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
