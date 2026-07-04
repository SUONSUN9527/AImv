package com.aimv.domain.provider;

import java.util.Map;

public record ProviderHttpRequest(
        String traceId,
        String chainRunId,
        String stageRunId,
        String stageCode,
        String nodeRunId,
        String nodeName,
        String capabilityType,
        String provider,
        String model,
        String freeModelGateId,
        String apiKeyId,
        String maskedKey,
        Map<String, Object> input
) {
}
