package com.aimv.interfaces.chain;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aimv.domain.externaljob.ExternalJob;
import com.aimv.domain.externaljob.ExternalJobRepository;
import com.aimv.domain.externaljob.ExternalJobStatus;
import com.aimv.domain.knowledge.KnowledgeChunk;
import com.aimv.domain.knowledge.KnowledgeRepository;
import com.aimv.domain.capability.ApiConfigRepository;
import com.aimv.domain.capability.ApiCredential;
import com.aimv.domain.capability.ApiKeyStatus;
import com.aimv.domain.capability.FreeModelGateStatus;
import com.aimv.domain.shared.ChainType;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ChainRunControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExternalJobRepository externalJobRepository;

    @Autowired
    private KnowledgeRepository knowledgeRepository;

    @Autowired
    private ApiConfigRepository apiConfigRepository;

    @Test
    void rejectsImageChainWhenSelectedKeysAreMissing() throws Exception {
        String projectId = createProject();

        mockMvc.perform(post("/api/projects/{projectId}/image-chain-runs", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userGoal\":\"生成一张都市悬疑短剧封面\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("API_CAPABILITY_NOT_CONFIGURED"));
    }

    @Test
    void rejectsChainStartWhenSelectedKeyNoLongerPassesFreeModelGate() throws Exception {
        saveSelectedCredential("llm.text.free", FreeModelGateStatus.PASSED);
        saveSelectedCredential("rag.embedding.free", FreeModelGateStatus.PASSED);
        saveSelectedCredential("rag.rerank.free", FreeModelGateStatus.PASSED);
        saveSelectedCredential("image.generate.free", FreeModelGateStatus.FAILED);
        String projectId = createProject();

        mockMvc.perform(post("/api/projects/{projectId}/image-chain-runs", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userGoal\":\"生成一张都市悬疑短剧封面\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("FREE_MODEL_GATE_FAILED"))
            .andExpect(content().string(not(containsString("direct-secret-1234"))));
    }

    @Test
    void runsImageChainThroughFixedStagesWhenFreeFixtureKeysAreSelected() throws Exception {
        selectAllKeys("IMAGE");
        String projectId = createProject();

        String response = mockMvc.perform(post("/api/projects/{projectId}/image-chain-runs", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userGoal\":\"生成一张9:16都市悬疑短剧封面\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.chainType").value("IMAGE"))
            .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
            .andExpect(jsonPath("$.data.currentStageCode").value("I60"))
            .andExpect(jsonPath("$.data.stageRuns[*].stageCode",
                    hasItems("I00", "I10", "I20", "I30", "I40", "I50", "I60")))
            .andExpect(jsonPath("$.data.stageRuns[*].retrievalRecordId").isNotEmpty())
            .andExpect(jsonPath("$.data.stageRuns[*].handoffContextId").isNotEmpty())
            .andExpect(jsonPath("$.data.stageRuns[*].agentNodeRunIds").isNotEmpty())
            .andExpect(jsonPath("$.data.stageRuns[*].freeModelGateIds").isNotEmpty())
            .andExpect(jsonPath("$.data.stageRuns[*].providerJobIds").isNotEmpty())
            .andExpect(jsonPath("$.data.artifacts[*].artifactKind",
                    hasItems("ImageCandidateAssets", "FinalImageArtifact", "ImageReviewReport")))
            .andExpect(jsonPath("$.data.artifacts[?(@.artifactKind == 'ImageCandidateAssets')].metadata.candidateCount",
                    hasItem(4)))
            .andReturn()
            .getResponse()
            .getContentAsString();

        String chainRunId = JsonTestSupport.extractString(response, "chainRunId");
        String retrievalRecordId = JsonTestSupport.extractString(response, "retrievalRecordId");
        String nodeRunId = JsonTestSupport.extractFirstArrayString(response, "agentNodeRunIds");
        String i40ProviderJobId = JsonTestSupport.extractStageFirstArrayString(response, "I40",
            "providerJobIds");

        mockMvc.perform(get("/api/chain-runs/{chainRunId}/api-selection-snapshot", chainRunId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[*].maskedKey", not(hasItems(containsString("secret")))))
            .andExpect(jsonPath("$.data[*].freeModelGate.passed", hasItems(true)));

        mockMvc.perform(get("/api/retrieval-records/{retrievalRecordId}", retrievalRecordId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.stageCode").value("I00"))
            .andExpect(jsonPath("$.data.hitChunkIds.length()").value(3))
            .andExpect(jsonPath("$.data.coverage.goal").value(true))
            .andExpect(jsonPath("$.data.coverage.stageMap").value(true))
            .andExpect(jsonPath("$.data.coverage.currentStage").value(true))
            .andExpect(jsonPath("$.data.coverage.passed").value(true));

        mockMvc.perform(get("/api/node-runs/{nodeRunId}", nodeRunId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.nodeRunId").value(nodeRunId))
            .andExpect(jsonPath("$.data.freeModelGate.passed").value(true))
            .andExpect(jsonPath("$.data.freeModelGate.paidFallbackAllowed").value(false))
            .andExpect(jsonPath("$.data.providerJobId").isNotEmpty())
            .andExpect(jsonPath("$.data.outputSummary").isNotEmpty())
            .andExpect(jsonPath("$.data.secretHash").doesNotExist())
            .andExpect(content().string(not(containsString("secret-key-1234"))));

        List<ExternalJob> externalJobs = externalJobRepository.findByChainRunId(chainRunId);
        ExternalJob imageJob = externalJobs.stream()
            .filter(job -> job.providerJobId().equals(i40ProviderJobId))
            .findFirst()
            .orElseThrow();
        // 12 = 14 个 agent 节点减去 I30 能力预检的 2 个确定性节点（CapabilityAgent、ProviderFitAgent
        // 由后端事实判定，不调用外部 provider，因此不产生 ExternalJob）。
        org.assertj.core.api.Assertions.assertThat(externalJobs).hasSize(12);
        org.assertj.core.api.Assertions.assertThat(imageJob.status()).isEqualTo(ExternalJobStatus.SUCCEEDED);
        org.assertj.core.api.Assertions.assertThat(imageJob.retryPolicy()).isEqualTo("FREE_PROVIDER_RETRY_ONLY");
        org.assertj.core.api.Assertions.assertThat(imageJob.retryCount()).isZero();
        org.assertj.core.api.Assertions.assertThat(imageJob.requestHash()).startsWith("sha256:");
        org.assertj.core.api.Assertions.assertThat(imageJob.responseMetadata().toString())
            .doesNotContain("secret-key-1234");

        mockMvc.perform(get("/api/chain-runs/{chainRunId}/external-jobs", chainRunId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(12))
            .andExpect(jsonPath("$.data[*].providerJobId", hasItems(i40ProviderJobId)))
            .andExpect(jsonPath("$.data[*].retryPolicy", hasItems("FREE_PROVIDER_RETRY_ONLY")))
            .andExpect(content().string(not(containsString("secret-key-1234"))));
    }

    @Test
    void waitsForReviewWhenChainRagEvidenceHasUnresolvedConflicts() throws Exception {
        selectAllKeys("IMAGE");
        String projectId = createProject();
        ingestProjectKnowledge(projectId, "IMAGE", "I00", "PROJECT_STABLE_KNOWLEDGE", "stale-goal",
            "userGoal=旧目标; aspectRatio=1:1");

        mockMvc.perform(post("/api/projects/{projectId}/image-chain-runs", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userGoal\":\"生成一张9:16都市悬疑短剧封面\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("WAITING_REVIEW"))
            .andExpect(jsonPath("$.data.currentStageCode").value("I00"))
            .andExpect(jsonPath("$.data.blockingReason", containsString("RAG 证据字段冲突")))
            .andExpect(jsonPath("$.data.stageRuns.length()").value(1))
            .andExpect(jsonPath("$.data.stageRuns[0].status").value("WAITING_REVIEW"))
            .andExpect(jsonPath("$.data.stageRuns[0].retrievalRecordId").isEmpty())
            .andExpect(jsonPath("$.data.stageRuns[0].agentNodeRunIds.length()").value(0))
            .andExpect(jsonPath("$.data.stageRuns[0].providerJobIds.length()").value(0))
            .andExpect(jsonPath("$.data.artifacts.length()").value(0));
    }

    @Test
    void redoesRequestedStageAndDownstreamStagesUsingSnapshotEvidence() throws Exception {
        selectAllKeys("IMAGE");
        String projectId = createProject();

        String initialResponse = mockMvc.perform(post("/api/projects/{projectId}/image-chain-runs", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userGoal\":\"生成一张9:16都市悬疑短剧封面\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        String chainRunId = JsonTestSupport.extractString(initialResponse, "chainRunId");
        String i10StageRunId = JsonTestSupport.extractStageString(initialResponse, "I10", "stageRunId");
        String i20StageRunId = JsonTestSupport.extractStageString(initialResponse, "I20", "stageRunId");
        String i20ProviderJobId = JsonTestSupport.extractStageFirstArrayString(initialResponse, "I20",
            "providerJobIds");
        String i60ProviderJobId = JsonTestSupport.extractStageFirstArrayString(initialResponse, "I60",
            "providerJobIds");

        String redoResponse = mockMvc.perform(post("/api/stage-runs/{stageRunId}:redo", i20StageRunId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.chainRunId").value(chainRunId))
            .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
            .andExpect(jsonPath("$.data.currentStageCode").value("I60"))
            .andExpect(jsonPath("$.data.stageRuns[*].stageCode",
                hasItems("I00", "I10", "I20", "I30", "I40", "I50", "I60")))
            .andExpect(jsonPath("$.data.stageRuns[*].retrievalRecordId").isNotEmpty())
            .andExpect(jsonPath("$.data.stageRuns[*].handoffContextId").isNotEmpty())
            .andExpect(jsonPath("$.data.stageRuns[*].providerJobIds").isNotEmpty())
            .andExpect(content().string(not(containsString("secret-key-1234"))))
            .andReturn()
            .getResponse()
            .getContentAsString();

        String redoneI10StageRunId = JsonTestSupport.extractStageString(redoResponse, "I10", "stageRunId");
        String redoneI20StageRunId = JsonTestSupport.extractStageString(redoResponse, "I20", "stageRunId");
        String redoneI20ProviderJobId = JsonTestSupport.extractStageFirstArrayString(redoResponse, "I20",
            "providerJobIds");
        String redoneI60ProviderJobId = JsonTestSupport.extractStageFirstArrayString(redoResponse, "I60",
            "providerJobIds");

        org.assertj.core.api.Assertions.assertThat(redoneI10StageRunId).isEqualTo(i10StageRunId);
        org.assertj.core.api.Assertions.assertThat(redoneI20StageRunId).isNotEqualTo(i20StageRunId);
        org.assertj.core.api.Assertions.assertThat(redoneI20ProviderJobId).isNotEqualTo(i20ProviderJobId);
        org.assertj.core.api.Assertions.assertThat(redoneI60ProviderJobId).isNotEqualTo(i60ProviderJobId);
    }

    @Test
    void writesSchemaCompliantNextStageContextToRagForEachStage() throws Exception {
        selectAllKeys("IMAGE");
        String projectId = createProject();

        String response = mockMvc.perform(post("/api/projects/{projectId}/image-chain-runs", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userGoal\":\"生成一张9:16都市悬疑短剧封面\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.stageRuns[*].handoffContextId").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();

        String chainRunId = JsonTestSupport.extractString(response, "chainRunId");
        String i20StageRunId = JsonTestSupport.extractStageString(response, "I20", "stageRunId");
        String i20HandoffContextId = JsonTestSupport.extractStageString(response, "I20", "handoffContextId");
        String chainNamespace = "project:" + projectId + ":chain:" + chainRunId;
        KnowledgeChunk handoffChunk = knowledgeRepository.findChunk(i20HandoffContextId)
            .orElseThrow(() -> new AssertionError("handoff chunk missing: " + i20HandoffContextId));

        org.assertj.core.api.Assertions.assertThat(handoffChunk.namespace()).isEqualTo(chainNamespace);
        org.assertj.core.api.Assertions.assertThat(handoffChunk.stageCode()).isEqualTo("I30");
        org.assertj.core.api.Assertions.assertThat(handoffChunk.sourceType()).isEqualTo("NEXT_STAGE_CONTEXT");
        org.assertj.core.api.Assertions.assertThat(handoffChunk.sourceId()).isEqualTo(i20StageRunId);
        org.assertj.core.api.Assertions.assertThat(handoffChunk.content())
            .contains("\"schemaVersion\":\"1.0\"")
            .contains("\"chainRunId\":\"" + chainRunId + "\"")
            .contains("\"chainType\":\"IMAGE\"")
            .contains("\"fromStage\":\"I20\"")
            .contains("\"toStage\":\"I30\"")
            .contains("\"reviewReportId\":\"" + i20StageRunId + "-review\"")
            .contains("\"evidenceChunkIds\"")
            .doesNotContain("secret-key-1234");
        JsonNode context = JsonTestSupport.readTree(handoffChunk.content());
        JsonNode firstClaim = context.path("claims").path(0);
        org.assertj.core.api.Assertions.assertThat(firstClaim.path("claim").asText()).isNotBlank();
        org.assertj.core.api.Assertions.assertThat(firstClaim.path("critical").asBoolean()).isTrue();
        org.assertj.core.api.Assertions.assertThat(firstClaim.path("citationChunkIds").isEmpty()).isFalse();
        org.assertj.core.api.Assertions.assertThat(firstClaim.path("supported").asBoolean()).isTrue();
        org.assertj.core.api.Assertions.assertThat(context.at("/evidenceCheck/passed").asBoolean()).isTrue();
        org.assertj.core.api.Assertions.assertThat(context.at("/evidenceCheck/groundednessScore").asInt())
            .isGreaterThanOrEqualTo(95);
        org.assertj.core.api.Assertions.assertThat(context.at("/evidenceCheck/schemaCompliance").asInt())
            .isEqualTo(100);
        org.assertj.core.api.Assertions.assertThat(context.at("/evidenceCheck/unsupportedCriticalClaims").asInt())
            .isZero();
    }

    @Test
    void retrievesPreviousHandoffAndReviewReportForNextStageRagContext() throws Exception {
        selectAllKeys("IMAGE");
        String projectId = createProject();

        String response = mockMvc.perform(post("/api/projects/{projectId}/image-chain-runs", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userGoal\":\"生成一张9:16都市悬疑短剧封面\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        String chainRunId = JsonTestSupport.extractString(response, "chainRunId");
        String chainNamespace = "project:" + projectId + ":chain:" + chainRunId;
        List<String> sourceTypes = knowledgeRepository.retrieve(chainNamespace, ChainType.IMAGE, "I30",
                "生成一张9:16都市悬疑短剧封面", 10)
            .stream()
            .map(KnowledgeChunk::sourceType)
            .toList();

        org.assertj.core.api.Assertions.assertThat(sourceTypes)
            .contains("USER_GOAL", "STAGE_MAP", "CHAIN_CONTEXT", "NEXT_STAGE_CONTEXT", "REVIEW_REPORT");
    }

    @Test
    void runsVideoChainAsCompleteVideoWithVoiceWithoutOldAudioOrEditStages() throws Exception {
        selectAllKeys("VIDEO");
        String projectId = createProject();

        mockMvc.perform(post("/api/projects/{projectId}/video-chain-runs", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userGoal\":\"生成10秒9:16带人声配音的AI短剧\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.chainType").value("VIDEO"))
            .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
            .andExpect(jsonPath("$.data.stageRuns[*].stageCode",
                    hasItems("V00", "V10", "V20", "V30", "V40", "V50", "V60")))
            .andExpect(jsonPath("$.data.stageRuns[*].stageCode", not(hasItems("GENERATE_AUDIO", "SYNC_LIP_MOVEMENT"))))
            .andExpect(jsonPath("$.data.artifacts[*].artifactKind",
                    hasItems("VideoCandidateAssets", "FinalVideoArtifact", "VideoReviewReport")))
            .andExpect(jsonPath("$.data.artifacts[0].artifactKind").value("FinalVideoArtifact"))
            .andExpect(jsonPath("$.data.artifacts[0].metadata.durationSeconds").value(10))
            .andExpect(jsonPath("$.data.artifacts[0].metadata.aspectRatio").value("9:16"))
            .andExpect(jsonPath("$.data.artifacts[0].metadata.hasHumanVoice").value(true))
            .andExpect(jsonPath("$.data.artifacts[?(@.artifactKind == 'VideoCandidateAssets')].metadata.candidateCount",
                    hasItem(1)));
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

    private void selectAllKeys(String chainType) throws Exception {
        String[] capabilityTypes = chainType.equals("IMAGE")
                ? new String[] {"llm.text.free", "rag.embedding.free", "rag.rerank.free", "image.generate.free"}
                : new String[] {"llm.text.free", "rag.embedding.free", "rag.rerank.free",
                        "video.generate.full_with_voice.free"};

        for (String capabilityType : capabilityTypes) {
            String response = mockMvc.perform(post("/api/api-configs/{chainType}/{capabilityType}/keys",
                    chainType, capabilityType)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"provider\":\"fixture-free\",\"label\":\"fixture\",\"apiKey\":\"secret-key-1234\","
                            + "\"model\":\"fixture-contract\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maskedKey").value("****1234"))
                .andExpect(jsonPath("$.data.apiKey").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

            String apiKeyId = JsonTestSupport.extractString(response, "apiKeyId");

            mockMvc.perform(post("/api/api-keys/{apiKeyId}:verify", apiKeyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.freeModelGateStatus").value("PASSED"));

            mockMvc.perform(post("/api/api-keys/{apiKeyId}:select", apiKeyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isSelected").value(true));
        }
    }

    private void saveSelectedCredential(String capabilityType, FreeModelGateStatus gateStatus) {
        apiConfigRepository.save(new ApiCredential("direct-" + capabilityType, ChainType.IMAGE, capabilityType,
            "fixture-free", "direct", "hash", "encrypted", "****1234", "fixture-contract",
            ApiKeyStatus.ACTIVE, true, Instant.now(), gateStatus));
    }

    private void ingestProjectKnowledge(String projectId, String chainType, String stageCode, String sourceType,
            String sourceId, String content) throws Exception {
        mockMvc.perform(post("/api/knowledge:ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"namespace\":\"project:" + projectId + "\",\"chainType\":\"" + chainType
                    + "\",\"stageCode\":\"" + stageCode + "\",\"sourceType\":\"" + sourceType
                    + "\",\"sourceId\":\"" + sourceId + "\",\"content\":\"" + content + "\"}"))
            .andExpect(status().isOk());
    }
}
