package com.aimv.domain.externaljob;

import java.time.Instant;
import java.util.Map;

public record ExternalJob(
        String externalJobId,
        String providerJobId,
        String chainRunId,
        String stageRunId,
        String capabilityType,
        String provider,
        ExternalJobStatus status,
        String retryPolicy,
        int retryCount,
        String requestHash,
        Map<String, Object> responseMetadata,
        Instant createdAt,
        Instant updatedAt
) {

    public ExternalJob {
        responseMetadata = responseMetadata == null ? Map.of() : Map.copyOf(responseMetadata);
    }
}
