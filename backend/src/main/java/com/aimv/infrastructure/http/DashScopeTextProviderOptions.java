package com.aimv.infrastructure.http;

record DashScopeTextProviderOptions(String apiKey, String openAiBaseUrl, String llmModel) {

    private static final String DEFAULT_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private static final String DEFAULT_MODEL = "qwen-plus";

    static DashScopeTextProviderOptions disabled() {
        return new DashScopeTextProviderOptions("", DEFAULT_BASE_URL, DEFAULT_MODEL);
    }

    boolean configured() {
        return !normalized(apiKey).isBlank();
    }

    String apiKeyValue() {
        return normalized(apiKey);
    }

    String baseUrlValue() {
        String value = normalized(openAiBaseUrl);
        return trimRightSlash(value.isBlank() ? DEFAULT_BASE_URL : value);
    }

    String modelValue() {
        String value = normalized(llmModel);
        return value.isBlank() ? DEFAULT_MODEL : value;
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
