package com.aimv.interfaces.chain;

import com.aimv.domain.capability.ApiSelectionSnapshot;
import com.aimv.domain.capability.FreeModelGate;
import com.aimv.domain.shared.ChainType;
import java.time.Instant;

public record ApiSelectionSnapshotDto(
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

    static ApiSelectionSnapshotDto from(ApiSelectionSnapshot snapshot) {
        return new ApiSelectionSnapshotDto(snapshot.snapshotId(), snapshot.chainRunId(), snapshot.chainType(),
            snapshot.capabilityType(), snapshot.provider(), snapshot.apiKeyId(), snapshot.maskedKey(),
            snapshot.model(), snapshot.freeModelGate(), snapshot.createdAt());
    }
}
