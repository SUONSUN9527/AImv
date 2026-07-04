package com.aimv.domain.capability;

import java.time.Instant;

public record FreeModelGate(
        String freeModelGateId,
        boolean passed,
        String provider,
        String model,
        String capabilityType,
        String plan,
        boolean paidFallbackAllowed,
        String quotaSnapshot,
        Instant checkedAt
) {
}
