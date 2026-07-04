package com.aimv.interfaces.capability;

import com.aimv.domain.capability.CapabilityDescriptor;
import com.aimv.domain.shared.ChainType;
import java.util.List;

public record CapabilityDto(
        String capabilityId,
        String capabilityType,
        String label,
        List<ChainType> chainTypes,
        String adapterKind,
        boolean freeOnly,
        boolean selectedKeyRequired
) {

    public static CapabilityDto from(CapabilityDescriptor descriptor) {
        return new CapabilityDto(descriptor.capabilityId(), descriptor.capabilityType(), descriptor.label(),
            descriptor.chainTypes(), descriptor.adapterKind(), descriptor.freeOnly(),
            descriptor.selectedKeyRequired());
    }
}
