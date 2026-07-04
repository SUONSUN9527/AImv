package com.aimv.infrastructure.http;

import com.aimv.domain.provider.ProviderHttpRequest;
import com.aimv.domain.provider.ProviderHttpResponse;
import com.aimv.shared.error.BusinessException;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

final class DashScopeRerankProviderHttpGateway {

    private static final String RERANK_CAPABILITY = "rag.rerank.free";
    private static final String PROVIDER_MARK = "dashscope";
    private static final String SUCCESS_STATUS = "SUCCEEDED";
    private static final String QUOTA_NOT_RETURNED = "dashscope-rerank:quota-not-returned";

    private final RestTemplate restTemplate;
    private final DashScopeRagProviderOptions options;

    DashScopeRerankProviderHttpGateway(RestTemplate restTemplate, DashScopeRagProviderOptions options) {
        this.restTemplate = restTemplate;
        this.options = options == null ? DashScopeRagProviderOptions.disabled() : options;
    }

    boolean supports(ProviderHttpRequest request) {
        return options.configured() && supportsCapability(request);
    }

    boolean supportsCapability(ProviderHttpRequest request) {
        String provider = request.provider() == null ? "" : request.provider().toLowerCase(Locale.ROOT);
        return RERANK_CAPABILITY.equals(request.capabilityType())
            && provider.contains(PROVIDER_MARK);
    }

    ProviderHttpResponse invoke(ProviderHttpRequest request) {
        return invoke(request, options.apiKeyValue());
    }

    ProviderHttpResponse invoke(ProviderHttpRequest request, String apiKey) {
        try {
            List<String> documents = documents(request);
            ResponseEntity<DashScopeRerankResponse> response = restTemplate.exchange(endpoint(),
                HttpMethod.POST, new HttpEntity<>(body(request, documents), headers(apiKey)),
                DashScopeRerankResponse.class);
            return response(request, response.getBody(), documents.size());
        } catch (RestClientException exception) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "DASHSCOPE_RERANK_PROVIDER_UNAVAILABLE",
                "DashScope rerank 模型调用失败");
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

    private Map<String, Object> body(ProviderHttpRequest request, List<String> documents) {
        return Map.of(
            "model", model(request),
            "query", query(request),
            "documents", documents,
            "top_n", topN(documents),
            "instruct", "根据短剧目标选出最相关的创作上下文"
        );
    }

    private String model(ProviderHttpRequest request) {
        return isBlank(request.model()) ? options.rerankModelValue() : request.model().strip();
    }

    private String query(ProviderHttpRequest request) {
        if (request.input() != null) {
            String query = firstText(request.input().get("query"), request.input().get("userGoal"),
                request.input().get("stageName"), request.input().get("prompt"));
            if (!isBlank(query)) {
                return query;
            }
        }
        return request.stageCode() + " " + String.valueOf(request.input());
    }

    private List<String> documents(ProviderHttpRequest request) {
        if (request.input() != null && request.input().get("documents") instanceof List<?> values) {
            List<String> documents = values.stream()
                .filter(value -> value != null)
                .map(String::valueOf)
                .map(String::strip)
                .filter(value -> !value.isBlank())
                .toList();
            if (!documents.isEmpty()) {
                return documents;
            }
        }
        List<String> fallback = Stream.of(stageName(request), query(request))
            .map(String::strip)
            .filter(value -> !value.isBlank())
            .distinct()
            .toList();
        return fallback.isEmpty() ? List.of(String.valueOf(request.input())) : fallback;
    }

    private String stageName(ProviderHttpRequest request) {
        if (request.input() == null) {
            return "";
        }
        return firstText(request.input().get("stageName"), request.input().get("stageGoal"));
    }

    private int topN(List<String> documents) {
        // 检索重排需要对全部候选打分供上层重排序，因此请求所有文档的相关性分。
        return documents.size();
    }

    private ProviderHttpResponse response(ProviderHttpRequest request, DashScopeRerankResponse response,
            int documentCount) {
        if (response == null) {
            throw invalid("response body");
        }
        int resultCount = resultCount(response);
        if (resultCount == 0) {
            throw invalid("results");
        }
        String providerJobId = isBlank(response.id()) ? "dashscope-rerank-" + request.traceId() : response.id();
        String outputSummary = "DashScope rerank completed: " + resultCount + " result(s)";
        return new ProviderHttpResponse(providerJobId, SUCCESS_STATUS, outputSummary, List.of(),
            metadata(request, providerJobId, response, resultCount, documentCount), QUOTA_NOT_RETURNED);
    }

    private Map<String, Object> metadata(ProviderHttpRequest request, String providerJobId,
            DashScopeRerankResponse response, int resultCount, int documentCount) {
        return Map.ofEntries(
            Map.entry("adapterKind", "DASHSCOPE_RERANK_COMPATIBLE"),
            Map.entry("capabilityType", request.capabilityType()),
            Map.entry("stageCode", request.stageCode()),
            Map.entry("provider", request.provider()),
            Map.entry("providerResponseId", providerJobId),
            Map.entry("rerankModel", response.model() == null ? model(request) : response.model()),
            Map.entry("documentCount", documentCount),
            Map.entry("resultCount", resultCount),
            Map.entry("rerankScores", rerankScores(response, documentCount)),
            Map.entry("quotaSource", "not_returned_by_api"),
            Map.entry("usage", usage(response))
        );
    }

    /**
     * 把 rerank 结果（index → relevance_score）还原成与请求 documents 同序、等长的分数列表，
     * 未被返回的文档补 0，供上层按原始候选顺序重排序。
     */
    private List<Double> rerankScores(DashScopeRerankResponse response, int documentCount) {
        Double[] scores = new Double[documentCount];
        if (response.results() != null) {
            for (DashScopeRerankResult result : response.results()) {
                if (result != null && result.relevanceScore() != null
                        && result.index() >= 0 && result.index() < documentCount) {
                    scores[result.index()] = result.relevanceScore();
                }
            }
        }
        List<Double> aligned = new java.util.ArrayList<>(documentCount);
        for (Double score : scores) {
            aligned.add(score == null ? 0.0 : score);
        }
        return List.copyOf(aligned);
    }

    private Map<String, Object> usage(DashScopeRerankResponse response) {
        if (response.usage() == null) {
            return Map.of();
        }
        return response.usage().entrySet().stream()
            .filter(entry -> entry.getValue() != null)
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private int resultCount(DashScopeRerankResponse response) {
        if (response.results() == null) {
            return 0;
        }
        return (int) response.results().stream()
            .filter(result -> result != null && result.relevanceScore() != null)
            .count();
    }

    private String endpoint() {
        return options.baseUrlValue() + "/reranks";
    }

    private BusinessException invalid(String fieldName) {
        return new BusinessException(HttpStatus.BAD_GATEWAY, "DASHSCOPE_RERANK_RESPONSE_INVALID",
            "DashScope rerank 响应缺少必填字段: " + fieldName);
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

    private record DashScopeRerankResponse(
            String id,
            List<DashScopeRerankResult> results,
            String model,
            Map<String, Object> usage
    ) {
    }

    private record DashScopeRerankResult(
            int index,
            @JsonProperty("relevance_score") Double relevanceScore
    ) {
    }
}
