package com.aimv.domain.capability;

import com.aimv.domain.shared.ChainType;
import java.util.List;

public record CapabilityDescriptor(
        String capabilityId,
        String capabilityType,
        String label,
        List<ChainType> chainTypes,
        String adapterKind,
        boolean freeOnly,
        boolean selectedKeyRequired
) {
}
