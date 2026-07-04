package com.aimv.domain.capability;

public record CapabilityAcquireDecision(
        String capabilityType,
        boolean acquired,
        String status,
        String reason
) {
}
