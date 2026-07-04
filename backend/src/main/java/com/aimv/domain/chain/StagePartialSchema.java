package com.aimv.domain.chain;

import java.util.List;

public record StagePartialSchema(
        String agentName,
        List<String> requiredFieldNames,
        List<String> allowedFieldNames
) {

    public StagePartialSchema {
        requireNonBlank(agentName, "agentName");
        requiredFieldNames = copy(requiredFieldNames);
        allowedFieldNames = copy(allowedFieldNames);
        if (!allowedFieldNames.containsAll(requiredFieldNames)) {
            throw new IllegalArgumentException("allowedFieldNames must contain all requiredFieldNames");
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
