package com.aimv.infrastructure.http;

record DashScopeImageProviderOptions(String apiKey, String apiBaseUrl, String imageModel, String imageSize) {

    private static final String DEFAULT_BASE_URL = "https://dashscope.aliyuncs.com/api/v1";
    private static final String DEFAULT_MODEL = "wan2.6-t2i";
    private static final String DEFAULT_SIZE = "720*1280";

    static DashScopeImageProviderOptions disabled() {
        return new DashScopeImageProviderOptions("", DEFAULT_BASE_URL, DEFAULT_MODEL, DEFAULT_SIZE);
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
        String value = normalized(imageModel);
        return value.isBlank() ? DEFAULT_MODEL : value;
    }

    String sizeValue() {
        String value = normalized(imageSize);
        return value.isBlank() ? DEFAULT_SIZE : value;
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
