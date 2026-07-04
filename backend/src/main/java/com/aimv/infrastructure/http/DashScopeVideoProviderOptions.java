package com.aimv.infrastructure.http;

import java.time.Duration;

record DashScopeVideoProviderOptions(
        String apiKey,
        String apiBaseUrl,
        String videoModel,
        String resolution,
        String ratio,
        int durationSeconds,
        int pollAttempts,
        Duration pollInterval,
        boolean nativeVoiceSupported
) {

    private static final String DEFAULT_BASE_URL = "https://dashscope.aliyuncs.com/api/v1";
    private static final String DEFAULT_MODEL = "wan2.7-t2v";
    private static final String DEFAULT_RESOLUTION = "720P";
    private static final String DEFAULT_RATIO = "9:16";
    private static final int DEFAULT_DURATION_SECONDS = 10;

    DashScopeVideoProviderOptions(String apiKey, String apiBaseUrl, String videoModel, String resolution,
            String ratio, int durationSeconds, int pollAttempts, Duration pollInterval) {
        this(apiKey, apiBaseUrl, videoModel, resolution, ratio, durationSeconds, pollAttempts, pollInterval,
            false);
    }

    static DashScopeVideoProviderOptions disabled() {
        return new DashScopeVideoProviderOptions("", DEFAULT_BASE_URL, DEFAULT_MODEL, DEFAULT_RESOLUTION,
            DEFAULT_RATIO, DEFAULT_DURATION_SECONDS, 0, Duration.ZERO, false);
    }

    boolean configured() {
        return !normalized(apiKey).isBlank();
    }

    String apiKeyValue() {
        return normalized(apiKey);
    }

    String baseUrlValue() {
        String value = normalized(apiBaseUrl);
        return trimRightSlash(value.isBlank() ? DEFAULT_BASE_URL : value);
    }

    String modelValue() {
        String value = normalized(videoModel);
        return value.isBlank() ? DEFAULT_MODEL : value;
    }

    String resolutionValue() {
        String value = normalized(resolution);
        return value.isBlank() ? DEFAULT_RESOLUTION : value;
    }

    String ratioValue() {
        String value = normalized(ratio);
        return value.isBlank() ? DEFAULT_RATIO : value;
    }

    int durationSecondsValue() {
        return durationSeconds > 0 ? durationSeconds : DEFAULT_DURATION_SECONDS;
    }

    int pollAttemptsValue() {
        return Math.max(pollAttempts, 0);
    }

    Duration pollIntervalValue() {
        if (pollInterval == null || pollInterval.isNegative()) {
            return Duration.ZERO;
        }
        return pollInterval;
    }

    private static String normalized(String value) {
        return value == null ? "" : value.strip();
    }

    private static String trimRightSlash(String value) {
        if (value.endsWith("/")) {
            return trimRightSlash(value.substring(0, value.length() - 1));
        }
        return value;
    }
}
