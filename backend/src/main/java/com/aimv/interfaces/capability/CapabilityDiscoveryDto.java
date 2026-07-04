package com.aimv.interfaces.capability;

import com.aimv.domain.capability.CapabilityDiscoveryResult;
import com.aimv.domain.shared.ChainType;
import java.util.List;

public record CapabilityDiscoveryDto(
        ChainType chainType,
        String stageCode,
        List<CapabilityRequirementDto> requiredCapabilities
) {

    public static CapabilityDiscoveryDto from(CapabilityDiscoveryResult result) {
        return new CapabilityDiscoveryDto(result.chainType(), result.stageCode(),
            result.requiredCapabilities().stream()
                .map(CapabilityRequirementDto::from)
                .toList());
    }
}
