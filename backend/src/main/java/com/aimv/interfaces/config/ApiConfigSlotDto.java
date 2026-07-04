package com.aimv.interfaces.config;

import com.aimv.domain.capability.ApiConfigSlot;
import com.aimv.domain.shared.ChainType;
import java.util.List;

public record ApiConfigSlotDto(
        ChainType chainType,
        String capabilityType,
        String label,
        boolean required,
        List<ApiKeySummaryDto> keys
) {

    static ApiConfigSlotDto from(ApiConfigSlot slot) {
        return new ApiConfigSlotDto(slot.chainType(), slot.capabilityType(), slot.label(), slot.required(),
            slot.keys().stream().map(ApiKeySummaryDto::from).toList());
    }
}
