package com.aimv.infrastructure.http;

record DashScopeRagProviderOptions(
        String apiKey,
        String openAiBaseUrl,
        String embeddingModel,
        int embeddingDimensions,
        String rerankModel,
        int rerankTopN
) {

    private static final String DEFAULT_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private static final String DEFAULT_EMBEDDING_MODEL = "text-embedding-v4";
    private static final int DEFAULT_EMBEDDING_DIMENSIONS = 1024;
    private static final String DEFAULT_RERANK_MODEL = "qwen3-rerank";
    private static final int DEFAULT_RERANK_TOP_N = 2;

    static DashScopeRagProviderOptions disabled() {
        return new DashScopeRagProviderOptions("", DEFAULT_BASE_URL, DEFAULT_EMBEDDING_MODEL,
            DEFAULT_EMBEDDING_DIMENSIONS, DEFAULT_RERANK_MODEL, DEFAULT_RERANK_TOP_N);
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

    String embeddingModelValue() {
        String value = normalized(embeddingModel);
        return value.isBlank() ? DEFAULT_EMBEDDING_MODEL : value;
    }

    int embeddingDimensionsValue() {
        return embeddingDimensions > 0 ? embeddingDimensions : DEFAULT_EMBEDDING_DIMENSIONS;
    }

    String rerankModelValue() {
        String value = normalized(rerankModel);
        return value.isBlank() ? DEFAULT_RERANK_MODEL : value;
    }

    int rerankTopNValue() {
        return rerankTopN > 0 ? rerankTopN : DEFAULT_RERANK_TOP_N;
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
