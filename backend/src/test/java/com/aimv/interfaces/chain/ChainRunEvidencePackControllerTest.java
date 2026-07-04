package com.aimv.interfaces.chain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aimv.domain.capability.ApiConfigRepository;
import com.aimv.domain.capability.ApiCredential;
import com.aimv.domain.capability.ApiKeyStatus;
import com.aimv.domain.capability.FreeModelGateStatus;
import com.aimv.domain.knowledge.KnowledgeChunk;
import com.aimv.domain.knowledge.KnowledgeRepository;
import com.aimv.domain.provider.ProviderHttpGateway;
import com.aimv.domain.provider.ProviderHttpRequest;
import com.aimv.domain.provider.ProviderHttpResponse;
import com.aimv.domain.shared.ChainType;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
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
class ChainRunEvidencePackControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiConfigRepository apiConfigRepository;

    @Autowired
    private KnowledgeRepository knowledgeRepository;

    @MockitoBean
    private ProviderHttpGateway providerHttpGateway;

    @Test
    void sendsCompressedEvidencePackToEveryAgentProviderRequest() throws Exception {
        List<ProviderHttpRequest> requests = new ArrayList<>();
        saveSelectedCredential("llm.text.free");
        saveSelectedCredential("rag.embedding.free");
        saveSelectedCredential("rag.rerank.free");
        saveSelectedCredential("image.generate.free");
        when(providerHttpGateway.invoke(any())).thenAnswer(successfulImageResponse(requests));
        String projectId = createProject();

        String response = mockMvc.perform(post("/api/projects/{projectId}/image-chain-runs", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userGoal\":\"生成一张9:16都市悬疑短剧封面\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        // RAG embedding/rerank 是基础设施调用（随每次 ingest/检索发生），不是 agent 执行请求，
        // 断言 agent 请求序列前先剔除。
        List<ProviderHttpRequest> agentRequests = requests.stream()
            .filter(request -> !"rag.embedding.free".equals(request.capabilityType()))
            .filter(request -> !"rag.rerank.free".equals(request.capabilityType()))
            .toList();
        // I30 能力预检的两个 agent 现在是后端确定性判定（不调用外部 provider），
        // 因此不出现在 provider 请求列表中。
        assertThat(agentRequests)
            .extracting(ProviderHttpRequest::stageCode)
            .containsExactly("I00", "I10", "I10", "I10", "I20", "I20", "I20",
                "I40", "I50", "I50", "I50", "I60");
        assertThat(agentRequests)
            .extracting(ProviderHttpRequest::nodeName)
            .containsExactly("GoalAgent", "SubjectAgent", "StyleAgent", "ConstraintAgent", "PromptAgent",
                "NegativePromptAgent", "PromptSafetyAgent",
                "ImageGenerationAgent", "VisualQualityAgent", "GoalMatchAgent", "SafetyReviewAgent",
                "ImageAcceptanceAgent");
        assertThat(agentRequests)
            .allSatisfy(request -> assertThat(request.input()).containsKey("evidencePack"));
        assertThat(agentRequests)
            .allSatisfy(request -> assertThat(request.input()).containsKey("stageInputContext"));

        ProviderHttpRequest i20Request = agentRequests.stream()
            .filter(request -> "I20".equals(request.stageCode()))
            .findFirst()
            .orElseThrow();
        Map<?, ?> stageInputContext = stageInputContext(i20Request);
        assertThat(stageInputContext.get("contextVersion")).isEqualTo("v1");
        assertThat(stageInputContext.get("projectId")).isEqualTo(projectId);
        assertThat(stageInputContext.get("chainType")).isEqualTo("IMAGE");
        assertThat(stageInputContext.get("chainRunId")).isEqualTo(i20Request.chainRunId());
        assertThat(stageInputContext.get("stageRunId")).isEqualTo(i20Request.stageRunId());
        assertThat(stageInputContext.get("currentStage")).isEqualTo("I20");
        assertThat(stageInputContext.get("goalRef")).asString().startsWith("chunk-");
        assertThat(stageInputContext.get("previousHandoffRef")).asString().startsWith("chunk-");
        assertThat(stageInputContext.get("previousReviewRef")).asString().startsWith("chunk-");
        assertThat(stageInputContext.get("retrievalPolicyRef")).isEqualTo("rag/image/i20.v1.json");
        assertThat(stageInputContext.get("stageDefinitionRef")).isEqualTo("stage/image/i20.v1.json");
        assertThat(stageInputContext.toString()).doesNotContain("secret-key-1234");

        Map<?, ?> evidencePack = evidencePack(i20Request);
        assertThat(evidencePack.get("schemaVersion")).isEqualTo("1.0");
        assertThat(evidencePack.get("chainType")).isEqualTo("IMAGE");
        assertThat(evidencePack.get("stageCode")).isEqualTo("I20");
        assertThat(evidencePack.get("retrievalRecordId")).asString().startsWith("retrieval-");
        List<String> citationChunkIds = strings(evidencePack.get("citationChunkIds"));
        List<String> requiredConstraints = strings(evidencePack.get("requiredConstraints"));
        assertThat(citationChunkIds).isNotEmpty();
        assertThat(requiredConstraints).contains("chainType=IMAGE", "stageCode=I20");
        assertThat(evidencePack.get("coverage")).asString()
            .contains("previousHandoff=true", "previousReviewReport=true", "passed=true");
        assertThat(evidencePack.toString()).doesNotContain("secret-key-1234");

        String i10HandoffContextId = JsonTestSupport.extractStageString(response, "I10", "handoffContextId");
        KnowledgeChunk i10Handoff = knowledgeRepository.findChunk(i10HandoffContextId)
            .orElseThrow(() -> new AssertionError("I10 handoff missing: " + i10HandoffContextId));
        JsonNode i10Context = JsonTestSupport.readTree(i10Handoff.content());
        assertThat(i10Context.at("/stageOutput/outputSchemaId").asText()).isEqualTo("image-I10-output.v1");
        assertThat(i10Context.at("/stageOutput/mergedOutput/aspectRatio").asText()).isEqualTo("9:16");
        assertThat(i10Context.at("/stageOutput/mergedOutput/subject").asText()).isEqualTo("侦探背影");
        assertThat(i10Context.at("/stageOutput/mergedOutput/palette").asText()).isEqualTo("neon");
        assertThat(i10Context.at("/stageOutput/conflictResolutions/0/fieldName").asText()).isEqualTo("aspectRatio");
        assertThat(i10Context.at("/stageOutput/conflictResolutions/0/selectedAgentName").asText())
            .isEqualTo("ConstraintAgent");
        assertThat(i10Context.at("/stageOutput/conflictResolutions/0/conflictingAgentNames").toString())
            .contains("SubjectAgent", "StyleAgent");
    }

    private Map<?, ?> stageInputContext(ProviderHttpRequest request) {
        Object value = request.input().get("stageInputContext");
        assertThat(value).isInstanceOf(Map.class);
        return (Map<?, ?>) value;
    }

    private Map<?, ?> evidencePack(ProviderHttpRequest request) {
        Object value = request.input().get("evidencePack");
        assertThat(value).isInstanceOf(Map.class);
        return (Map<?, ?>) value;
    }

    private List<String> strings(Object value) {
        assertThat(value).isInstanceOf(List.class);
        return ((List<?>) value).stream()
            .map(String::valueOf)
            .toList();
    }

    private Answer<ProviderHttpResponse> successfulImageResponse(List<ProviderHttpRequest> requests) {
        return invocation -> {
            ProviderHttpRequest request = invocation.getArgument(0);
            requests.add(request);
            return new ProviderHttpResponse("provider-job-" + request.stageCode(), "SUCCEEDED",
                request.stageCode() + " completed", List.of(), providerMetadata(request), "free-quota-ok");
        };
    }

    private Map<String, Object> providerMetadata(ProviderHttpRequest request) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("adapterKind", "HTTP_ADAPTER");
        metadata.put("capabilityType", request.capabilityType());
        metadata.put("stageCode", request.stageCode());
        if ("I40".equals(request.stageCode())) {
            metadata.put("candidateCount", 4);
            metadata.put("aspectRatio", "9:16");
            metadata.put("artifactIntegrityScore", 100);
        }
        if ("I50".equals(request.stageCode())) {
            metadata.put("finalScore", 96);
            metadata.put("safetyScore", 100);
            metadata.put("artifactIntegrityScore", 100);
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
                "subject", "侦探背影",
                "scene", "雨夜霓虹街口",
                "style", "neon suspense",
                "aspectRatio", "9:16",
                "count", 1,
                "goalClarityScore", 95,
                "safetyScore", 100
            );
            case "SubjectAgent" -> Map.of("subject", "侦探背影", "aspectRatio", "1:1");
            case "StyleAgent" -> Map.of("palette", "neon", "aspectRatio", "16:9");
            case "ConstraintAgent" -> Map.of("aspectRatio", "9:16", "forbiddenTerms", List.of("logo"));
            case "PromptAgent" -> Map.of(
                "positivePrompt", "neon suspense poster",
                "aspectRatio", "9:16",
                "promptVariables", List.of("subject=侦探背影", "scene=雨夜霓虹街口", "style=neon suspense")
            );
            case "NegativePromptAgent" -> Map.of("negativePrompt", "logo, watermark, blur");
            case "PromptSafetyAgent" -> Map.of("safetyPassed", true);
            case "CapabilityAgent" -> Map.of("providerId", request.provider(), "apiKeyId", request.apiKeyId(),
                "model", request.model(), "freeModelGatePassed", true);
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
        apiConfigRepository.save(new ApiCredential("direct-" + capabilityType, ChainType.IMAGE, capabilityType,
            "fixture-free", "direct", "hash", "encrypted", "****1234", "fixture-contract",
            ApiKeyStatus.ACTIVE, true, Instant.now(), FreeModelGateStatus.PASSED));
    }
}
