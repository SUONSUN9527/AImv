package com.aimv.domain.capability;

public record CapabilityRequirement(
        String capabilityType,
        String label,
        boolean required,
        boolean selected,
        String selectedApiKeyId
) {
}
