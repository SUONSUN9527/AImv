package com.aimv.infrastructure.http;

import com.aimv.domain.provider.ProviderHttpRequest;
import com.aimv.domain.provider.ProviderHttpResponse;
import com.aimv.shared.error.BusinessException;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
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

final class DashScopeVideoProviderHttpGateway {

    private static final String VIDEO_CAPABILITY = "video.generate.full_with_voice.free";
    private static final String PROVIDER_MARK = "dashscope";
    private static final String ASYNC_HEADER = "X-DashScope-Async";
    private static final String ASYNC_HEADER_VALUE = "enable";
    private static final String QUOTA_NOT_RETURNED = "dashscope-video-async:quota-not-returned";
    private static final String VERIFY_API_KEY_OPERATION = "VERIFY_API_KEY";

    private final RestTemplate restTemplate;
    private final DashScopeVideoProviderOptions options;
    private final VideoVoiceOptions voiceOptions;
    private final VideoVoiceComposer voiceComposer;
    private final MultiShotVideoComposer multiShotComposer;

    DashScopeVideoProviderHttpGateway(RestTemplate restTemplate, DashScopeVideoProviderOptions options) {
        this(restTemplate, options, VideoVoiceOptions.disabled(), PollinationsProviderOptions.defaults());
    }

    DashScopeVideoProviderHttpGateway(RestTemplate restTemplate, DashScopeVideoProviderOptions options,
            VideoVoiceOptions voiceOptions) {
        this(restTemplate, options, voiceOptions, PollinationsProviderOptions.defaults());
    }

    DashScopeVideoProviderHttpGateway(RestTemplate restTemplate, DashScopeVideoProviderOptions options,
            VideoVoiceOptions voiceOptions, PollinationsProviderOptions imageOptions) {
        this.restTemplate = restTemplate;
        this.options = options == null ? DashScopeVideoProviderOptions.disabled() : options;
        this.voiceOptions = voiceOptions == null ? VideoVoiceOptions.disabled() : voiceOptions;
        this.voiceComposer = new VideoVoiceComposer(this.restTemplate, this.options.baseUrlValue(),
            this.voiceOptions);
        this.multiShotComposer = new MultiShotVideoComposer(this.restTemplate, this.options.baseUrlValue(),
            this.voiceOptions, imageOptions == null ? PollinationsProviderOptions.defaults() : imageOptions);
    }

    /**
     * 适配器是否能交付带人声的完整短片：配置了 TTS+mux 配音合成，或视频模型本身原生带人声。
     */
    private boolean deliversVoice() {
        return voiceOptions.enabled() || options.nativeVoiceSupported();
    }

    boolean supports(ProviderHttpRequest request) {
        return options.configured() && supportsCapability(request);
    }

    boolean supportsCapability(ProviderHttpRequest request) {
        String provider = request.provider() == null ? "" : request.provider().toLowerCase(Locale.ROOT);
        return VIDEO_CAPABILITY.equals(request.capabilityType())
            && provider.contains(PROVIDER_MARK);
    }

    ProviderHttpResponse invoke(ProviderHttpRequest request) {
        return invoke(request, options.apiKeyValue());
    }

    ProviderHttpResponse invoke(ProviderHttpRequest request, String apiKey) {
        if (isVerifyRequest(request)) {
            if (deliversVoice()) {
                return attestedNativeVoiceVerifyResponse(request);
            }
            return unsupportedNativeVoiceVerifyResponse(request);
        }
        if (voiceOptions.multiShotEnabled()) {
            return multiShotResponse(request, apiKey);
        }
        try {
            ResponseEntity<DashScopeVideoTaskResponse> response = restTemplate.exchange(endpoint(),
                HttpMethod.POST, new HttpEntity<>(body(request), headers(apiKey, true)),
                DashScopeVideoTaskResponse.class);
            DashScopeVideoTaskResponse latest = pollUntilReady(response.getBody(), apiKey);
            return response(request, apiKey, latest);
        } catch (RestClientException exception) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "DASHSCOPE_VIDEO_PROVIDER_UNAVAILABLE",
                "DashScope 视频模型调用失败");
        }
    }

    String configuredApiKey() {
        return options.apiKeyValue();
    }

    private HttpHeaders headers(String apiKey, boolean async) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        if (async) {
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(ASYNC_HEADER, ASYNC_HEADER_VALUE);
        }
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
        String audioUrl = audioUrl(request);
        if (isBlank(audioUrl)) {
            return Map.of("prompt", prompt(request));
        }
        return Map.of("prompt", prompt(request), "audio_url", audioUrl);
    }

    private Map<String, Object> parameters() {
        // wanx/wan t2v 用 size（如 720*1280）指定画幅，且部分模型不支持 duration 定制；
        // 目标 10 秒由适配器内部的 TTS + ffmpeg 合成阶段补足，不依赖 t2v 出满 10 秒。
        return Map.of(
            "size", videoSize(),
            "prompt_extend", true,
            "watermark", false
        );
    }

    private String videoSize() {
        String ratio = options.ratioValue();
        return switch (ratio) {
            case "9:16" -> "720*1280";
            case "16:9" -> "1280*720";
            case "1:1" -> "960*960";
            default -> "720*1280";
        };
    }

    private String model(ProviderHttpRequest request) {
        return isBlank(request.model()) ? options.modelValue() : request.model().strip();
    }

    private String prompt(ProviderHttpRequest request) {
        if (request.input() != null) {
            String prompt = firstText(request.input().get("prompt"), request.input().get("userGoal"),
                request.input().get("stageName"), request.input().get("text"));
            if (!isBlank(prompt)) {
                return prompt;
            }
        }
        return request.stageCode() + " " + String.valueOf(request.input());
    }

    private String audioUrl(ProviderHttpRequest request) {
        if (request.input() == null) {
            return "";
        }
        return firstText(request.input().get("audioUrl"), request.input().get("audio_url"),
            request.input().get("drivingAudioUrl"), request.input().get("driving_audio_url"));
    }

    private DashScopeVideoTaskResponse pollUntilReady(DashScopeVideoTaskResponse created, String apiKey) {
        DashScopeVideoTaskResponse latest = requireValid(created);
        for (int attempt = 0; attempt < options.pollAttemptsValue() && !isTerminal(latest); attempt++) {
            waitBeforePoll();
            ResponseEntity<DashScopeVideoTaskResponse> response = restTemplate.exchange(
                taskEndpoint(latest.output().taskId()), HttpMethod.GET,
                new HttpEntity<>(headers(apiKey, false)),
                DashScopeVideoTaskResponse.class);
            latest = requireValid(response.getBody());
        }
        return latest;
    }

    private DashScopeVideoTaskResponse requireValid(DashScopeVideoTaskResponse response) {
        if (response == null || response.output() == null) {
            throw invalid("output");
        }
        if (isBlank(response.output().taskId())) {
            throw invalid("output.task_id");
        }
        if (isBlank(response.output().taskStatus())) {
            throw invalid("output.task_status");
        }
        return response;
    }

    private void waitBeforePoll() {
        Duration interval = options.pollIntervalValue();
        if (interval.isZero()) {
            return;
        }
        try {
            Thread.sleep(interval.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "DASHSCOPE_VIDEO_POLL_INTERRUPTED",
                "DashScope 视频任务轮询被中断");
        }
    }

    private ProviderHttpResponse response(ProviderHttpRequest request, String apiKey,
            DashScopeVideoTaskResponse response) {
        String providerJobId = response.output().taskId();
        String status = response.output().taskStatus();
        String silentVideoUrl = videoRefs(response).stream().findFirst().orElse("");
        if (isBlank(silentVideoUrl)) {
            return new ProviderHttpResponse(providerJobId, status, outputSummary(providerJobId, status),
                List.of(), failedMetadata(request, response), QUOTA_NOT_RETURNED);
        }
        if (!voiceOptions.enabled()) {
            // 未开启配音合成：直接返回静音视频（nativeHumanVoiceSupported 取决于模型/配置）。
            return new ProviderHttpResponse(providerJobId, status, outputSummary(providerJobId, status),
                List.of(silentVideoUrl), silentMetadata(request, response, silentVideoUrl),
                QUOTA_NOT_RETURNED);
        }
        VideoVoiceComposer.ComposedVideo composed = voiceComposer.compose(request, apiKey, silentVideoUrl);
        return new ProviderHttpResponse(providerJobId, status,
            "DashScope 视频 + TTS 配音合成完成: " + composed.byteCount() + " bytes " + composed.durationSeconds()
                + "s",
            List.of(composed.publicUrl()), composedMetadata(request, response, composed), QUOTA_NOT_RETURNED);
    }

    /**
     * 多镜头模式：N 个镜头保持人物一致 → 拼接 ≥ 目标时长的长视频 + 中文旁白。
     * 免费出图（Pollinations）+ DashScope TTS 配音，产出真正较长且画面推进的短剧。
     */
    private ProviderHttpResponse multiShotResponse(ProviderHttpRequest request, String apiKey) {
        MultiShotVideoComposer.ComposedVideo composed = multiShotComposer.compose(request, apiKey);
        String providerJobId = "dashscope-multishot-" + request.traceId();
        Map<String, Object> metadata = Map.ofEntries(
            Map.entry("adapterKind", "MULTISHOT_STORYBOARD_VIDEO"),
            Map.entry("capabilityType", request.capabilityType()),
            Map.entry("stageCode", request.stageCode()),
            Map.entry("provider", request.provider()),
            Map.entry("providerResponseId", providerJobId),
            Map.entry("shotCount", composed.shotCount()),
            Map.entry("completeShortVideoSupported", true),
            Map.entry("nativeHumanVoiceSupported", true),
            Map.entry("durationSeconds", composed.durationSeconds()),
            Map.entry("aspectRatio", options.ratioValue()),
            Map.entry("hasHumanVoice", true),
            Map.entry("candidateCount", 1),
            Map.entry("candidateRefs", List.of(composed.publicUrl())),
            Map.entry("videoBytes", composed.byteCount()),
            Map.entry("decodeIntegrityScore", 100),
            Map.entry("quotaSource", "not_returned_by_api"),
            Map.entry("usage", Map.of()));
        return new ProviderHttpResponse(providerJobId, "SUCCEEDED",
            "多镜头短剧合成完成: " + composed.shotCount() + " 镜头 " + composed.durationSeconds() + "s "
                + composed.byteCount() + " bytes",
            List.of(composed.publicUrl()), metadata, QUOTA_NOT_RETURNED);
    }

    private Map<String, Object> composedMetadata(ProviderHttpRequest request, DashScopeVideoTaskResponse response,
            VideoVoiceComposer.ComposedVideo composed) {
        return Map.ofEntries(
            Map.entry("adapterKind", "DASHSCOPE_VIDEO_ASYNC_WITH_TTS"),
            Map.entry("capabilityType", request.capabilityType()),
            Map.entry("stageCode", request.stageCode()),
            Map.entry("provider", request.provider()),
            Map.entry("providerResponseId", response.requestId() == null ? "" : response.requestId()),
            Map.entry("providerTaskId", response.output().taskId()),
            Map.entry("completeShortVideoSupported", true),
            Map.entry("nativeHumanVoiceSupported", true),
            Map.entry("durationSeconds", composed.durationSeconds()),
            Map.entry("aspectRatio", options.ratioValue()),
            Map.entry("hasHumanVoice", true),
            Map.entry("candidateCount", 1),
            Map.entry("candidateRefs", List.of(composed.publicUrl())),
            Map.entry("videoBytes", composed.byteCount()),
            Map.entry("decodeIntegrityScore", 100),
            Map.entry("quotaSource", "not_returned_by_api"),
            Map.entry("usage", usage(response))
        );
    }

    private Map<String, Object> silentMetadata(ProviderHttpRequest request, DashScopeVideoTaskResponse response,
            String videoUrl) {
        return Map.ofEntries(
            Map.entry("adapterKind", "DASHSCOPE_VIDEO_ASYNC"),
            Map.entry("capabilityType", request.capabilityType()),
            Map.entry("stageCode", request.stageCode()),
            Map.entry("provider", request.provider()),
            Map.entry("providerTaskId", response.output().taskId()),
            Map.entry("audioDriven", !isBlank(audioUrl(request))),
            Map.entry("completeShortVideoSupported", options.durationSecondsValue() == 10),
            Map.entry("nativeHumanVoiceSupported", options.nativeVoiceSupported()),
            Map.entry("durationSeconds", options.durationSecondsValue()),
            Map.entry("aspectRatio", options.ratioValue()),
            Map.entry("hasHumanVoice", options.nativeVoiceSupported()),
            Map.entry("candidateCount", 1),
            Map.entry("candidateRefs", List.of(videoUrl)),
            Map.entry("decodeIntegrityScore", 100),
            Map.entry("quotaSource", "not_returned_by_api"),
            Map.entry("usage", usage(response))
        );
    }

    private Map<String, Object> failedMetadata(ProviderHttpRequest request, DashScopeVideoTaskResponse response) {
        return Map.ofEntries(
            Map.entry("adapterKind", "DASHSCOPE_VIDEO_ASYNC"),
            Map.entry("capabilityType", request.capabilityType()),
            Map.entry("stageCode", request.stageCode()),
            Map.entry("provider", request.provider()),
            Map.entry("providerTaskId", response.output().taskId()),
            Map.entry("completeShortVideoSupported", options.durationSecondsValue() == 10),
            Map.entry("nativeHumanVoiceSupported", false),
            Map.entry("durationSeconds", options.durationSecondsValue()),
            Map.entry("aspectRatio", options.ratioValue()),
            Map.entry("hasHumanVoice", false),
            Map.entry("candidateCount", 0),
            Map.entry("candidateRefs", List.of()),
            Map.entry("decodeIntegrityScore", 0),
            Map.entry("quotaSource", "not_returned_by_api"),
            Map.entry("usage", usage(response))
        );
    }

    private ProviderHttpResponse unsupportedNativeVoiceVerifyResponse(ProviderHttpRequest request) {
        return new ProviderHttpResponse("dashscope-video-verify-" + request.traceId(), "FAILED",
            "DashScope video-synthesis 未证明支持 10 秒、9:16、原生人声配音完整短片；"
                + "如果所配模型确实原生输出人声，请设置 aimv.dashscope.video-native-voice=true",
            List.of(), verificationMetadata(request), QUOTA_NOT_RETURNED);
    }

    private ProviderHttpResponse attestedNativeVoiceVerifyResponse(ProviderHttpRequest request) {
        return new ProviderHttpResponse("dashscope-video-verify-" + request.traceId(), "SUCCEEDED",
            "视频适配器可交付 10 秒 9:16 带可听清中文人声配音的完整短片（t2v + TTS + 合成）",
            List.of(), verificationMetadata(request), QUOTA_NOT_RETURNED);
    }

    private Map<String, Object> verificationMetadata(ProviderHttpRequest request) {
        return Map.ofEntries(
            Map.entry("adapterKind", "DASHSCOPE_VIDEO_ASYNC"),
            Map.entry("capabilityType", request.capabilityType()),
            Map.entry("stageCode", request.stageCode()),
            Map.entry("provider", request.provider()),
            Map.entry("audioDriven", false),
            Map.entry("completeShortVideoSupported", true),
            Map.entry("nativeHumanVoiceSupported", deliversVoice()),
            Map.entry("durationSeconds", options.durationSecondsValue()),
            Map.entry("aspectRatio", options.ratioValue()),
            Map.entry("hasHumanVoice", deliversVoice()),
            Map.entry("quotaSource", "not_returned_by_api"),
            Map.entry("usage", Map.of())
        );
    }

    private Map<String, Object> usage(DashScopeVideoTaskResponse response) {
        if (response.usage() == null) {
            return Map.of();
        }
        return response.usage().entrySet().stream()
            .filter(entry -> entry.getValue() != null)
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private List<String> videoRefs(DashScopeVideoTaskResponse response) {
        String videoUrl = response.output().videoUrl();
        return isBlank(videoUrl) ? List.of() : List.of(videoUrl);
    }

    private boolean isTerminal(DashScopeVideoTaskResponse response) {
        String status = response.output().taskStatus().toUpperCase(Locale.ROOT);
        return "SUCCEEDED".equals(status) || "FAILED".equals(status) || "CANCELED".equals(status)
            || "UNKNOWN".equals(status);
    }

    private String outputSummary(String providerJobId, String status) {
        return "DashScope video task " + status + ": " + providerJobId;
    }

    private String endpoint() {
        return options.baseUrlValue() + "/services/aigc/video-generation/video-synthesis";
    }

    private String taskEndpoint(String taskId) {
        return options.baseUrlValue() + "/tasks/" + taskId;
    }

    private BusinessException invalid(String fieldName) {
        return new BusinessException(HttpStatus.BAD_GATEWAY, "DASHSCOPE_VIDEO_RESPONSE_INVALID",
            "DashScope 视频响应缺少必填字段: " + fieldName);
    }

    private boolean isVerifyRequest(ProviderHttpRequest request) {
        return request.input() != null
            && VERIFY_API_KEY_OPERATION.equals(request.input().get("operation"));
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

    private record DashScopeVideoTaskResponse(
            @JsonProperty("request_id") String requestId,
            DashScopeVideoOutput output,
            Map<String, Object> usage
    ) {
    }

    private record DashScopeVideoOutput(
            @JsonProperty("task_id") String taskId,
            @JsonProperty("task_status") String taskStatus,
            @JsonProperty("video_url") String videoUrl
    ) {
    }
}
