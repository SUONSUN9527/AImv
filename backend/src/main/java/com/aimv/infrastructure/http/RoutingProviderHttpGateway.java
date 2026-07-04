package com.aimv.infrastructure.http;

import com.aimv.domain.provider.ProviderHttpGateway;
import com.aimv.domain.provider.ProviderHttpRequest;
import com.aimv.domain.provider.ProviderHttpResponse;
import com.aimv.domain.provider.ProviderSecretResolver;
import com.aimv.shared.error.BusinessException;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class RoutingProviderHttpGateway implements ProviderHttpGateway {

    private static final String FIXTURE_PROVIDER = "fixture-free";

    private final RestTemplate restTemplate;
    private final String adapterBaseUrl;
    private final ProviderSecretResolver providerSecretResolver;
    private final DashScopeTextProviderHttpGateway dashScopeTextProviderHttpGateway;
    private final DashScopeImageProviderHttpGateway dashScopeImageProviderHttpGateway;
    private final DashScopeEmbeddingProviderHttpGateway dashScopeEmbeddingProviderHttpGateway;
    private final DashScopeRerankProviderHttpGateway dashScopeRerankProviderHttpGateway;
    private final DashScopeVideoProviderHttpGateway dashScopeVideoProviderHttpGateway;
    private final PollinationsTextProviderHttpGateway pollinationsTextProviderHttpGateway;
    private final PollinationsImageProviderHttpGateway pollinationsImageProviderHttpGateway;
    private final String pollinationsToken;
    private final FixtureProviderHttpGateway fixtureProviderHttpGateway = new FixtureProviderHttpGateway();

    @Autowired
    public RoutingProviderHttpGateway(RestTemplateBuilder restTemplateBuilder,
            @Value("${aimv.provider-adapter.base-url:}") String adapterBaseUrl,
            @Value("${aimv.dashscope.api-key:}") String dashScopeApiKey,
            @Value("${aimv.dashscope.openai-base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
            String dashScopeOpenAiBaseUrl,
            @Value("${aimv.dashscope.llm-model:qwen-plus}") String dashScopeLlmModel,
            @Value("${aimv.dashscope.api-base-url:https://dashscope.aliyuncs.com/api/v1}")
            String dashScopeApiBaseUrl,
            @Value("${aimv.dashscope.image-model:wan2.6-t2i}") String dashScopeImageModel,
            @Value("${aimv.dashscope.image-size:720*1280}") String dashScopeImageSize,
            @Value("${aimv.dashscope.embedding-model:text-embedding-v4}") String dashScopeEmbeddingModel,
            @Value("${aimv.dashscope.embedding-dimensions:1024}") int dashScopeEmbeddingDimensions,
            @Value("${aimv.dashscope.rerank-model:qwen3-rerank}") String dashScopeRerankModel,
            @Value("${aimv.dashscope.rerank-top-n:2}") int dashScopeRerankTopN,
            @Value("${aimv.dashscope.video-model:wan2.7-t2v}") String dashScopeVideoModel,
            @Value("${aimv.dashscope.video-resolution:720P}") String dashScopeVideoResolution,
            @Value("${aimv.dashscope.video-ratio:9:16}") String dashScopeVideoRatio,
            @Value("${aimv.dashscope.video-duration-seconds:10}") int dashScopeVideoDurationSeconds,
            @Value("${aimv.dashscope.video-poll-attempts:60}") int dashScopeVideoPollAttempts,
            @Value("${aimv.dashscope.video-poll-interval:10s}") Duration dashScopeVideoPollInterval,
            @Value("${aimv.dashscope.video-native-voice:false}") boolean dashScopeVideoNativeVoice,
            @Value("${aimv.video-voice.enabled:true}") boolean videoVoiceEnabled,
            @Value("${aimv.dashscope.tts-model:qwen-tts}") String dashScopeTtsModel,
            @Value("${aimv.dashscope.tts-voice:Chelsie}") String dashScopeTtsVoice,
            @Value("${aimv.media.ffmpeg-path:ffmpeg}") String ffmpegPath,
            @Value("${aimv.media.storage-dir:./storage/media}") String mediaStorageDir,
            @Value("${aimv.media.public-base-url:http://127.0.0.1:8081}") String mediaPublicBaseUrl,
            @Value("${aimv.video-voice.shots:0}") int videoShots,
            @Value("${aimv.video-voice.per-shot-seconds:5}") int videoPerShotSeconds,
            @Value("${aimv.pollinations.token:}") String pollinationsToken,
            @Value("${aimv.pollinations.text-base-url:https://text.pollinations.ai}")
            String pollinationsTextBaseUrl,
            @Value("${aimv.pollinations.text-model:openai}") String pollinationsTextModel,
            @Value("${aimv.pollinations.image-base-url:https://image.pollinations.ai}")
            String pollinationsImageBaseUrl,
            @Value("${aimv.pollinations.image-model:flux}") String pollinationsImageModel,
            @Value("${aimv.pollinations.image-width:720}") int pollinationsImageWidth,
            @Value("${aimv.pollinations.image-height:1280}") int pollinationsImageHeight,
            ProviderSecretResolver providerSecretResolver) {
        this(restTemplateBuilder.build(), adapterBaseUrl,
            new DashScopeTextProviderOptions(dashScopeApiKey, dashScopeOpenAiBaseUrl, dashScopeLlmModel),
            new DashScopeImageProviderOptions(dashScopeApiKey, dashScopeApiBaseUrl, dashScopeImageModel,
                dashScopeImageSize),
            new DashScopeRagProviderOptions(dashScopeApiKey, dashScopeOpenAiBaseUrl,
                dashScopeEmbeddingModel, dashScopeEmbeddingDimensions, dashScopeRerankModel,
                dashScopeRerankTopN),
            new DashScopeVideoProviderOptions(dashScopeApiKey, dashScopeApiBaseUrl, dashScopeVideoModel,
                dashScopeVideoResolution, dashScopeVideoRatio, dashScopeVideoDurationSeconds,
                dashScopeVideoPollAttempts, dashScopeVideoPollInterval, dashScopeVideoNativeVoice),
            providerSecretResolver,
            new PollinationsProviderOptions(pollinationsToken, pollinationsTextBaseUrl, pollinationsTextModel,
                pollinationsImageBaseUrl, pollinationsImageModel, pollinationsImageWidth,
                pollinationsImageHeight),
            new VideoVoiceOptions(videoVoiceEnabled, dashScopeTtsModel, dashScopeTtsVoice, ffmpegPath,
                mediaStorageDir, mediaPublicBaseUrl, dashScopeVideoDurationSeconds, videoShots,
                videoPerShotSeconds));
    }

    RoutingProviderHttpGateway(RestTemplate restTemplate, String adapterBaseUrl) {
        this(restTemplate, adapterBaseUrl, DashScopeTextProviderOptions.disabled(),
            DashScopeImageProviderOptions.disabled());
    }

    RoutingProviderHttpGateway(RestTemplate restTemplate, String adapterBaseUrl,
            DashScopeTextProviderOptions dashScopeOptions) {
        this(restTemplate, adapterBaseUrl, dashScopeOptions, DashScopeImageProviderOptions.disabled());
    }

    RoutingProviderHttpGateway(RestTemplate restTemplate, String adapterBaseUrl,
            DashScopeTextProviderOptions dashScopeOptions, DashScopeImageProviderOptions imageOptions) {
        this(restTemplate, adapterBaseUrl, dashScopeOptions, imageOptions,
            DashScopeRagProviderOptions.disabled());
    }

    RoutingProviderHttpGateway(RestTemplate restTemplate, String adapterBaseUrl,
            DashScopeTextProviderOptions dashScopeOptions, DashScopeImageProviderOptions imageOptions,
            DashScopeRagProviderOptions ragOptions) {
        this(restTemplate, adapterBaseUrl, dashScopeOptions, imageOptions, ragOptions,
            DashScopeVideoProviderOptions.disabled());
    }

    RoutingProviderHttpGateway(RestTemplate restTemplate, String adapterBaseUrl,
            DashScopeTextProviderOptions dashScopeOptions, DashScopeImageProviderOptions imageOptions,
            DashScopeRagProviderOptions ragOptions, DashScopeVideoProviderOptions videoOptions) {
        this(restTemplate, adapterBaseUrl, dashScopeOptions, imageOptions, ragOptions, videoOptions,
            ProviderSecretResolver.empty());
    }

    RoutingProviderHttpGateway(RestTemplate restTemplate, String adapterBaseUrl,
            DashScopeTextProviderOptions dashScopeOptions, DashScopeImageProviderOptions imageOptions,
            DashScopeRagProviderOptions ragOptions, DashScopeVideoProviderOptions videoOptions,
            ProviderSecretResolver providerSecretResolver) {
        this(restTemplate, adapterBaseUrl, dashScopeOptions, imageOptions, ragOptions, videoOptions,
            providerSecretResolver, PollinationsProviderOptions.defaults());
    }

    RoutingProviderHttpGateway(RestTemplate restTemplate, String adapterBaseUrl,
            DashScopeTextProviderOptions dashScopeOptions, DashScopeImageProviderOptions imageOptions,
            DashScopeRagProviderOptions ragOptions, DashScopeVideoProviderOptions videoOptions,
            ProviderSecretResolver providerSecretResolver, PollinationsProviderOptions pollinationsOptions) {
        this(restTemplate, adapterBaseUrl, dashScopeOptions, imageOptions, ragOptions, videoOptions,
            providerSecretResolver, pollinationsOptions, VideoVoiceOptions.disabled());
    }

    RoutingProviderHttpGateway(RestTemplate restTemplate, String adapterBaseUrl,
            DashScopeTextProviderOptions dashScopeOptions, DashScopeImageProviderOptions imageOptions,
            DashScopeRagProviderOptions ragOptions, DashScopeVideoProviderOptions videoOptions,
            ProviderSecretResolver providerSecretResolver, PollinationsProviderOptions pollinationsOptions,
            VideoVoiceOptions videoVoiceOptions) {
        this.restTemplate = restTemplate;
        this.adapterBaseUrl = adapterBaseUrl == null ? "" : adapterBaseUrl.strip();
        this.providerSecretResolver = providerSecretResolver == null ? ProviderSecretResolver.empty()
            : providerSecretResolver;
        this.dashScopeTextProviderHttpGateway = new DashScopeTextProviderHttpGateway(restTemplate,
            dashScopeOptions);
        this.dashScopeImageProviderHttpGateway = new DashScopeImageProviderHttpGateway(restTemplate,
            imageOptions, videoVoiceOptions.storageDirValue(), videoVoiceOptions.publicBaseUrlValue());
        this.dashScopeEmbeddingProviderHttpGateway = new DashScopeEmbeddingProviderHttpGateway(restTemplate,
            ragOptions);
        this.dashScopeRerankProviderHttpGateway = new DashScopeRerankProviderHttpGateway(restTemplate,
            ragOptions);
        PollinationsProviderOptions pollinations = pollinationsOptions == null
            ? PollinationsProviderOptions.defaults() : pollinationsOptions;
        this.dashScopeVideoProviderHttpGateway = new DashScopeVideoProviderHttpGateway(restTemplate,
            videoOptions, videoVoiceOptions == null ? VideoVoiceOptions.disabled() : videoVoiceOptions,
            pollinations);
        this.pollinationsTextProviderHttpGateway = new PollinationsTextProviderHttpGateway(restTemplate,
            pollinations);
        this.pollinationsImageProviderHttpGateway = new PollinationsImageProviderHttpGateway(restTemplate,
            pollinations);
        this.pollinationsToken = pollinations.tokenValue();
    }

    @Override
    public ProviderHttpResponse invoke(ProviderHttpRequest request) {
        if (FIXTURE_PROVIDER.equals(request.provider())) {
            return fixtureProviderHttpGateway.invoke(request);
        }
        if (dashScopeTextProviderHttpGateway.supportsCapability(request)) {
            Optional<String> apiKey = dashScopeApiKey(request,
                dashScopeTextProviderHttpGateway.configuredApiKey());
            if (apiKey.isPresent()) {
                return dashScopeTextProviderHttpGateway.invoke(request, apiKey.get());
            }
        }
        if (dashScopeEmbeddingProviderHttpGateway.supportsCapability(request)) {
            Optional<String> apiKey = dashScopeApiKey(request,
                dashScopeEmbeddingProviderHttpGateway.configuredApiKey());
            if (apiKey.isPresent()) {
                return dashScopeEmbeddingProviderHttpGateway.invoke(request, apiKey.get());
            }
        }
        if (dashScopeRerankProviderHttpGateway.supportsCapability(request)) {
            Optional<String> apiKey = dashScopeApiKey(request,
                dashScopeRerankProviderHttpGateway.configuredApiKey());
            if (apiKey.isPresent()) {
                return dashScopeRerankProviderHttpGateway.invoke(request, apiKey.get());
            }
        }
        if (dashScopeImageProviderHttpGateway.supportsCapability(request)) {
            Optional<String> apiKey = dashScopeApiKey(request,
                dashScopeImageProviderHttpGateway.configuredApiKey());
            if (apiKey.isPresent()) {
                return dashScopeImageProviderHttpGateway.invoke(request, apiKey.get());
            }
        }
        if (dashScopeVideoProviderHttpGateway.supportsCapability(request)) {
            Optional<String> apiKey = dashScopeApiKey(request,
                dashScopeVideoProviderHttpGateway.configuredApiKey());
            if (apiKey.isPresent()) {
                return dashScopeVideoProviderHttpGateway.invoke(request, apiKey.get());
            }
        }
        if (pollinationsTextProviderHttpGateway.supportsCapability(request)) {
            return pollinationsTextProviderHttpGateway.invoke(request, pollinationsToken(request));
        }
        if (pollinationsImageProviderHttpGateway.supportsCapability(request)) {
            return pollinationsImageProviderHttpGateway.invoke(request, pollinationsToken(request));
        }
        if (adapterBaseUrl.isBlank()) {
            throw new BusinessException(HttpStatus.CONFLICT, "PROVIDER_ADAPTER_NOT_CONFIGURED",
                "非 fixture provider 需要配置云端 HTTP adapter 地址");
        }
        ProviderHttpResponse response = callAdapter(request);
        validate(response);
        return response;
    }

    private Optional<String> dashScopeApiKey(ProviderHttpRequest request, String configuredApiKey) {
        return providerSecretResolver.resolve(request.apiKeyId())
            .filter(secret -> !secret.isBlank())
            .or(() -> configuredApiKey == null || configuredApiKey.isBlank()
                ? Optional.empty() : Optional.of(configuredApiKey));
    }

    /**
     * Pollinations 免费匿名层无需 key；若用户为该能力条目录入了 token 则解密使用，否则退回
     * 服务级配置的 token（也可为空，走匿名免费）。
     */
    private String pollinationsToken(ProviderHttpRequest request) {
        return providerSecretResolver.resolve(request.apiKeyId())
            .filter(secret -> !secret.isBlank())
            .orElse(pollinationsToken);
    }

    private ProviderHttpResponse callAdapter(ProviderHttpRequest request) {
        try {
            return restTemplate.postForObject(adapterBaseUrl + "/v1/provider-jobs", request,
                ProviderHttpResponse.class);
        } catch (RestClientException exception) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "PROVIDER_ADAPTER_UNAVAILABLE",
                "云端 HTTP adapter 调用失败");
        }
    }

    private void validate(ProviderHttpResponse response) {
        if (response == null) {
            throw invalid("response body");
        }
        if (isBlank(response.providerJobId())) {
            throw invalid("providerJobId");
        }
        if (isBlank(response.status())) {
            throw invalid("status");
        }
        if (isBlank(response.freeQuotaSnapshot())) {
            throw invalid("freeQuotaSnapshot");
        }
    }

    private BusinessException invalid(String fieldName) {
        return new BusinessException(HttpStatus.BAD_GATEWAY, "PROVIDER_ADAPTER_RESPONSE_INVALID",
            "云端 HTTP adapter 响应缺少必填字段: " + fieldName);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
