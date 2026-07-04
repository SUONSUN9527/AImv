package com.aimv.infrastructure.http;

import com.aimv.domain.provider.ProviderHttpRequest;
import com.aimv.domain.provider.ProviderHttpResponse;
import com.aimv.shared.error.BusinessException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Pollinations 免费图片网关（image.generate.free，provider 含 "pollinations"）。
 * GET {imageBaseUrl}/prompt/{prompt}?width&height&model&seed&nologo 直出图片；本网关实际拉取一次
 * 确认返回可解码图片（200 + image/*），并把稳定的生成 URL 作为 artifactRef 返回。
 * seed 由 prompt 派生，保证同一 prompt 复现同一张图。免费匿名层无需 key。
 */
final class PollinationsImageProviderHttpGateway {

    private static final String IMAGE_CAPABILITY = "image.generate.free";
    private static final String PROVIDER_MARK = "pollinations";
    private static final String QUOTA_FREE = "pollinations-free:anonymous-tier";
    private static final String VERIFY_OPERATION = "VERIFY_API_KEY";
    private static final int MIN_IMAGE_BYTES = 512;

    private final RestTemplate restTemplate;
    private final PollinationsProviderOptions options;

    PollinationsImageProviderHttpGateway(RestTemplate restTemplate, PollinationsProviderOptions options) {
        this.restTemplate = restTemplate;
        this.options = options == null ? PollinationsProviderOptions.defaults() : options;
    }

    boolean supportsCapability(ProviderHttpRequest request) {
        String provider = request.provider() == null ? "" : request.provider().toLowerCase(Locale.ROOT);
        return IMAGE_CAPABILITY.equals(request.capabilityType()) && provider.contains(PROVIDER_MARK);
    }

    ProviderHttpResponse invoke(ProviderHttpRequest request, String token) {
        if (isVerify(request)) {
            return verifyResponse(request);
        }
        String prompt = prompt(request);
        int width = options.imageWidthValue();
        int height = options.imageHeightValue();
        long seed = seed(prompt);
        URI uri = imageUri(prompt, width, height, seed, token);
        ImageResult image = fetchImage(uri, token);

        String providerJobId = "pollinations-image-" + seed;
        String aspectRatio = aspectRatio(width, height);
        boolean decodable = image.contentType().startsWith("image/") && image.byteCount() >= MIN_IMAGE_BYTES;
        return new ProviderHttpResponse(providerJobId, "SUCCEEDED",
            "Pollinations 免费图片生成完成: " + image.byteCount() + " bytes " + image.contentType(),
            List.of(uri.toString()),
            metadata(request, providerJobId, uri, width, height, aspectRatio, image, decodable),
            QUOTA_FREE);
    }

    private ImageResult fetchImage(URI uri, String token) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.IMAGE_JPEG, MediaType.IMAGE_PNG, MediaType.ALL));
            if (token != null && !token.isBlank()) {
                headers.setBearerAuth(token.strip());
            }
            ResponseEntity<byte[]> response = restTemplate.exchange(uri, HttpMethod.GET,
                new HttpEntity<>(headers), byte[].class);
            byte[] body = response.getBody();
            if (body == null || body.length < MIN_IMAGE_BYTES) {
                throw new BusinessException(HttpStatus.BAD_GATEWAY, "POLLINATIONS_IMAGE_EMPTY",
                    "Pollinations 图片响应为空或过小");
            }
            MediaType contentType = response.getHeaders().getContentType();
            return new ImageResult(contentType == null ? "application/octet-stream" : contentType.toString(),
                body.length);
        } catch (RestClientException exception) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "POLLINATIONS_IMAGE_PROVIDER_UNAVAILABLE",
                "Pollinations 免费图片模型调用失败");
        }
    }

    private ProviderHttpResponse verifyResponse(ProviderHttpRequest request) {
        return new ProviderHttpResponse("pollinations-image-verify-" + request.traceId(), "SUCCEEDED",
            "Pollinations 免费图片能力可用（匿名免费层）", List.of(),
            Map.of("adapterKind", "POLLINATIONS_IMAGE", "capabilityType", request.capabilityType(),
                "provider", request.provider(), "quotaSource", "free_anonymous_tier"),
            QUOTA_FREE);
    }

    private Map<String, Object> metadata(ProviderHttpRequest request, String providerJobId, URI uri, int width,
            int height, String aspectRatio, ImageResult image, boolean decodable) {
        return Map.ofEntries(
            Map.entry("adapterKind", "POLLINATIONS_IMAGE"),
            Map.entry("capabilityType", request.capabilityType()),
            Map.entry("stageCode", request.stageCode()),
            Map.entry("provider", request.provider()),
            Map.entry("providerResponseId", providerJobId),
            Map.entry("candidateCount", 1),
            Map.entry("candidateRefs", List.of(uri.toString())),
            Map.entry("aspectRatio", aspectRatio),
            Map.entry("width", width),
            Map.entry("height", height),
            Map.entry("mimeType", image.contentType()),
            Map.entry("imageBytes", image.byteCount()),
            Map.entry("artifactIntegrityScore", decodable ? 100 : 0),
            Map.entry("quotaSource", "free_anonymous_tier")
        );
    }

    private URI imageUri(String prompt, int width, int height, long seed, String token) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(options.imageBaseUrlValue())
            .pathSegment("prompt", prompt)
            .queryParam("width", width)
            .queryParam("height", height)
            .queryParam("model", options.imageModelValue())
            .queryParam("seed", seed)
            .queryParam("nologo", true)
            .queryParam("referrer", "aimv");
        if (token != null && !token.isBlank()) {
            builder.queryParam("token", token.strip());
        }
        return builder.build().encode(StandardCharsets.UTF_8).toUri();
    }

    private String prompt(ProviderHttpRequest request) {
        if (request.input() != null) {
            String prompt = firstText(request.input().get("prompt"), request.input().get("positivePrompt"),
                request.input().get("userGoal"), request.input().get("stageName"));
            if (!isBlank(prompt)) {
                return prompt;
            }
        }
        return request.stageCode() + " image";
    }

    private long seed(String prompt) {
        return Math.floorMod(prompt.hashCode(), 1_000_000);
    }

    private String aspectRatio(int width, int height) {
        int divisor = gcd(width, height);
        return (width / divisor) + ":" + (height / divisor);
    }

    private int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }

    private boolean isVerify(ProviderHttpRequest request) {
        return request.input() != null && VERIFY_OPERATION.equals(request.input().get("operation"));
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

    private record ImageResult(String contentType, int byteCount) {
    }
}
