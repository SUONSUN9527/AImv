package com.aimv.domain.chain;

import java.util.List;
import java.util.Map;

public record StageCoordinationResult(
        String outputSchemaId,
        Map<String, Object> mergedOutput,
        List<StageConflictResolution> conflictResolutions
) {

    public static StageCoordinationResult empty(String outputSchemaId) {
        return new StageCoordinationResult(outputSchemaId, Map.of(), List.of());
    }

    public StageCoordinationResult {
        requireNonBlank(outputSchemaId, "outputSchemaId");
        if (mergedOutput == null) {
            mergedOutput = Map.of();
        }
        mergedOutput = Map.copyOf(mergedOutput);
        if (conflictResolutions == null) {
            conflictResolutions = List.of();
        }
        conflictResolutions = List.copyOf(conflictResolutions);
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
