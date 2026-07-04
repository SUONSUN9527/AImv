package com.aimv.domain.capability;

import com.aimv.domain.shared.ChainType;
import java.time.Instant;

public record ApiSelectionSnapshot(
        String snapshotId,
        String chainRunId,
        ChainType chainType,
        String capabilityType,
        String provider,
        String apiKeyId,
        String maskedKey,
        String model,
        FreeModelGate freeModelGate,
        Instant createdAt
) {
}
