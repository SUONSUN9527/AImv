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
 * DashScope OpenAI 兼容文本模型网关。结构化输出（partialSchema→JSON→重试）交给
 * {@link LlmStructuredOutput} 引擎，本类只负责 DashScope 的 HTTP 调用细节。
 */
final class DashScopeTextProviderHttpGateway {

    private static final String TEXT_CAPABILITY = "llm.text.free";
    private static final String PROVIDER_MARK = "dashscope";
    private static final String QUOTA_NOT_RETURNED = "dashscope-openai-compatible:quota-not-returned";
    private static final double PLANNING_TEMPERATURE = 0.3;

    private final RestTemplate restTemplate;
    private final DashScopeTextProviderOptions options;
    private final LlmStructuredOutput structuredOutput = new LlmStructuredOutput(
        "DASHSCOPE_OPENAI_COMPATIBLE", QUOTA_NOT_RETURNED, "dashscope-chat-");

    DashScopeTextProviderHttpGateway(RestTemplate restTemplate, DashScopeTextProviderOptions options) {
        this.restTemplate = restTemplate;
        this.options = options == null ? DashScopeTextProviderOptions.disabled() : options;
    }

    boolean supports(ProviderHttpRequest request) {
        return options.configured() && supportsCapability(request);
    }

    boolean supportsCapability(ProviderHttpRequest request) {
        String provider = request.provider() == null ? "" : request.provider().toLowerCase(Locale.ROOT);
        return TEXT_CAPABILITY.equals(request.capabilityType())
            && provider.contains(PROVIDER_MARK);
    }

    ProviderHttpResponse invoke(ProviderHttpRequest request) {
        return invoke(request, options.apiKeyValue());
    }

    ProviderHttpResponse invoke(ProviderHttpRequest request, String apiKey) {
        return structuredOutput.respond(request,
            (systemPrompt, userPrompt, imageUrls) -> chatCompletion(request, apiKey, systemPrompt, userPrompt,
                imageUrls));
    }

    String configuredApiKey() {
        return options.apiKeyValue();
    }

    private Completion chatCompletion(ProviderHttpRequest request, String apiKey, String systemPrompt,
            String userPrompt, List<String> imageUrls) {
        try {
            ResponseEntity<DashScopeChatCompletionResponse> response = restTemplate.exchange(endpoint(),
                HttpMethod.POST,
                new HttpEntity<>(body(request, systemPrompt, userPrompt, imageUrls), headers(apiKey)),
                DashScopeChatCompletionResponse.class);
            if (response.getBody() == null) {
                throw invalid("response body");
            }
            return new Completion(response.getBody().id(), firstOutput(response.getBody()));
        } catch (RestClientException exception) {
            // 带上真实原因，便于排障（此前吞掉 cause 导致无法定位 401/model/格式等问题）。
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "DASHSCOPE_TEXT_PROVIDER_UNAVAILABLE",
                "DashScope OpenAI 兼容文本模型调用失败: " + exception.getMessage());
        }
    }

    private HttpHeaders headers(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        return headers;
    }

    private Map<String, Object> body(ProviderHttpRequest request, String systemPrompt, String userPrompt,
            List<String> imageUrls) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model(request));
        body.put("messages", List.of(
            Map.of("role", "system", "content", systemPrompt),
            Map.of("role", "user", "content", userContent(userPrompt, imageUrls))
        ));
        body.put("stream", false);
        body.put("temperature", PLANNING_TEMPERATURE);
        return Map.copyOf(body);
    }

    /**
     * 无图片时用纯文本 content；有候选图时用 OpenAI 多模态 content 数组（text + image_url），
     * 让 qwen-vl 等视觉模型真正看图评分。
     */
    private Object userContent(String userPrompt, List<String> imageUrls) {
        if (imageUrls.isEmpty()) {
            return userPrompt;
        }
        List<Map<String, Object>> content = new java.util.ArrayList<>();
        content.add(Map.of("type", "text", "text", userPrompt));
        for (String url : imageUrls) {
            content.add(Map.of("type", "image_url", "image_url", Map.of("url", url)));
        }
        return List.copyOf(content);
    }

    private String firstOutput(DashScopeChatCompletionResponse response) {
        if (response.choices() == null || response.choices().isEmpty()) {
            throw invalid("choices");
        }
        DashScopeMessage message = response.choices().get(0).message();
        if (message == null || isBlank(message.content())) {
            throw invalid("choices[0].message.content");
        }
        return message.content();
    }

    private String model(ProviderHttpRequest request) {
        return isBlank(request.model()) ? options.modelValue() : request.model().strip();
    }

    private String endpoint() {
        return options.baseUrlValue() + "/chat/completions";
    }

    private BusinessException invalid(String fieldName) {
        return new BusinessException(HttpStatus.BAD_GATEWAY, "DASHSCOPE_TEXT_RESPONSE_INVALID",
            "DashScope OpenAI 兼容响应缺少必填字段: " + fieldName);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record DashScopeChatCompletionResponse(
            String id,
            List<DashScopeChoice> choices,
            Map<String, Object> usage
    ) {
    }

    private record DashScopeChoice(DashScopeMessage message) {
    }

    private record DashScopeMessage(String content) {
    }
}
