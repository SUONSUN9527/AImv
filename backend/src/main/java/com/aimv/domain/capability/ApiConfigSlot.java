package com.aimv.domain.capability;

import com.aimv.domain.shared.ChainType;
import java.util.List;

public record ApiConfigSlot(
        ChainType chainType,
        String capabilityType,
        String label,
        boolean required,
        List<ApiCredential> keys
) {
}
