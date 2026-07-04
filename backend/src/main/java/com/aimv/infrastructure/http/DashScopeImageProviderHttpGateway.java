package com.aimv.infrastructure.http;

import com.aimv.domain.provider.ProviderHttpRequest;
import com.aimv.domain.provider.ProviderHttpResponse;
import com.aimv.shared.error.BusinessException;
import com.fasterxml.jackson.annotation.JsonProperty;
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

final class DashScopeImageProviderHttpGateway {

    private static final String IMAGE_CAPABILITY = "image.generate.free";
    private static final String PROVIDER_MARK = "dashscope";
    private static final String SUCCESS_STATUS = "SUCCEEDED";
    private static final String QUOTA_NOT_RETURNED = "dashscope-image-sync:quota-not-returned";

    private final RestTemplate restTemplate;
    private final DashScopeImageProviderOptions options;
    private final String mediaStorageDir;
    private final String mediaPublicBaseUrl;

    DashScopeImageProviderHttpGateway(RestTemplate restTemplate, DashScopeImageProviderOptions options) {
        this(restTemplate, options, "./storage/media", "http://127.0.0.1:8081");
    }

    DashScopeImageProviderHttpGateway(RestTemplate restTemplate, DashScopeImageProviderOptions options,
            String mediaStorageDir, String mediaPublicBaseUrl) {
        this.restTemplate = restTemplate;
        this.options = options == null ? DashScopeImageProviderOptions.disabled() : options;
        this.mediaStorageDir = mediaStorageDir;
        this.mediaPublicBaseUrl = mediaPublicBaseUrl;
    }

    boolean supports(ProviderHttpRequest request) {
        return options.configured() && supportsCapability(request);
    }

    boolean supportsCapability(ProviderHttpRequest request) {
        String provider = request.provider() == null ? "" : request.provider().toLowerCase(Locale.ROOT);
        return IMAGE_CAPABILITY.equals(request.capabilityType())
            && provider.contains(PROVIDER_MARK);
    }

    ProviderHttpResponse invoke(ProviderHttpRequest request) {
        return invoke(request, options.apiKeyValue());
    }

    ProviderHttpResponse invoke(ProviderHttpRequest request, String apiKey) {
        try {
            ResponseEntity<DashScopeImageResponse> response = restTemplate.exchange(endpoint(), HttpMethod.POST,
                new HttpEntity<>(body(request), headers(apiKey)), DashScopeImageResponse.class);
            return response(request, response.getBody());
        } catch (RestClientException exception) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "DASHSCOPE_IMAGE_PROVIDER_UNAVAILABLE",
                "DashScope 图片模型调用失败");
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
            "input", input(request),
            "parameters", parameters()
        );
    }

    private Map<String, Object> input(ProviderHttpRequest request) {
        return Map.of("messages", List.of(Map.of(
            "role", "user",
            "content", List.of(Map.of("text", prompt(request)))
        )));
    }

    private Map<String, Object> parameters() {
        return Map.of(
            "prompt_extend", true,
            "watermark", false,
            "n", 1,
            "size", options.sizeValue()
        );
    }

    private String prompt(ProviderHttpRequest request) {
        if (isVerifyRequest(request)) {
            return "一张简单的蓝色渐变色块测试图，用于验证 API key 连通性";
        }
        if (request.input() != null) {
            String prompt = firstText(request.input().get("prompt"), request.input().get("userGoal"),
                request.input().get("stageGoal"));
            if (!isBlank(prompt)) {
                return prompt;
            }
        }
        return request.stageCode() + " " + String.valueOf(request.input());
    }

    private boolean isVerifyRequest(ProviderHttpRequest request) {
        return request.input() != null
            && "VERIFY_API_KEY".equals(request.input().get("operation"));
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            if (value instanceof String text && !text.isBlank()) {
                return text.strip();
            }
        }
        return "";
    }

    private String model(ProviderHttpRequest request) {
        return isBlank(request.model()) ? options.modelValue() : request.model().strip();
    }

    private ProviderHttpResponse response(ProviderHttpRequest request, DashScopeImageResponse response) {
        if (response == null) {
            throw invalid("response body");
        }
        List<String> imageRefs = imageRefs(response);
        if (imageRefs.isEmpty()) {
            throw invalid("output.choices[].message.content[].image");
        }
        // DashScope 返回的是会过期的 OSS 预签名链接 → 转存到本地 /media 永久托管，避免历史图失效。
        imageRefs = MediaRehoster.rehostAll(imageRefs, mediaStorageDir, mediaPublicBaseUrl);
        String providerJobId = isBlank(response.requestId())
            ? "dashscope-image-" + request.traceId() : response.requestId();
        return new ProviderHttpResponse(providerJobId, SUCCESS_STATUS, "DashScope 图片生成完成",
            imageRefs, metadata(request, providerJobId, response, imageRefs), QUOTA_NOT_RETURNED);
    }

    private List<String> imageRefs(DashScopeImageResponse response) {
        if (response.output() == null || response.output().choices() == null) {
            return List.of();
        }
        return response.output().choices().stream()
            .filter(choice -> choice != null && choice.message() != null && choice.message().content() != null)
            .flatMap(choice -> choice.message().content().stream())
            .map(DashScopeImageContent::image)
            .filter(image -> !isBlank(image))
            .toList();
    }

    private Map<String, Object> metadata(ProviderHttpRequest request, String providerJobId,
            DashScopeImageResponse response, List<String> imageRefs) {
        // imageRefs 已是转存后的永久 /media 链接，candidateRefs 直接用它，保证最终产物 URL 也永久。
        return Map.ofEntries(
            Map.entry("adapterKind", "DASHSCOPE_IMAGE_SYNC"),
            Map.entry("capabilityType", request.capabilityType()),
            Map.entry("stageCode", request.stageCode()),
            Map.entry("provider", request.provider()),
            Map.entry("providerResponseId", providerJobId),
            Map.entry("candidateCount", imageRefs.size()),
            Map.entry("aspectRatio", aspectRatioFromSize(options.sizeValue())),
            Map.entry("artifactIntegrityScore", imageRefs.isEmpty() ? 0 : 100),
            Map.entry("candidateRefs", imageRefs),
            Map.entry("quotaSource", "not_returned_by_api"),
            Map.entry("usage", usage(response))
        );
    }

    private String aspectRatioFromSize(String size) {
        if (isBlank(size)) {
            return "";
        }
        String[] parts = size.split("[*xX]");
        if (parts.length != 2) {
            return size;
        }
        try {
            int width = Integer.parseInt(parts[0].strip());
            int height = Integer.parseInt(parts[1].strip());
            int divisor = gcd(width, height);
            return (width / divisor) + ":" + (height / divisor);
        } catch (NumberFormatException exception) {
            return size;
        }
    }

    private int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }

    private Map<String, Object> usage(DashScopeImageResponse response) {
        if (response.usage() == null) {
            return Map.of();
        }
        return response.usage().entrySet().stream()
            .filter(entry -> entry.getValue() != null)
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private String endpoint() {
        return options.baseUrlValue() + "/services/aigc/multimodal-generation/generation";
    }

    private BusinessException invalid(String fieldName) {
        return new BusinessException(HttpStatus.BAD_GATEWAY, "DASHSCOPE_IMAGE_RESPONSE_INVALID",
            "DashScope 图片响应缺少必填字段: " + fieldName);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record DashScopeImageResponse(
            @JsonProperty("request_id") String requestId,
            DashScopeImageOutput output,
            Map<String, Object> usage
    ) {
    }

    private record DashScopeImageOutput(List<DashScopeImageChoice> choices) {
    }

    private record DashScopeImageChoice(DashScopeImageMessage message) {
    }

    private record DashScopeImageMessage(List<DashScopeImageContent> content) {
    }

    private record DashScopeImageContent(String image) {
    }
}
