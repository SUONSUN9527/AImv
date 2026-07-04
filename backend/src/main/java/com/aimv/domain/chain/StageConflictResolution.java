package com.aimv.domain.chain;

import java.util.List;

public record StageConflictResolution(
        String fieldName,
        String selectedAgentName,
        Object selectedValue,
        List<String> conflictingAgentNames,
        String reason
) {

    public StageConflictResolution {
        requireNonBlank(fieldName, "fieldName");
        requireNonBlank(selectedAgentName, "selectedAgentName");
        if (selectedValue == null) {
            throw new IllegalArgumentException("selectedValue must not be null");
        }
        conflictingAgentNames = copy(conflictingAgentNames);
        requireNonBlank(reason, "reason");
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
