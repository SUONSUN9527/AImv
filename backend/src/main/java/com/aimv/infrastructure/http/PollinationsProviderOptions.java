package com.aimv.infrastructure.http;

/**
 * Pollinations 免费公共 API 配置。文本走 OpenAI 兼容端点，图片走 GET 直出。
 * 免费匿名层无需 key；若配置了 token 则作为 Bearer 传入以获得更高额度。
 */
record PollinationsProviderOptions(
        String token,
        String textBaseUrl,
        String textModel,
        String imageBaseUrl,
        String imageModel,
        int imageWidth,
        int imageHeight
) {

    private static final String DEFAULT_TEXT_BASE_URL = "https://text.pollinations.ai";
    private static final String DEFAULT_TEXT_MODEL = "openai";
    private static final String DEFAULT_IMAGE_BASE_URL = "https://image.pollinations.ai";
    private static final String DEFAULT_IMAGE_MODEL = "flux";
    private static final int DEFAULT_WIDTH = 720;
    private static final int DEFAULT_HEIGHT = 1280;

    static PollinationsProviderOptions defaults() {
        return new PollinationsProviderOptions("", DEFAULT_TEXT_BASE_URL, DEFAULT_TEXT_MODEL,
            DEFAULT_IMAGE_BASE_URL, DEFAULT_IMAGE_MODEL, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    String tokenValue() {
        return normalized(token);
    }

    boolean hasToken() {
        return !tokenValue().isBlank();
    }

    String textBaseUrlValue() {
        return trimRightSlash(blankToDefault(textBaseUrl, DEFAULT_TEXT_BASE_URL));
    }

    String textModelValue() {
        return blankToDefault(textModel, DEFAULT_TEXT_MODEL);
    }

    String imageBaseUrlValue() {
        return trimRightSlash(blankToDefault(imageBaseUrl, DEFAULT_IMAGE_BASE_URL));
    }

    String imageModelValue() {
        return blankToDefault(imageModel, DEFAULT_IMAGE_MODEL);
    }

    int imageWidthValue() {
        return imageWidth > 0 ? imageWidth : DEFAULT_WIDTH;
    }

    int imageHeightValue() {
        return imageHeight > 0 ? imageHeight : DEFAULT_HEIGHT;
    }

    private static String normalized(String value) {
        return value == null ? "" : value.strip();
    }

    private static String blankToDefault(String value, String fallback) {
        String normalized = normalized(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    private static String trimRightSlash(String value) {
        if (value.endsWith("/")) {
            return trimRightSlash(value.substring(0, value.length() - 1));
        }
        return value;
    }
}
