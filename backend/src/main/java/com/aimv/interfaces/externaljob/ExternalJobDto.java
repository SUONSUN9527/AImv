package com.aimv.interfaces.externaljob;

import com.aimv.domain.externaljob.ExternalJob;
import com.aimv.domain.externaljob.ExternalJobStatus;
import java.time.Instant;
import java.util.Map;

public record ExternalJobDto(
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

    static ExternalJobDto from(ExternalJob externalJob) {
        return new ExternalJobDto(externalJob.externalJobId(), externalJob.providerJobId(),
            externalJob.chainRunId(), externalJob.stageRunId(), externalJob.capabilityType(),
            externalJob.provider(), externalJob.status(), externalJob.retryPolicy(), externalJob.retryCount(),
            externalJob.requestHash(), externalJob.responseMetadata(), externalJob.createdAt(),
            externalJob.updatedAt());
    }
}
