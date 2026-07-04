package com.aimv.interfaces.chain;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
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
class ChainRunVideoCapabilityGateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiConfigRepository apiConfigRepository;

    @MockitoBean
    private ProviderHttpGateway providerHttpGateway;

    @Test
    void blocksVideoChainWhenProviderDoesNotProveNativeHumanVoiceCapability() throws Exception {
        saveSelectedCredential("llm.text.free");
        saveSelectedCredential("rag.embedding.free");
        saveSelectedCredential("rag.rerank.free");
        saveSelectedCredential("video.generate.full_with_voice.free");
        when(providerHttpGateway.invoke(any())).thenAnswer(providerResponse());
        String projectId = createProject();

        mockMvc.perform(post("/api/projects/{projectId}/video-chain-runs", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userGoal\":\"生成10秒9:16带人声配音的AI短剧\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("WAITING_CAPABILITY"))
            .andExpect(jsonPath("$.data.currentStageCode").value("V40"))
            .andExpect(jsonPath("$.data.blockingReason").value(containsString("原生人声")))
            .andExpect(jsonPath("$.data.stageRuns.length()").value(5))
            .andExpect(jsonPath("$.data.stageRuns[4].stageCode").value("V40"))
            .andExpect(jsonPath("$.data.stageRuns[4].status").value("WAITING_CAPABILITY"))
            .andExpect(jsonPath("$.data.artifacts.length()").value(0))
            .andExpect(content().string(not(containsString("direct-secret-1234"))));
    }

    private Answer<ProviderHttpResponse> providerResponse() {
        return invocation -> {
            ProviderHttpRequest request = invocation.getArgument(0);
            Map<String, Object> metadata = metadata(request);
            return new ProviderHttpResponse("provider-job-" + request.stageCode(), "SUCCEEDED",
                "provider completed without native voice evidence", List.of("video-url"), metadata,
                "free-quota-ok");
        };
    }

    private Map<String, Object> metadata(ProviderHttpRequest request) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("adapterKind", "HTTP_ADAPTER");
        metadata.put("capabilityType", request.capabilityType());
        metadata.put("stageCode", request.stageCode());
        if ("video.generate.full_with_voice.free".equals(request.capabilityType())) {
            metadata.put("adapterKind", "DASHSCOPE_VIDEO_ASYNC");
            metadata.put("completeShortVideoSupported", true);
            metadata.put("nativeHumanVoiceSupported", false);
            metadata.put("durationSeconds", 10);
            metadata.put("aspectRatio", "9:16");
        }
        Map<String, Object> partialOutput = partialOutput(request);
        if (!partialOutput.isEmpty()) {
            metadata.put("partialOutput", partialOutput);
        }
        return Map.copyOf(metadata);
    }

    private Map<String, Object> partialOutput(ProviderHttpRequest request) {
        return switch (request.nodeName()) {
            case "GoalAgent" -> Map.of(
                "theme", "都市悬疑反转",
                "durationSeconds", 10,
                "aspectRatio", "9:16",
                "style", "neon suspense",
                "voiceoverRequirement", "HUMAN_VOICE_REQUIRED",
                "outputFormat", "complete_short_video",
                "goalClarityScore", 95,
                "safetyScore", 100
            );
            case "StoryAgent" -> Map.of("story", "主角发现线索并完成反转");
            case "VisualAgent" -> Map.of("visualStyle", "neon suspense", "aspectRatio", "16:9");
            case "MotionAgent" -> Map.of("motion", "slow push-in", "rhythm", "fast hook");
            case "ConstraintAgent" -> Map.of("aspectRatio", "9:16", "durationSeconds", 10,
                "nativeVoiceRequired", true);
            case "PromptAgent" -> Map.of(
                "positivePrompt", "complete short drama",
                "durationSeconds", 10,
                "aspectRatio", "9:16",
                "voiceoverRequirement", "HUMAN_VOICE_REQUIRED",
                "continuityConstraintRefs", List.of("same subject and scene"),
                "characterContinuityRefs", List.of("same detective profile"),
                "motionPromptRefs", List.of("slow push-in with subject movement"),
                "visualStyleRefs", List.of("neon suspense")
            );
            case "MotionPromptAgent" -> Map.of("motionPrompt", "slow push-in with subject movement");
            case "ContinuityAgent" -> Map.of(
                "continuityConstraints", "same subject and scene",
                "characterContinuity", "same detective profile",
                "visualStyleConstraint", "neon suspense"
            );
            case "PromptSafetyAgent" -> Map.of("safetyPassed", true);
            case "CapabilityAgent" -> Map.of("providerId", request.provider(), "apiKeyId", request.apiKeyId(),
                "model", request.model(), "nativeHumanVoiceSupported", false);
            case "ProviderFitAgent" -> Map.of("selectedProviderId", request.provider(), "reason", "fixture fit");
            default -> Map.of();
        };
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

    private void saveSelectedCredential(String capabilityType) {
        apiConfigRepository.save(new ApiCredential("direct-" + capabilityType, ChainType.VIDEO, capabilityType,
            "dashscope-free", "direct", "hash", "encrypted", "****1234", "fixture-contract",
            ApiKeyStatus.ACTIVE, true, Instant.now(), FreeModelGateStatus.PASSED));
    }
}
