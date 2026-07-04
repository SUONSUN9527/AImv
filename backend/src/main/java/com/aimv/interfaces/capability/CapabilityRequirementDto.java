package com.aimv.interfaces.capability;

import com.aimv.domain.capability.CapabilityRequirement;

public record CapabilityRequirementDto(
        String capabilityType,
        String label,
        boolean required,
        boolean selected,
        String selectedApiKeyId
) {

    public static CapabilityRequirementDto from(CapabilityRequirement requirement) {
        return new CapabilityRequirementDto(requirement.capabilityType(), requirement.label(), requirement.required(),
            requirement.selected(), requirement.selectedApiKeyId());
    }
}
