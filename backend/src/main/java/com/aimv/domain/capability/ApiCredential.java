package com.aimv.domain.capability;

import com.aimv.domain.shared.ChainType;
import java.time.Instant;

public record ApiCredential(
        String apiKeyId,
        ChainType chainType,
        String capabilityType,
        String provider,
        String label,
        String secretHash,
        String encryptedSecret,
        String maskedKey,
        String model,
        ApiKeyStatus status,
        boolean selected,
        Instant lastVerifiedAt,
        FreeModelGateStatus freeModelGateStatus
) {

    public ApiCredential verified(Instant verifiedAt) {
        return new ApiCredential(apiKeyId, chainType, capabilityType, provider, label, secretHash, encryptedSecret,
            maskedKey,
            model, ApiKeyStatus.AVAILABLE, selected, verifiedAt, FreeModelGateStatus.PASSED);
    }

    public ApiCredential selected(boolean isSelected) {
        ApiKeyStatus nextStatus = isSelected ? ApiKeyStatus.ACTIVE : ApiKeyStatus.AVAILABLE;
        return new ApiCredential(apiKeyId, chainType, capabilityType, provider, label, secretHash, encryptedSecret,
            maskedKey,
            model, nextStatus, isSelected, lastVerifiedAt, freeModelGateStatus);
    }

    public ApiCredential deleted() {
        return new ApiCredential(apiKeyId, chainType, capabilityType, provider, label, "", "", maskedKey, model,
            ApiKeyStatus.DELETED, false, lastVerifiedAt, freeModelGateStatus);
    }

    @Override
    public String toString() {
        return "ApiCredential{"
            + "apiKeyId='" + apiKeyId + '\''
            + ", chainType=" + chainType
            + ", capabilityType='" + capabilityType + '\''
            + ", provider='" + provider + '\''
            + ", label='" + label + '\''
            + ", maskedKey='" + maskedKey + '\''
            + ", model='" + model + '\''
            + ", status=" + status
            + ", selected=" + selected
            + ", lastVerifiedAt=" + lastVerifiedAt
            + ", freeModelGateStatus=" + freeModelGateStatus
            + '}';
    }
}
