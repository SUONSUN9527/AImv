package com.aimv.infrastructure.http;

import com.aimv.domain.provider.ProviderHttpRequest;
import com.aimv.domain.provider.ProviderHttpResponse;
import com.aimv.infrastructure.http.LlmStructuredOutput.Completion;
import com.aimv.shared.error.BusinessException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Pollinations 免费文本网关（llm.text.free，provider 含 "pollinations"）。走 OpenAI 兼容端点
 * POST {textBaseUrl}/openai。结构化输出复用 {@link LlmStructuredOutput}。免费匿名层无需 key，
 * verify 直接返回可用。
 */
final class PollinationsTextProviderHttpGateway {

    private static final String TEXT_CAPABILITY = "llm.text.free";
    private static final String PROVIDER_MARK = "pollinations";
    private static final String QUOTA_FREE = "pollinations-free:anonymous-tier";
    private static final String VERIFY_OPERATION = "VERIFY_API_KEY";
    private static final double PLANNING_TEMPERATURE = 0.3;

    private final RestTemplate restTemplate;
    private final PollinationsProviderOptions options;
    private final LlmStructuredOutput structuredOutput = new LlmStructuredOutput(
        "POLLINATIONS_OPENAI_COMPATIBLE", QUOTA_FREE, "pollinations-chat-");

    PollinationsTextProviderHttpGateway(RestTemplate restTemplate, PollinationsProviderOptions options) {
        this.restTemplate = restTemplate;
        this.options = options == null ? PollinationsProviderOptions.defaults() : options;
    }

    boolean supportsCapability(ProviderHttpRequest request) {
        String provider = request.provider() == null ? "" : request.provider().toLowerCase(Locale.ROOT);
        return TEXT_CAPABILITY.equals(request.capabilityType()) && provider.contains(PROVIDER_MARK);
    }

    ProviderHttpResponse invoke(ProviderHttpRequest request, String token) {
        if (isVerify(request)) {
            return verifyResponse(request);
        }
        return structuredOutput.respond(request,
            (systemPrompt, userPrompt, imageUrls) -> chatCompletion(request, token, systemPrompt, userPrompt));
    }

    private Completion chatCompletion(ProviderHttpRequest request, String token, String systemPrompt,
            String userPrompt) {
        try {
            ResponseEntity<PollinationsChatResponse> response = restTemplate.exchange(endpoint(),
                HttpMethod.POST, new HttpEntity<>(body(request, systemPrompt, userPrompt), headers(token)),
                PollinationsChatResponse.class);
            if (response.getBody() == null) {
                throw invalid("response body");
            }
            return new Completion(response.getBody().id(), firstOutput(response.getBody()));
        } catch (RestClientException exception) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "POLLINATIONS_TEXT_PROVIDER_UNAVAILABLE",
                "Pollinations 免费文本模型调用失败");
        }
    }

    private ProviderHttpResponse verifyResponse(ProviderHttpRequest request) {
        return new ProviderHttpResponse("pollinations-verify-" + request.traceId(), "SUCCEEDED",
            "Pollinations 免费文本能力可用（匿名免费层）", List.of(),
            Map.of("adapterKind", "POLLINATIONS_OPENAI_COMPATIBLE", "capabilityType", request.capabilityType(),
                "provider", request.provider(), "quotaSource", "free_anonymous_tier"),
            QUOTA_FREE);
    }

    private HttpHeaders headers(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null && !token.isBlank()) {
            headers.setBearerAuth(token.strip());
        }
        return headers;
    }

    private Map<String, Object> body(ProviderHttpRequest request, String systemPrompt, String userPrompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model(request));
        body.put("messages", List.of(
            Map.of("role", "system", "content", systemPrompt),
            Map.of("role", "user", "content", userPrompt)
        ));
        body.put("temperature", PLANNING_TEMPERATURE);
        body.put("private", true);
        body.put("referrer", "aimv");
        return Map.copyOf(body);
    }

    private String firstOutput(PollinationsChatResponse response) {
        if (response.choices() == null || response.choices().isEmpty()) {
            throw invalid("choices");
        }
        PollinationsMessage message = response.choices().get(0).message();
        if (message == null || isBlank(message.content())) {
            throw invalid("choices[0].message.content");
        }
        return message.content();
    }

    private boolean isVerify(ProviderHttpRequest request) {
        return request.input() != null && VERIFY_OPERATION.equals(request.input().get("operation"));
    }

    private String model(ProviderHttpRequest request) {
        return isBlank(request.model()) || "free-model".equals(request.model())
            ? options.textModelValue() : request.model().strip();
    }

    private String endpoint() {
        return options.textBaseUrlValue() + "/openai";
    }

    private BusinessException invalid(String fieldName) {
        return new BusinessException(HttpStatus.BAD_GATEWAY, "POLLINATIONS_TEXT_RESPONSE_INVALID",
            "Pollinations 文本响应缺少必填字段: " + fieldName);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record PollinationsChatResponse(
            String id,
            List<PollinationsChoice> choices
    ) {
    }

    private record PollinationsChoice(PollinationsMessage message) {
    }

    private record PollinationsMessage(String content) {
    }
}
