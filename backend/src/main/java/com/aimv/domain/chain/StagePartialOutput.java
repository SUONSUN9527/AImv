package com.aimv.domain.chain;

import java.util.Map;

public record StagePartialOutput(
        String agentName,
        Map<String, Object> fields
) {

    public StagePartialOutput {
        requireNonBlank(agentName, "agentName");
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("fields must not be empty");
        }
        fields = Map.copyOf(fields);
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
