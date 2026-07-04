package com.aimv.interfaces.chain;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aimv.domain.capability.ApiConfigRepository;
import com.aimv.domain.capability.ApiCredential;
import com.aimv.domain.capability.ApiKeyStatus;
import com.aimv.domain.capability.FreeModelGateStatus;
import com.aimv.domain.provider.ProviderHttpGateway;
import com.aimv.domain.provider.ProviderHttpRequest;
import com.aimv.domain.provider.ProviderHttpResponse;
import com.aimv.domain.shared.ChainType;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ChainRunQualityGateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiConfigRepository apiConfigRepository;

    @MockitoBean
    private ProviderHttpGateway providerHttpGateway;

    @Test
    void retriesImageGenerationWhenFirstImageReviewFailsAndThenDelivers() throws Exception {
        saveSelectedCredentials(ChainType.IMAGE, "image.generate.free");
        AtomicInteger imageReviewCalls = new AtomicInteger();
        when(providerHttpGateway.invoke(any())).thenAnswer(imageReviewFailsOnceThenPasses(imageReviewCalls));
        String projectId = createProject();

        mockMvc.perform(post("/api/projects/{projectId}/image-chain-runs", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userGoal\":\"生成一张都市悬疑短剧封面\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
            .andExpect(jsonPath("$.data.currentStageCode").value("I60"))
            .andExpect(jsonPath("$.data.blockingReason").doesNotExist())
            .andExpect(jsonPath("$.data.stageRuns.length()").value(9))
            .andExpect(jsonPath("$.data.stageRuns[4].stageCode").value("I40"))
            .andExpect(jsonPath("$.data.stageRuns[5].stageCode").value("I50"))
            .andExpect(jsonPath("$.data.stageRuns[5].status").value("FAILED"))
            .andExpect(jsonPath("$.data.stageRuns[5].reviewReport.passed").value(false))
            .andExpect(jsonPath("$.data.stageRuns[5].reviewReport.overallScore").value(70))
            .andExpect(jsonPath("$.data.stageRuns[6].stageCode").value("I40"))
            .andExpect(jsonPath("$.data.stageRuns[7].stageCode").value("I50"))
            .andExpect(jsonPath("$.data.stageRuns[7].status").value("SUCCEEDED"))
            .andExpect(jsonPath("$.data.stageRuns[7].reviewReport.passed").value(true))
            .andExpect(jsonPath("$.data.stageRuns[7].reviewReport.overallScore").value(96))
            .andExpect(jsonPath("$.data.stageRuns[8].stageCode").value("I60"))
            .andExpect(jsonPath("$.data.artifacts[*].artifactKind",
                hasItems("ImageCandidateAssets", "FinalImageArtifact", "ImageReviewReport")))
            .andExpect(jsonPath("$.data.artifacts[?(@.artifactKind == 'ImageCandidateAssets')].metadata.candidateCount",
                hasItem(4)))
            .andExpect(content().string(not(containsString("direct-secret-1234"))));
    }

    @Test
    void returnsVideoToWaitingUserWhenVoiceReviewFailsTwiceAndNoAlternativeProviderExists() throws Exception {
        saveSelectedCredentials(ChainType.VIDEO, "video.generate.full_with_voice.free");
        when(providerHttpGateway.invoke(any())).thenAnswer(videoReviewAlwaysFails());
        String projectId = createProject();

        mockMvc.perform(post("/api/projects/{projectId}/video-chain-runs", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userGoal\":\"生成10秒9:16带人声配音的AI短剧\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("WAITING_USER"))
            .andExpect(jsonPath("$.data.currentStageCode").value("V30"))
            .andExpect(jsonPath("$.data.blockingReason").value(containsString("连续两次质量评审失败")))
            .andExpect(jsonPath("$.data.blockingReason").value(containsString("没有其他完整视频 provider")))
            .andExpect(jsonPath("$.data.stageRuns.length()").value(9))
            .andExpect(jsonPath("$.data.stageRuns[4].stageCode").value("V40"))
            .andExpect(jsonPath("$.data.stageRuns[5].stageCode").value("V50"))
            .andExpect(jsonPath("$.data.stageRuns[5].status").value("FAILED"))
            .andExpect(jsonPath("$.data.stageRuns[5].reviewReport.passed").value(false))
            .andExpect(jsonPath("$.data.stageRuns[5].reviewReport.overallScore").value(88))
            .andExpect(jsonPath("$.data.stageRuns[6].stageCode").value("V40"))
            .andExpect(jsonPath("$.data.stageRuns[7].stageCode").value("V50"))
            .andExpect(jsonPath("$.data.stageRuns[7].status").value("FAILED"))
            .andExpect(jsonPath("$.data.stageRuns[8].stageCode").value("V30"))
            .andExpect(jsonPath("$.data.stageRuns[8].status").value("WAITING_USER"))
            .andExpect(jsonPath("$.data.stageRuns[8].reviewReport.passed").value(false))
            .andExpect(jsonPath("$.data.artifacts.length()").value(0))
            .andExpect(content().string(not(containsString("direct-secret-1234"))));
    }

    @Test
    void autoSelectsAlternativeVideoProviderAfterRepeatedQualityFailure() throws Exception {
        saveSelectedCredentials(ChainType.VIDEO, "video.generate.full_with_voice.free");
        saveCredential(ChainType.VIDEO, "video.generate.full_with_voice.free", "provider-b", false,
            "direct-VIDEO-video.generate.full_with_voice.free-b");
        AtomicBoolean providerBGeneratedVideo = new AtomicBoolean();
        when(providerHttpGateway.invoke(any())).thenAnswer(videoProviderBFixesReview(providerBGeneratedVideo));
        String projectId = createProject();

        String response = mockMvc.perform(post("/api/projects/{projectId}/video-chain-runs", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userGoal\":\"生成10秒9:16带人声配音的AI短剧\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
            .andExpect(jsonPath("$.data.currentStageCode").value("V60"))
            .andExpect(jsonPath("$.data.blockingReason").doesNotExist())
            .andExpect(jsonPath("$.data.stageRuns.length()").value(12))
            .andExpect(jsonPath("$.data.stageRuns[8].stageCode").value("V30"))
            .andExpect(jsonPath("$.data.stageRuns[8].status").value("SUCCEEDED"))
            .andExpect(jsonPath("$.data.stageRuns[9].stageCode").value("V40"))
            .andExpect(jsonPath("$.data.stageRuns[10].stageCode").value("V50"))
            .andExpect(jsonPath("$.data.stageRuns[10].status").value("SUCCEEDED"))
            .andExpect(jsonPath("$.data.stageRuns[11].stageCode").value("V60"))
            .andExpect(jsonPath("$.data.artifacts[*].artifactKind",
                hasItems("VideoCandidateAssets", "FinalVideoArtifact", "VideoReviewReport")))
            .andExpect(jsonPath("$.data.artifacts[?(@.artifactKind == 'VideoCandidateAssets')].metadata.candidateCount",
                hasItem(1)))
            .andExpect(content().string(not(containsString("direct-secret-1234"))))
            .andReturn()
            .getResponse()
            .getContentAsString();

        String chainRunId = JsonTestSupport.extractString(response, "chainRunId");
        mockMvc.perform(get("/api/chain-runs/{chainRunId}/api-selection-snapshot", chainRunId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[?(@.capabilityType=='video.generate.full_with_voice.free')].provider",
                hasItems("quality-provider", "provider-b")))
            .andExpect(content().string(not(containsString("direct-secret-1234"))));

        mockMvc.perform(get("/api/chain-runs/{chainRunId}/external-jobs", chainRunId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[?(@.capabilityType=='video.generate.full_with_voice.free')].provider",
                hasItems("provider-b")))
            .andExpect(content().string(not(containsString("direct-secret-1234"))));
    }

    @Test
    void redoesVideoRecoveryPreflightWithReselectedProviderSnapshot() throws Exception {
        saveSelectedCredentials(ChainType.VIDEO, "video.generate.full_with_voice.free");
        AtomicInteger videoReviewCalls = new AtomicInteger();
        when(providerHttpGateway.invoke(any())).thenAnswer(videoReviewFailsTwiceThenPasses(videoReviewCalls));
        String projectId = createProject();

        String waitingResponse = mockMvc.perform(post("/api/projects/{projectId}/video-chain-runs", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userGoal\":\"生成10秒9:16带人声配音的AI短剧\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("WAITING_USER"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        String chainRunId = JsonTestSupport.extractString(waitingResponse, "chainRunId");
        String recoveryStageRunId = JsonTestSupport.extractLastStageString(waitingResponse, "V30",
            "stageRunId");
        saveCredential(ChainType.VIDEO, "video.generate.full_with_voice.free", "provider-a", false,
            "direct-VIDEO-video.generate.full_with_voice.free");
        saveCredential(ChainType.VIDEO, "video.generate.full_with_voice.free", "provider-b", true,
            "direct-VIDEO-video.generate.full_with_voice.free-b");

        mockMvc.perform(post("/api/stage-runs/{stageRunId}:redo", recoveryStageRunId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
            .andExpect(jsonPath("$.data.currentStageCode").value("V60"))
            .andExpect(jsonPath("$.data.artifacts[*].artifactKind",
                hasItems("VideoCandidateAssets", "FinalVideoArtifact", "VideoReviewReport")))
            .andExpect(jsonPath("$.data.artifacts[?(@.artifactKind == 'VideoCandidateAssets')].metadata.candidateCount",
                hasItem(1)))
            .andExpect(content().string(not(containsString("direct-secret-1234"))));

        mockMvc.perform(get("/api/chain-runs/{chainRunId}/api-selection-snapshot", chainRunId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[?(@.capabilityType=='video.generate.full_with_voice.free')].provider",
                hasItems("provider-b")))
            .andExpect(content().string(not(containsString("direct-secret-1234"))));

        mockMvc.perform(get("/api/chain-runs/{chainRunId}/external-jobs", chainRunId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[?(@.capabilityType=='video.generate.full_with_voice.free')].provider",
                hasItems("provider-b")))
            .andExpect(content().string(not(containsString("direct-secret-1234"))));
    }

    @Test
    void failsImagePromptPackWhenPromptSafetyAgentVetoes() throws Exception {
        saveSelectedCredentials(ChainType.IMAGE, "image.generate.free");
        when(providerHttpGateway.invoke(any())).thenAnswer(imagePromptSafetyVetoes());
        String projectId = createProject();

        mockMvc.perform(post("/api/projects/{projectId}/image-chain-runs", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userGoal\":\"生成一张都市悬疑短剧封面\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("FAILED"))
            .andExpect(jsonPath("$.data.currentStageCode").value("I20"))
            .andExpect(jsonPath("$.data.blockingReason").value(containsString("PromptSafetyAgent")))
            .andExpect(jsonPath("$.data.blockingReason").value(containsString("CONTENT_SAFETY_REJECTED")))
            .andExpect(jsonPath("$.data.stageRuns.length()").value(3))
            .andExpect(jsonPath("$.data.stageRuns[2].stageCode").value("I20"))
            .andExpect(jsonPath("$.data.stageRuns[2].status").value("FAILED"))
            .andExpect(jsonPath("$.data.stageRuns[2].reviewReport.passed").value(false))
            .andExpect(jsonPath("$.data.artifacts.length()").value(0))
            .andExpect(content().string(not(containsString("direct-secret-1234"))));
    }

    @Test
    void failsImagePromptPackWhenPromptVariablesAreMissing() throws Exception {
        saveSelectedCredentials(ChainType.IMAGE, "image.generate.free");
        when(providerHttpGateway.invoke(any())).thenAnswer(imagePromptMissesPromptVariables());
        String projectId = createProject();

        mockMvc.perform(post("/api/projects/{projectId}/image-chain-runs", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userGoal\":\"生成一张都市悬疑短剧封面\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("FAILED"))
            .andExpect(jsonPath("$.data.currentStageCode").value("I20"))
            .andExpect(jsonPath("$.data.blockingReason").value(containsString("PromptAgent")))
            .andExpect(jsonPath("$.data.blockingReason").value(containsString("promptVariables")))
            .andExpect(jsonPath("$.data.stageRuns.length()").value(3))
            .andExpect(jsonPath("$.data.stageRuns[2].stageCode").value("I20"))
            .andExpect(jsonPath("$.data.stageRuns[2].status").value("FAILED"))
            .andExpect(jsonPath("$.data.stageRuns[2].reviewReport.passed").value(false))
            .andExpect(jsonPath("$.data.artifacts.length()").value(0))
            .andExpect(content().string(not(containsString("direct-secret-1234"))));
    }

    @Test
    void failsImageGenerationWhenProviderMissesCandidateEvidence() throws Exception {
        saveSelectedCredentials(ChainType.IMAGE, "image.generate.free");
        when(providerHttpGateway.invoke(any())).thenAnswer(imageGenerationMissesCandidateEvidence());
        String projectId = createProject();

        mockMvc.perform(post("/api/projects/{projectId}/image-chain-runs", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userGoal\":\"生成一张都市悬疑短剧封面\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("FAILED"))
            .andExpect(jsonPath("$.data.currentStageCode").value("I40"))
            .andExpect(jsonPath("$.data.blockingReason").value(containsString("ImageGenerationAgent")))
            .andExpect(jsonPath("$.data.blockingReason").value(containsString("candidateCount")))
            .andExpect(jsonPath("$.data.stageRuns.length()").value(5))
            .andExpect(jsonPath("$.data.stageRuns[4].stageCode").value("I40"))
            .andExpect(jsonPath("$.data.stageRuns[4].status").value("FAILED"))
            .andExpect(jsonPath("$.data.artifacts.length()").value(0))
            .andExpect(content().string(not(containsString("direct-secret-1234"))));
    }

    @Test
    void failsImageGoalLockWhenGoalAgentMissesStructuredScene() throws Exception {
        saveSelectedCredentials(ChainType.IMAGE, "image.generate.free");
        when(providerHttpGateway.invoke(any())).thenAnswer(imageGoalMissesScene());
        String projectId = createProject();

        mockMvc.perform(post("/api/projects/{projectId}/image-chain-runs", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userGoal\":\"生成一张都市悬疑短剧封面\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("FAILED"))
            .andExpect(jsonPath("$.data.currentStageCode").value("I00"))
            .andExpect(jsonPath("$.data.blockingReason").value(containsString("GoalAgent")))
            .andExpect(jsonPath("$.data.blockingReason").value(containsString("scene")))
            .andExpect(jsonPath("$.data.stageRuns.length()").value(1))
            .andExpect(jsonPath("$.data.stageRuns[0].stageCode").value("I00"))
            .andExpect(jsonPath("$.data.stageRuns[0].status").value("FAILED"))
            .andExpect(jsonPath("$.data.stageRuns[0].reviewReport.passed").value(false))
            .andExpect(jsonPath("$.data.artifacts.length()").value(0))
            .andExpect(content().string(not(containsString("direct-secret-1234"))));
    }

    @Test
    void failsVideoPromptPackWhenPromptMissesContinuityReference() throws Exception {
        saveSelectedCredentials(ChainType.VIDEO, "video.generate.full_with_voice.free");
        when(providerHttpGateway.invoke(any())).thenAnswer(videoPromptMissesContinuityReference());
        String projectId = createProject();

        mockMvc.perform(post("/api/projects/{projectId}/video-chain-runs", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userGoal\":\"生成10秒9:16带人声配音的AI短剧\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("FAILED"))
            .andExpect(jsonPath("$.data.currentStageCode").value("V20"))
            .andExpect(jsonPath("$.data.blockingReason").value(containsString("continuityConstraints")))
            .andExpect(jsonPath("$.data.stageRuns.length()").value(3))
            .andExpect(jsonPath("$.data.stageRuns[2].stageCode").value("V20"))
            .andExpect(jsonPath("$.data.stageRuns[2].status").value("FAILED"))
            .andExpect(jsonPath("$.data.stageRuns[2].reviewReport.passed").value(false))
            .andExpect(jsonPath("$.data.artifacts.length()").value(0))
            .andExpect(content().string(not(containsString("direct-secret-1234"))));
    }

    @Test
    void failsVideoPromptPackWhenPromptMissesMotionReference() throws Exception {
        saveSelectedCredentials(ChainType.VIDEO, "video.generate.full_with_voice.free");
        when(providerHttpGateway.invoke(any())).thenAnswer(videoPromptMissesMotionReference());
        String projectId = createProject();

        mockMvc.perform(post("/api/projects/{projectId}/video-chain-runs", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userGoal\":\"生成10秒9:16带人声配音的AI短剧\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("FAILED"))
            .andExpect(jsonPath("$.data.currentStageCode").value("V20"))
            .andExpect(jsonPath("$.data.blockingReason").value(containsString("motionPrompt")))
            .andExpect(jsonPath("$.data.stageRuns.length()").value(3))
            .andExpect(jsonPath("$.data.stageRuns[2].stageCode").value("V20"))
            .andExpect(jsonPath("$.data.stageRuns[2].status").value("FAILED"))
            .andExpect(jsonPath("$.data.stageRuns[2].reviewReport.passed").value(false))
            .andExpect(jsonPath("$.data.artifacts.length()").value(0))
            .andExpect(content().string(not(containsString("direct-secret-1234"))));
    }

    @Test
    void failsVideoPromptPackWhenPromptMissesCharacterContinuityReference() throws Exception {
        saveSelectedCredentials(ChainType.VIDEO, "video.generate.full_with_voice.free");
        when(providerHttpGateway.invoke(any())).thenAnswer(videoPromptMissesCharacterContinuityReference());
        String projectId = createProject();

        mockMvc.perform(post("/api/projects/{projectId}/video-chain-runs", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userGoal\":\"生成10秒9:16带人声配音的AI短剧\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("FAILED"))
            .andExpect(jsonPath("$.data.currentStageCode").value("V20"))
            .andExpect(jsonPath("$.data.blockingReason").value(containsString("characterContinuity")))
            .andExpect(jsonPath("$.data.stageRuns.length()").value(3))
            .andExpect(jsonPath("$.data.stageRuns[2].stageCode").value("V20"))
            .andExpect(jsonPath("$.data.stageRuns[2].status").value("FAILED"))
            .andExpect(jsonPath("$.data.stageRuns[2].reviewReport.passed").value(false))
            .andExpect(jsonPath("$.data.artifacts.length()").value(0))
            .andExpect(content().string(not(containsString("direct-secret-1234"))));
    }

    @Test
    void failsVideoPromptPackWhenPromptMissesVisualStyleReference() throws Exception {
        saveSelectedCredentials(ChainType.VIDEO, "video.generate.full_with_voice.free");
        when(providerHttpGateway.invoke(any())).thenAnswer(videoPromptMissesVisualStyleReference());
        String projectId = createProject();

        mockMvc.perform(post("/api/projects/{projectId}/video-chain-runs", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userGoal\":\"生成10秒9:16带人声配音的AI短剧\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("FAILED"))
            .andExpect(jsonPath("$.data.currentStageCode").value("V20"))
            .andExpect(jsonPath("$.data.blockingReason").value(containsString("visualStyleConstraint")))
            .andExpect(jsonPath("$.data.stageRuns.length()").value(3))
            .andExpect(jsonPath("$.data.stageRuns[2].stageCode").value("V20"))
            .andExpect(jsonPath("$.data.stageRuns[2].status").value("FAILED"))
            .andExpect(jsonPath("$.data.stageRuns[2].reviewReport.passed").value(false))
            .andExpect(jsonPath("$.data.artifacts.length()").value(0))
            .andExpect(content().string(not(containsString("direct-secret-1234"))));
    }

    @Test
    void failsVideoPromptPackWhenPromptMissesNativeVoiceRequirement() throws Exception {
        saveSelectedCredentials(ChainType.VIDEO, "video.generate.full_with_voice.free");
        when(providerHttpGateway.invoke(any())).thenAnswer(videoPromptMissesNativeVoiceRequirement());
        String projectId = createProject();

        mockMvc.perform(post("/api/projects/{projectId}/video-chain-runs", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userGoal\":\"生成10秒9:16带人声配音的AI短剧\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("FAILED"))
            .andExpect(jsonPath("$.data.currentStageCode").value("V20"))
            .andExpect(jsonPath("$.data.blockingReason").value(containsString("PromptAgent")))
            .andExpect(jsonPath("$.data.blockingReason").value(containsString("voiceoverRequirement")))
            .andExpect(jsonPath("$.data.stageRuns.length()").value(3))
            .andExpect(jsonPath("$.data.stageRuns[2].stageCode").value("V20"))
            .andExpect(jsonPath("$.data.stageRuns[2].status").value("FAILED"))
            .andExpect(jsonPath("$.data.stageRuns[2].reviewReport.passed").value(false))
            .andExpect(jsonPath("$.data.artifacts.length()").value(0))
            .andExpect(content().string(not(containsString("direct-secret-1234"))));
    }

    private Answer<ProviderHttpResponse> imageGoalMissesScene() {
        return invocation -> {
            ProviderHttpRequest request = invocation.getArgument(0);
            Map<String, Object> metadata = imageMetadata(request, 0);
            if ("I00".equals(request.stageCode()) && "GoalAgent".equals(request.nodeName())) {
                metadata = new LinkedHashMap<>(metadata);
                metadata.put("partialOutput", Map.of(
                    "subject", "侦探背影",
                    "style", "neon suspense",
                    "aspectRatio", "9:16",
                    "count", 1,
                    "goalClarityScore", 95,
                    "safetyScore", 100
                ));
                metadata = Map.copyOf(metadata);
            }
            return new ProviderHttpResponse("provider-job-" + request.stageCode(), "SUCCEEDED",
                "provider completed " + request.stageCode(), List.of("artifact-ref"), metadata,
                "free-quota-ok");
        };
    }

    private Answer<ProviderHttpResponse> imageReviewFailsOnceThenPasses(AtomicInteger reviewCalls) {
        Map<String, Integer> reviewAttemptByStageRunId = new java.util.LinkedHashMap<>();
        return invocation -> {
            ProviderHttpRequest request = invocation.getArgument(0);
            int reviewAttempt = "I50".equals(request.stageCode())
                ? reviewAttemptByStageRunId.computeIfAbsent(request.stageRunId(),
                    ignored -> reviewCalls.incrementAndGet())
                : 0;
            Map<String, Object> metadata = imageMetadata(request, reviewAttempt);
            return new ProviderHttpResponse("provider-job-" + request.stageCode(), "SUCCEEDED",
                "provider completed " + request.stageCode(), List.of("artifact-ref"), metadata,
                "free-quota-ok");
        };
    }

    // V20 连续性/动作/风格约束现在由 sibling agent 建立、确定性合并进提示词包，评审校验其非空。
    // 因此把"约束缺失导致 V20 失败"通过置空对应 agent 的输出来触发。
    private Answer<ProviderHttpResponse> videoPromptMissesContinuityReference() {
        return videoAgentBlankField("ContinuityAgent", "continuityConstraints");
    }

    private Answer<ProviderHttpResponse> videoPromptMissesMotionReference() {
        return videoAgentBlankField("MotionPromptAgent", "motionPrompt");
    }

    private Answer<ProviderHttpResponse> videoPromptMissesCharacterContinuityReference() {
        return videoAgentBlankField("ContinuityAgent", "characterContinuity");
    }

    private Answer<ProviderHttpResponse> videoPromptMissesVisualStyleReference() {
        return videoAgentBlankField("ContinuityAgent", "visualStyleConstraint");
    }

    private Answer<ProviderHttpResponse> videoAgentBlankField(String agentName, String field) {
        return invocation -> {
            ProviderHttpRequest request = invocation.getArgument(0);
            Map<String, Object> metadata = videoMetadata(request);
            if ("V20".equals(request.stageCode()) && agentName.equals(request.nodeName())) {
                Map<String, Object> partial = new LinkedHashMap<>(partialOutput(request));
                partial.put(field, "");
                metadata = new LinkedHashMap<>(metadata);
                metadata.put("partialOutput", Map.copyOf(partial));
                metadata = Map.copyOf(metadata);
            }
            return new ProviderHttpResponse("provider-job-" + request.stageCode(), "SUCCEEDED",
                "provider completed " + request.stageCode(), List.of("artifact-ref"), metadata,
                "free-quota-ok");
        };
    }

    private Answer<ProviderHttpResponse> videoPromptMissesNativeVoiceRequirement() {
        return videoPromptWithOverrides(java.util.Collections.singletonMap("voiceoverRequirement", null));
    }

    private Answer<ProviderHttpResponse> videoPromptWithOverrides(Map<String, Object> promptOverrides) {
        return invocation -> {
            ProviderHttpRequest request = invocation.getArgument(0);
            Map<String, Object> metadata = videoMetadata(request);
            if ("V20".equals(request.stageCode()) && "PromptAgent".equals(request.nodeName())) {
                metadata = new LinkedHashMap<>(metadata);
                metadata.put("partialOutput", videoPromptPartialOutput(promptOverrides));
                metadata = Map.copyOf(metadata);
            }
            return new ProviderHttpResponse("provider-job-" + request.stageCode(), "SUCCEEDED",
                "provider completed " + request.stageCode(), List.of("artifact-ref"), metadata,
                "free-quota-ok");
        };
    }

    private Map<String, Object> videoPromptPartialOutput(Map<String, Object> overrides) {
        Map<String, Object> partialOutput = new LinkedHashMap<>(Map.of(
            "positivePrompt", "complete short drama",
            "durationSeconds", 10,
            "aspectRatio", "9:16",
            "voiceoverRequirement", "HUMAN_VOICE_REQUIRED",
            "continuityConstraintRefs", List.of("same subject and scene"),
            "motionPromptRefs", List.of("slow push-in with subject movement"),
            "characterContinuityRefs", List.of("same detective profile"),
            "visualStyleRefs", List.of("neon suspense")
        ));
        overrides.forEach((key, value) -> {
            if (value == null) {
                partialOutput.remove(key);
            } else {
                partialOutput.put(key, value);
            }
        });
        return Map.copyOf(partialOutput);
    }

    private Answer<ProviderHttpResponse> imagePromptMissesPromptVariables() {
        return invocation -> {
            ProviderHttpRequest request = invocation.getArgument(0);
            Map<String, Object> metadata = imageMetadata(request, 0);
            if ("I20".equals(request.stageCode()) && "PromptAgent".equals(request.nodeName())) {
                metadata = new LinkedHashMap<>(metadata);
                metadata.put("partialOutput", Map.of(
                    "positivePrompt", "portrait of {{subject}}",
                    "aspectRatio", "9:16"
                ));
                metadata = Map.copyOf(metadata);
            }
            return new ProviderHttpResponse("provider-job-" + request.stageCode(), "SUCCEEDED",
                "provider completed " + request.stageCode(), List.of("artifact-ref"), metadata,
                "free-quota-ok");
        };
    }

    private Answer<ProviderHttpResponse> imageGenerationMissesCandidateEvidence() {
        return invocation -> {
            ProviderHttpRequest request = invocation.getArgument(0);
            Map<String, Object> metadata = imageMetadata(request, 0);
            if ("I40".equals(request.stageCode())) {
                metadata = new LinkedHashMap<>(metadata);
                metadata.put("candidateCount", 0);
                metadata = Map.copyOf(metadata);
            }
            return new ProviderHttpResponse("provider-job-" + request.stageCode(), "SUCCEEDED",
                "provider completed " + request.stageCode(), List.of(), metadata, "free-quota-ok");
        };
    }

    private Answer<ProviderHttpResponse> imagePromptSafetyVetoes() {
        return invocation -> {
            ProviderHttpRequest request = invocation.getArgument(0);
            Map<String, Object> metadata = imageMetadata(request, 0);
            if ("I20".equals(request.stageCode()) && "PromptSafetyAgent".equals(request.nodeName())) {
                metadata = new LinkedHashMap<>(metadata);
                metadata.put("partialOutput", Map.of(
                    "safetyPassed", false,
                    "vetoReason", "CONTENT_SAFETY_REJECTED"
                ));
                metadata = Map.copyOf(metadata);
            }
            return new ProviderHttpResponse("provider-job-" + request.stageCode(), "SUCCEEDED",
                "provider completed " + request.stageCode(), List.of("artifact-ref"), metadata,
                "free-quota-ok");
        };
    }

    private Answer<ProviderHttpResponse> videoReviewAlwaysFails() {
        return invocation -> {
            ProviderHttpRequest request = invocation.getArgument(0);
            Map<String, Object> metadata = videoMetadata(request);
            return new ProviderHttpResponse("provider-job-" + request.stageCode(), "SUCCEEDED",
                "provider completed " + request.stageCode(), List.of("artifact-ref"), metadata,
                "free-quota-ok");
        };
    }

    private Answer<ProviderHttpResponse> videoReviewFailsTwiceThenPasses(AtomicInteger reviewCalls) {
        Map<String, Integer> reviewAttemptByStageRunId = new java.util.LinkedHashMap<>();
        return invocation -> {
            ProviderHttpRequest request = invocation.getArgument(0);
            int reviewAttempt = "V50".equals(request.stageCode())
                ? reviewAttemptByStageRunId.computeIfAbsent(request.stageRunId(),
                    ignored -> reviewCalls.incrementAndGet())
                : 0;
            Map<String, Object> metadata = videoMetadataThatEventuallyPasses(request, reviewAttempt);
            return new ProviderHttpResponse(request.provider() + "-job-" + request.stageCode(), "SUCCEEDED",
                "provider completed " + request.stageCode(), List.of("artifact-ref"), metadata,
                "free-quota-ok");
        };
    }

    private Answer<ProviderHttpResponse> videoProviderBFixesReview(AtomicBoolean providerBGeneratedVideo) {
        return invocation -> {
            ProviderHttpRequest request = invocation.getArgument(0);
            Map<String, Object> metadata = videoMetadataForProviderSwitch(request, providerBGeneratedVideo);
            return new ProviderHttpResponse(request.provider() + "-job-" + request.stageCode(), "SUCCEEDED",
                "provider completed " + request.stageCode(), List.of("artifact-ref"), metadata,
                "free-quota-ok");
        };
    }

    private Map<String, Object> imageMetadata(ProviderHttpRequest request, int reviewAttempt) {
        Map<String, Object> metadata = baseMetadata(request);
        if ("I40".equals(request.stageCode())) {
            metadata.put("candidateCount", 4);
            metadata.put("aspectRatio", "9:16");
            metadata.put("artifactIntegrityScore", 100);
            return Map.copyOf(metadata);
        }
        if ("I50".equals(request.stageCode())) {
            if (reviewAttempt == 1) {
                metadata.put("finalScore", 70);
                metadata.put("safetyScore", 100);
                metadata.put("artifactIntegrityScore", 100);
                return Map.copyOf(metadata);
            }
            metadata.put("finalScore", 96);
            metadata.put("safetyScore", 100);
            metadata.put("artifactIntegrityScore", 100);
            return Map.copyOf(metadata);
        }
        return Map.copyOf(metadata);
    }

    private Map<String, Object> videoMetadata(ProviderHttpRequest request) {
        Map<String, Object> metadata = baseMetadata(request);
        if ("V40".equals(request.stageCode())) {
            metadata.putAll(videoGenerationEvidence());
            return Map.copyOf(metadata);
        }
        if ("V50".equals(request.stageCode())) {
            metadata.put("finalScore", 88);
            metadata.put("decodeIntegrityScore", 100);
            metadata.put("safetyScore", 100);
            metadata.put("shortDramaScore", 92);
            metadata.put("humanVoiceAudible", false);
            return Map.copyOf(metadata);
        }
        return Map.copyOf(metadata);
    }

    private Map<String, Object> videoMetadataThatEventuallyPasses(ProviderHttpRequest request, int reviewAttempt) {
        Map<String, Object> metadata = baseMetadata(request);
        if ("V40".equals(request.stageCode())) {
            metadata.putAll(videoGenerationEvidence());
            return Map.copyOf(metadata);
        }
        if ("V50".equals(request.stageCode()) && reviewAttempt <= 2) {
            metadata.put("finalScore", 88);
            metadata.put("decodeIntegrityScore", 100);
            metadata.put("safetyScore", 100);
            metadata.put("shortDramaScore", 92);
            metadata.put("humanVoiceAudible", false);
            return Map.copyOf(metadata);
        }
        if ("V50".equals(request.stageCode())) {
            metadata.put("finalScore", 92);
            metadata.put("decodeIntegrityScore", 100);
            metadata.put("safetyScore", 100);
            metadata.put("shortDramaScore", 92);
            metadata.put("humanVoiceAudible", true);
            return Map.copyOf(metadata);
        }
        return Map.copyOf(metadata);
    }

    private Map<String, Object> videoMetadataForProviderSwitch(ProviderHttpRequest request,
            AtomicBoolean providerBGeneratedVideo) {
        Map<String, Object> metadata = baseMetadata(request);
        if ("V40".equals(request.stageCode())) {
            if ("provider-b".equals(request.provider())) {
                providerBGeneratedVideo.set(true);
            }
            metadata.putAll(videoGenerationEvidence());
            return Map.copyOf(metadata);
        }
        if ("V50".equals(request.stageCode()) && !providerBGeneratedVideo.get()) {
            metadata.put("finalScore", 88);
            metadata.put("decodeIntegrityScore", 100);
            metadata.put("safetyScore", 100);
            metadata.put("shortDramaScore", 92);
            metadata.put("humanVoiceAudible", false);
            return Map.copyOf(metadata);
        }
        if ("V50".equals(request.stageCode())) {
            metadata.put("finalScore", 92);
            metadata.put("decodeIntegrityScore", 100);
            metadata.put("safetyScore", 100);
            metadata.put("shortDramaScore", 92);
            metadata.put("humanVoiceAudible", true);
            return Map.copyOf(metadata);
        }
        return Map.copyOf(metadata);
    }

    private Map<String, Object> baseMetadata(ProviderHttpRequest request) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("adapterKind", "HTTP_ADAPTER");
        metadata.put("stageCode", request.stageCode());
        Map<String, Object> partialOutput = partialOutput(request);
        if (!partialOutput.isEmpty()) {
            metadata.put("partialOutput", partialOutput);
        }
        return metadata;
    }

    private Map<String, Object> partialOutput(ProviderHttpRequest request) {
        return switch (request.nodeName()) {
            case "GoalAgent" -> goalPartialOutput(request);
            case "SubjectAgent" -> Map.of("subject", "侦探背影", "aspectRatio", "1:1");
            case "StyleAgent" -> Map.of("palette", "neon", "aspectRatio", "16:9");
            case "ConstraintAgent" -> constraintPartialOutput(request);
            case "PromptAgent" -> promptPartialOutput(request);
            case "NegativePromptAgent" -> Map.of("negativePrompt", "logo, watermark, blur");
            case "PromptSafetyAgent" -> Map.of("safetyPassed", true);
            case "CapabilityAgent" -> Map.of("providerId", request.provider(), "apiKeyId", request.apiKeyId(),
                "model", request.model(), "freeModelGatePassed", true);
            case "ProviderFitAgent" -> Map.of("selectedProviderId", request.provider(), "reason", "fixture fit");
            case "StoryAgent" -> Map.of("story", "主角发现线索并完成反转");
            case "VisualAgent" -> Map.of("visualStyle", "neon suspense", "aspectRatio", "16:9");
            case "MotionAgent" -> Map.of("motion", "slow push-in", "rhythm", "fast hook");
            case "MotionPromptAgent" -> Map.of("motionPrompt", "slow push-in with subject movement");
            case "ContinuityAgent" -> Map.of(
                "continuityConstraints", "same subject and scene",
                "characterContinuity", "same detective profile",
                "visualStyleConstraint", "neon suspense"
            );
            default -> Map.of();
        };
    }

    private Map<String, Object> goalPartialOutput(ProviderHttpRequest request) {
        if (request.stageCode().startsWith("V")) {
            return Map.of(
                "theme", "都市悬疑反转",
                "durationSeconds", 10,
                "aspectRatio", "9:16",
                "style", "neon suspense",
                "voiceoverRequirement", "HUMAN_VOICE_REQUIRED",
                "outputFormat", "complete_short_video",
                "goalClarityScore", 95,
                "safetyScore", 100
            );
        }
        return Map.of(
            "subject", "侦探背影",
            "scene", "雨夜霓虹街口",
            "style", "neon suspense",
            "aspectRatio", "9:16",
            "count", 1,
            "goalClarityScore", 95,
            "safetyScore", 100
        );
    }

    private Map<String, Object> constraintPartialOutput(ProviderHttpRequest request) {
        if (request.stageCode().startsWith("V")) {
            return Map.of("aspectRatio", "9:16", "durationSeconds", 10, "nativeVoiceRequired", true);
        }
        return Map.of("aspectRatio", "9:16", "forbiddenTerms", List.of("logo"));
    }

    private Map<String, Object> promptPartialOutput(ProviderHttpRequest request) {
        if (request.stageCode().startsWith("V")) {
            return Map.of(
                "positivePrompt", "complete short drama",
                "durationSeconds", 10,
                "aspectRatio", "9:16",
                "voiceoverRequirement", "HUMAN_VOICE_REQUIRED",
                "continuityConstraintRefs", List.of("same subject and scene"),
                "characterContinuityRefs", List.of("same detective profile"),
                "motionPromptRefs", List.of("slow push-in with subject movement"),
                "visualStyleRefs", List.of("neon suspense")
            );
        }
        return Map.of(
            "positivePrompt", "neon suspense poster",
            "aspectRatio", "9:16",
            "promptVariables", List.of("subject=侦探背影", "scene=雨夜霓虹街口", "style=neon suspense")
        );
    }

    private Map<String, Object> videoGenerationEvidence() {
        return Map.of(
            "adapterKind", "HTTP_ADAPTER",
            "completeShortVideoSupported", true,
            "nativeHumanVoiceSupported", true,
            "candidateCount", 1,
            "durationSeconds", 10,
            "aspectRatio", "9:16",
            "decodeIntegrityScore", 100,
            "hasHumanVoice", true
        );
    }

    private String createProject() throws Exception {
        String response = mockMvc.perform(post("/api/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"都市悬疑\",\"goal\":\"生成短剧资产\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        return JsonTestSupport.extractString(response, "projectId");
    }

    private void saveSelectedCredentials(ChainType chainType, String generationCapability) {
        saveSelectedCredential(chainType, "llm.text.free");
        saveSelectedCredential(chainType, "rag.embedding.free");
        saveSelectedCredential(chainType, "rag.rerank.free");
        saveSelectedCredential(chainType, generationCapability);
    }

    private void saveSelectedCredential(ChainType chainType, String capabilityType) {
        saveCredential(chainType, capabilityType, "quality-provider", true,
            "direct-" + chainType + "-" + capabilityType);
    }

    private void saveCredential(ChainType chainType, String capabilityType, String provider, boolean selected,
            String apiKeyId) {
        ApiKeyStatus status = selected ? ApiKeyStatus.ACTIVE : ApiKeyStatus.AVAILABLE;
        apiConfigRepository.save(new ApiCredential(apiKeyId, chainType, capabilityType, provider, "direct",
            "hash", "encrypted", "****1234", "fixture-contract", status, selected, Instant.now(),
            FreeModelGateStatus.PASSED));
    }
}
