package com.aimv.infrastructure.http;

import com.aimv.domain.provider.ProviderHttpRequest;
import com.aimv.domain.provider.ProviderHttpResponse;
import com.aimv.shared.error.BusinessException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

final class DashScopeEmbeddingProviderHttpGateway {

    private static final String EMBEDDING_CAPABILITY = "rag.embedding.free";
    private static final String PROVIDER_MARK = "dashscope";
    private static final String SUCCESS_STATUS = "SUCCEEDED";
    private static final String QUOTA_NOT_RETURNED = "dashscope-embedding:quota-not-returned";

    private final RestTemplate restTemplate;
    private final DashScopeRagProviderOptions options;

    DashScopeEmbeddingProviderHttpGateway(RestTemplate restTemplate, DashScopeRagProviderOptions options) {
        this.restTemplate = restTemplate;
        this.options = options == null ? DashScopeRagProviderOptions.disabled() : options;
    }

    boolean supports(ProviderHttpRequest request) {
        return options.configured() && supportsCapability(request);
    }

    boolean supportsCapability(ProviderHttpRequest request) {
        String provider = request.provider() == null ? "" : request.provider().toLowerCase(Locale.ROOT);
        return EMBEDDING_CAPABILITY.equals(request.capabilityType())
            && provider.contains(PROVIDER_MARK);
    }

    ProviderHttpResponse invoke(ProviderHttpRequest request) {
        return invoke(request, options.apiKeyValue());
    }

    ProviderHttpResponse invoke(ProviderHttpRequest request, String apiKey) {
        try {
            ResponseEntity<DashScopeEmbeddingResponse> response = restTemplate.exchange(endpoint(),
                HttpMethod.POST, new HttpEntity<>(body(request), headers(apiKey)),
                DashScopeEmbeddingResponse.class);
            return response(request, response.getBody());
        } catch (RestClientException exception) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "DASHSCOPE_EMBEDDING_PROVIDER_UNAVAILABLE",
                "DashScope embedding 模型调用失败");
        }
    }

    String configuredApiKey() {
        return options.apiKeyValue();
    }

    private HttpHeaders headers(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        return headers;
    }

    private Map<String, Object> body(ProviderHttpRequest request) {
        return Map.of(
            "model", model(request),
            "input", inputText(request),
            "dimensions", options.embeddingDimensionsValue(),
            "encoding_format", "float"
        );
    }

    private String model(ProviderHttpRequest request) {
        return isBlank(request.model()) ? options.embeddingModelValue() : request.model().strip();
    }

    private String inputText(ProviderHttpRequest request) {
        if (request.input() != null) {
            String text = firstText(request.input().get("text"), request.input().get("query"),
                request.input().get("userGoal"), request.input().get("stageName"), request.input().get("prompt"));
            if (!isBlank(text)) {
                return text;
            }
        }
        return String.valueOf(request.input());
    }

    private ProviderHttpResponse response(ProviderHttpRequest request, DashScopeEmbeddingResponse response) {
        if (response == null) {
            throw invalid("response body");
        }
        int embeddingCount = embeddingCount(response);
        int embeddingDimensions = embeddingDimensions(response);
        if (embeddingCount == 0 || embeddingDimensions == 0) {
            throw invalid("data[].embedding");
        }
        String providerJobId = "dashscope-embedding-" + request.traceId();
        String outputSummary = "DashScope embedding completed: " + embeddingCount
            + " vector(s), dimension " + embeddingDimensions;
        return new ProviderHttpResponse(providerJobId, SUCCESS_STATUS, outputSummary, List.of(),
            metadata(request, providerJobId, response, embeddingCount, embeddingDimensions),
            QUOTA_NOT_RETURNED);
    }

    private Map<String, Object> metadata(ProviderHttpRequest request, String providerJobId,
            DashScopeEmbeddingResponse response, int embeddingCount, int embeddingDimensions) {
        return Map.ofEntries(
            Map.entry("adapterKind", "DASHSCOPE_EMBEDDING_OPENAI_COMPATIBLE"),
            Map.entry("capabilityType", request.capabilityType()),
            Map.entry("stageCode", request.stageCode()),
            Map.entry("provider", request.provider()),
            Map.entry("providerResponseId", providerJobId),
            Map.entry("embeddingModel", response.model() == null ? model(request) : response.model()),
            Map.entry("embeddingCount", embeddingCount),
            Map.entry("embeddingDimensions", embeddingDimensions),
            Map.entry("denseVector", firstEmbedding(response)),
            Map.entry("quotaSource", "not_returned_by_api"),
            Map.entry("usage", usage(response))
        );
    }

    private List<Double> firstEmbedding(DashScopeEmbeddingResponse response) {
        if (response.data() == null) {
            return List.of();
        }
        return response.data().stream()
            .filter(item -> item != null && item.embedding() != null && !item.embedding().isEmpty())
            .findFirst()
            .map(DashScopeEmbeddingData::embedding)
            .orElse(List.of());
    }

    private Map<String, Object> usage(DashScopeEmbeddingResponse response) {
        if (response.usage() == null) {
            return Map.of();
        }
        return response.usage().entrySet().stream()
            .filter(entry -> entry.getValue() != null)
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private int embeddingCount(DashScopeEmbeddingResponse response) {
        if (response.data() == null) {
            return 0;
        }
        return (int) response.data().stream()
            .filter(item -> item != null && item.embedding() != null && !item.embedding().isEmpty())
            .count();
    }

    private int embeddingDimensions(DashScopeEmbeddingResponse response) {
        if (response.data() == null) {
            return 0;
        }
        return response.data().stream()
            .filter(item -> item != null && item.embedding() != null && !item.embedding().isEmpty())
            .findFirst()
            .map(item -> item.embedding().size())
            .orElse(0);
    }

    private String endpoint() {
        return options.baseUrlValue() + "/embeddings";
    }

    private BusinessException invalid(String fieldName) {
        return new BusinessException(HttpStatus.BAD_GATEWAY, "DASHSCOPE_EMBEDDING_RESPONSE_INVALID",
            "DashScope embedding 响应缺少必填字段: " + fieldName);
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            if (value instanceof String text && !text.isBlank()) {
                return text.strip();
            }
        }
        return "";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record DashScopeEmbeddingResponse(
            List<DashScopeEmbeddingData> data,
            String model,
            Map<String, Object> usage
    ) {
    }

    private record DashScopeEmbeddingData(List<Double> embedding) {
    }
}
