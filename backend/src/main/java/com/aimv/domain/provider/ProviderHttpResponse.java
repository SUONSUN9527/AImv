package com.aimv.domain.provider;

import java.util.List;
import java.util.Map;

public record ProviderHttpResponse(
        String providerJobId,
        String status,
        String outputSummary,
        List<String> artifactRefs,
        Map<String, Object> providerMetadata,
        String freeQuotaSnapshot
) {
}
