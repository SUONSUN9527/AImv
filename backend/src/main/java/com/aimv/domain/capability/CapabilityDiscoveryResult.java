package com.aimv.domain.capability;

import com.aimv.domain.shared.ChainType;
import java.util.List;

public record CapabilityDiscoveryResult(
        ChainType chainType,
        String stageCode,
        List<CapabilityRequirement> requiredCapabilities
) {
}
