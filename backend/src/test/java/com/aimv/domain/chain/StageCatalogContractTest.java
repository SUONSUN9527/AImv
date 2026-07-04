package com.aimv.domain.chain;

import static org.assertj.core.api.Assertions.assertThat;

import com.aimv.domain.chain.StageCatalog.StageDefinition;
import com.aimv.domain.shared.ChainType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StageCatalogContractTest {

    @Test
    void everyStageDeclaresSchemaRubricRetrievalPolicyAndRuntimeContracts() {
        assertStageContracts(ChainType.IMAGE, "image.generate.free");
        assertStageContracts(ChainType.VIDEO, "video.generate.full_with_voice.free");
    }

    @Test
    void catalogRubricVersionMatchesReviewPolicyVersion() {
        assertRubricVersions(ChainType.IMAGE);
        assertRubricVersions(ChainType.VIDEO);
    }

    @Test
    void stageRuntimeContractsDeclareCollaborationModeAndCapabilityWithoutSuffixInference() {
        StageDefinition imagePreflight = stage(ChainType.IMAGE, "I30");
        StageDefinition imageGeneration = stage(ChainType.IMAGE, "I40");
        StageDefinition imageDelivery = stage(ChainType.IMAGE, "I60");
        StageDefinition videoGeneration = stage(ChainType.VIDEO, "V40");

        assertThat(imagePreflight.collaborationMode()).isEqualTo("DIVIDE_AND_MERGE");
        assertThat(imagePreflight.capabilityType()).isEqualTo("rag.rerank.free");
        assertThat(imageGeneration.collaborationMode()).isEqualTo("CANDIDATE_SELECTION");
        assertThat(imageGeneration.capabilityType()).isEqualTo("image.generate.free");
        assertThat(videoGeneration.collaborationMode()).isEqualTo("CANDIDATE_SELECTION");
        assertThat(videoGeneration.capabilityType()).isEqualTo("video.generate.full_with_voice.free");
        assertThat(imageDelivery.collaborationMode()).isEqualTo("SINGLE_ACCEPTANCE");
        assertThat(imageDelivery.capabilityType()).isEqualTo("llm.text.free");
    }

    @Test
    void stageContractsDeclareDocumentedAgentRoles() {
        assertThat(stage(ChainType.IMAGE, "I00").agentNames()).containsExactly("GoalAgent");
        assertThat(stage(ChainType.IMAGE, "I10").agentNames())
            .containsExactly("SubjectAgent", "StyleAgent", "ConstraintAgent");
        assertThat(stage(ChainType.IMAGE, "I20").agentNames())
            .containsExactly("PromptAgent", "NegativePromptAgent", "PromptSafetyAgent");
        assertThat(stage(ChainType.IMAGE, "I30").agentNames())
            .containsExactly("CapabilityAgent", "ProviderFitAgent");
        assertThat(stage(ChainType.IMAGE, "I40").agentNames()).containsExactly("ImageGenerationAgent");
        assertThat(stage(ChainType.IMAGE, "I50").agentNames())
            .containsExactly("VisualQualityAgent", "GoalMatchAgent", "SafetyReviewAgent");
        assertThat(stage(ChainType.IMAGE, "I60").agentNames()).containsExactly("ImageAcceptanceAgent");

        assertThat(stage(ChainType.VIDEO, "V00").agentNames()).containsExactly("GoalAgent");
        assertThat(stage(ChainType.VIDEO, "V10").agentNames())
            .containsExactly("StoryAgent", "VisualAgent", "MotionAgent", "ConstraintAgent");
        assertThat(stage(ChainType.VIDEO, "V20").agentNames())
            .containsExactly("PromptAgent", "MotionPromptAgent", "ContinuityAgent", "PromptSafetyAgent");
        assertThat(stage(ChainType.VIDEO, "V30").agentNames())
            .containsExactly("CapabilityAgent", "ProviderFitAgent");
        assertThat(stage(ChainType.VIDEO, "V40").agentNames()).containsExactly("VideoGenerationAgent");
        assertThat(stage(ChainType.VIDEO, "V50").agentNames())
            .containsExactly("DecodeReviewAgent", "MotionQualityAgent", "GoalMatchAgent", "VoiceReviewAgent",
                "SafetyReviewAgent");
        assertThat(stage(ChainType.VIDEO, "V60").agentNames()).containsExactly("VideoAcceptanceAgent");
    }

    @Test
    void divideAndMergeStagesDeclareDocumentedMergePriority() {
        assertThat(stage(ChainType.IMAGE, "I10").mergePriorityAgentNames())
            .containsExactly("ConstraintAgent", "SubjectAgent", "StyleAgent");
        assertThat(stage(ChainType.IMAGE, "I30").mergePriorityAgentNames())
            .containsExactly("CapabilityAgent", "ProviderFitAgent");
        assertThat(stage(ChainType.VIDEO, "V10").mergePriorityAgentNames())
            .containsExactly("ConstraintAgent", "StoryAgent", "MotionAgent", "VisualAgent");
        assertThat(stage(ChainType.VIDEO, "V20").mergePriorityAgentNames())
            .containsExactly("PromptSafetyAgent", "ContinuityAgent", "PromptAgent", "MotionPromptAgent");
        assertThat(stage(ChainType.VIDEO, "V30").mergePriorityAgentNames())
            .containsExactly("CapabilityAgent", "ProviderFitAgent");
    }

    @Test
    void divideAndMergeStagesDeclarePartialSchemasForEveryAgent() {
        StageCatalog.stages(ChainType.IMAGE).stream()
            .filter(stage -> "DIVIDE_AND_MERGE".equals(stage.collaborationMode()))
            .forEach(this::assertPartialSchemasCoverAgents);
        StageCatalog.stages(ChainType.VIDEO).stream()
            .filter(stage -> "DIVIDE_AND_MERGE".equals(stage.collaborationMode()))
            .forEach(this::assertPartialSchemasCoverAgents);
    }

    @Test
    void goalStagesDeclareGoalAgentPartialSchemas() {
        assertPartialSchemasCoverAgents(stage(ChainType.IMAGE, "I00"));
        assertPartialSchemasCoverAgents(stage(ChainType.VIDEO, "V00"));
    }

    private void assertStageContracts(ChainType chainType, String generationCapabilityType) {
        List<StageDefinition> stages = StageCatalog.stages(chainType);

        assertThat(stages).hasSize(7);
        assertThat(stages).extracting(StageDefinition::stageCode).doesNotHaveDuplicates();
        assertThat(stages).allSatisfy(stage -> {
            assertThat(stage.stageName()).isNotBlank();
            assertThat(stage.inputSchemaId()).isNotBlank().endsWith(".v1");
            assertThat(stage.outputSchemaId()).isNotBlank().endsWith(".v1");
            assertThat(stage.rubricVersion()).isNotBlank().endsWith(".v1");
            assertThat(stage.retrievalPolicyId()).isNotBlank().endsWith(".v1");
            assertThat(stage.collaborationMode()).isIn("DIVIDE_AND_MERGE", "CANDIDATE_SELECTION",
                "SINGLE_ACCEPTANCE");
            assertThat(stage.capabilityType()).isIn("llm.text.free", "rag.rerank.free",
                generationCapabilityType);
            assertThat(stage.agentNames()).isNotEmpty().doesNotHaveDuplicates();
        });
    }

    private void assertPartialSchemasCoverAgents(StageDefinition stage) {
        assertThat(stage.partialSchemas()).isNotEmpty();
        assertThat(stage.partialSchemas())
            .extracting(StagePartialSchema::agentName)
            .containsExactlyElementsOf(stage.agentNames());
        assertThat(stage.partialSchemas()).allSatisfy(partialSchema -> {
            assertThat(partialSchema.requiredFieldNames()).isNotEmpty();
            assertThat(partialSchema.allowedFieldNames()).containsAll(partialSchema.requiredFieldNames());
        });
    }

    private void assertRubricVersions(ChainType chainType) {
        StageCatalog.stages(chainType).forEach(stage ->
            assertThat(StageReviewPolicy.review(chainType, stage.stageCode(), stage.stageName(),
                passingMetadata(stage.stageCode())).rubricVersion())
                .isEqualTo(stage.rubricVersion()));
    }

    private Map<String, Object> passingMetadata(String stageCode) {
        if ("I00".equals(stageCode)) {
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
        if ("V00".equals(stageCode)) {
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
        if ("I40".equals(stageCode)) {
            return Map.of("candidateCount", 4, "aspectRatio", "9:16", "artifactIntegrityScore", 100,
                "providerJobIds", List.of("provider-job-i40"));
        }
        if ("V40".equals(stageCode)) {
            return Map.of("candidateCount", 1, "durationSeconds", 10, "aspectRatio", "9:16",
                "decodeIntegrityScore", 100, "hasHumanVoice", true,
                "providerJobIds", List.of("provider-job-v40"));
        }
        if ("I50".equals(stageCode)) {
            return Map.of("finalScore", 96, "safetyScore", 100, "artifactIntegrityScore", 100);
        }
        if ("V50".equals(stageCode)) {
            return Map.of("finalScore", 96, "decodeIntegrityScore", 100, "safetyScore", 100,
                "shortDramaScore", 92, "humanVoiceAudible", true);
        }
        return Map.of();
    }

    private StageDefinition stage(ChainType chainType, String stageCode) {
        return StageCatalog.stages(chainType).stream()
            .filter(candidate -> candidate.stageCode().equals(stageCode))
            .findFirst()
            .orElseThrow();
    }
}
