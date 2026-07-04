package com.aimv.domain.externaljob;

import java.util.Locale;

public enum ExternalJobStatus {
    SUBMITTED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED;

    public static ExternalJobStatus fromProviderStatus(String providerStatus) {
        if (providerStatus == null || providerStatus.isBlank()) {
            return SUBMITTED;
        }
        String normalizedStatus = providerStatus.trim().toUpperCase(Locale.ROOT);
        return switch (normalizedStatus) {
            case "QUEUED", "PENDING", "SUBMITTED" -> SUBMITTED;
            case "RUNNING", "STARTED", "PROCESSING", "IN_PROGRESS" -> RUNNING;
            case "SUCCESS", "SUCCEEDED", "DONE", "COMPLETED" -> SUCCEEDED;
            case "FAIL", "FAILED", "ERROR" -> FAILED;
            case "CANCELLED", "CANCELED" -> CANCELLED;
            default -> RUNNING;
        };
    }
}
