package com.aimv.interfaces.capability;

import jakarta.validation.constraints.NotBlank;

public record CapabilityAcquireRequest(
        @NotBlank String capabilityType,
        boolean downloadModelWeights
) {
}
