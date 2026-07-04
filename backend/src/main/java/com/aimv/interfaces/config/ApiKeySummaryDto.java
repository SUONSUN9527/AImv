package com.aimv.interfaces.config;

import com.aimv.domain.capability.ApiCredential;
import com.aimv.domain.capability.ApiKeyStatus;
import com.aimv.domain.capability.FreeModelGateStatus;
import java.time.Instant;

public record ApiKeySummaryDto(
        String apiKeyId,
        String chainType,
        String capabilityType,
        String label,
        String provider,
        String maskedKey,
        ApiKeyStatus status,
        boolean isSelected,
        Instant lastVerifiedAt,
        FreeModelGateStatus freeModelGateStatus
) {

    static ApiKeySummaryDto from(ApiCredential credential) {
        return new ApiKeySummaryDto(credential.apiKeyId(), credential.chainType().name(),
            credential.capabilityType(), credential.label(), credential.provider(), credential.maskedKey(),
            credential.status(), credential.selected(), credential.lastVerifiedAt(), credential.freeModelGateStatus());
    }
}
