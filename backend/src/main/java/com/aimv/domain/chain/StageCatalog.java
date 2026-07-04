package com.aimv.domain.chain;

import com.aimv.domain.shared.ChainType;
import java.util.List;

public final class StageCatalog {

    private static final String LLM_TEXT_CAPABILITY = "llm.text.free";
    private static final String RAG_RERANK_CAPABILITY = "rag.rerank.free";
    private static final String IMAGE_GENERATION_CAPABILITY = "image.generate.free";
    private static final String VIDEO_GENERATION_CAPABILITY = "video.generate.full_with_voice.free";
    private static final String DIVIDE_AND_MERGE = "DIVIDE_AND_MERGE";
    private static final String CANDIDATE_SELECTION = "CANDIDATE_SELECTION";
    private static final String SINGLE_ACCEPTANCE = "SINGLE_ACCEPTANCE";

    private static final List<StageDefinition> IMAGE_STAGES = List.of(
        imageStage("I00", "目标锁定", SINGLE_ACCEPTANCE, LLM_TEXT_CAPABILITY, "GoalAgent"),
        imageStage("I10", "视觉方案", DIVIDE_AND_MERGE, LLM_TEXT_CAPABILITY,
            List.of("ConstraintAgent", "SubjectAgent", "StyleAgent"), "SubjectAgent", "StyleAgent",
            "ConstraintAgent"),
        imageStage("I20", "图片提示词包", DIVIDE_AND_MERGE, LLM_TEXT_CAPABILITY, "PromptAgent",
            "NegativePromptAgent", "PromptSafetyAgent"),
        imageStage("I30", "图片能力预检", DIVIDE_AND_MERGE, RAG_RERANK_CAPABILITY,
            List.of("CapabilityAgent", "ProviderFitAgent"), "CapabilityAgent", "ProviderFitAgent"),
        imageStage("I40", "图片生成", CANDIDATE_SELECTION, IMAGE_GENERATION_CAPABILITY, "ImageGenerationAgent"),
        imageStage("I50", "图片质量评审", CANDIDATE_SELECTION, LLM_TEXT_CAPABILITY, "VisualQualityAgent",
            "GoalMatchAgent", "SafetyReviewAgent"),
        imageStage("I60", "图片验收交付", SINGLE_ACCEPTANCE, LLM_TEXT_CAPABILITY, "ImageAcceptanceAgent")
    );

    private static final List<StageDefinition> VIDEO_STAGES = List.of(
        videoStage("V00", "目标锁定", SINGLE_ACCEPTANCE, LLM_TEXT_CAPABILITY, "GoalAgent"),
        videoStage("V10", "短片方案", DIVIDE_AND_MERGE, LLM_TEXT_CAPABILITY,
            List.of("ConstraintAgent", "StoryAgent", "MotionAgent", "VisualAgent"), "StoryAgent", "VisualAgent",
            "MotionAgent", "ConstraintAgent"),
        videoStage("V20", "完整视频提示词包", DIVIDE_AND_MERGE, LLM_TEXT_CAPABILITY,
            List.of("PromptSafetyAgent", "ContinuityAgent", "PromptAgent", "MotionPromptAgent"), "PromptAgent",
            "MotionPromptAgent", "ContinuityAgent", "PromptSafetyAgent"),
        videoStage("V30", "视频能力预检", DIVIDE_AND_MERGE, RAG_RERANK_CAPABILITY,
            List.of("CapabilityAgent", "ProviderFitAgent"), "CapabilityAgent", "ProviderFitAgent"),
        videoStage("V40", "完整短片生成", CANDIDATE_SELECTION, VIDEO_GENERATION_CAPABILITY,
            "VideoGenerationAgent"),
        videoStage("V50", "视频质量评审", CANDIDATE_SELECTION, LLM_TEXT_CAPABILITY, "DecodeReviewAgent",
            "MotionQualityAgent", "GoalMatchAgent", "VoiceReviewAgent", "SafetyReviewAgent"),
        videoStage("V60", "视频验收交付", SINGLE_ACCEPTANCE, LLM_TEXT_CAPABILITY, "VideoAcceptanceAgent")
    );

    private StageCatalog() {
    }

    public static List<StageDefinition> stages(ChainType chainType) {
        return chainType == ChainType.IMAGE ? IMAGE_STAGES : VIDEO_STAGES;
    }

    private static StageDefinition imageStage(String stageCode, String stageName, String collaborationMode,
            String capabilityType, String... agentNames) {
        return stage(ChainType.IMAGE, stageCode, stageName, collaborationMode, capabilityType,
            List.of(agentNames), List.of());
    }

    private static StageDefinition imageStage(String stageCode, String stageName, String collaborationMode,
            String capabilityType, List<String> mergePriorityAgentNames, String... agentNames) {
        return stage(ChainType.IMAGE, stageCode, stageName, collaborationMode, capabilityType,
            List.of(agentNames), mergePriorityAgentNames);
    }

    private static StageDefinition videoStage(String stageCode, String stageName, String collaborationMode,
            String capabilityType, String... agentNames) {
        return stage(ChainType.VIDEO, stageCode, stageName, collaborationMode, capabilityType,
            List.of(agentNames), List.of());
    }

    private static StageDefinition videoStage(String stageCode, String stageName, String collaborationMode,
            String capabilityType, List<String> mergePriorityAgentNames, String... agentNames) {
        return stage(ChainType.VIDEO, stageCode, stageName, collaborationMode, capabilityType,
            List.of(agentNames), mergePriorityAgentNames);
    }

    private static StageDefinition stage(ChainType chainType, String stageCode, String stageName,
            String collaborationMode, String capabilityType, List<String> agentNames,
            List<String> mergePriorityAgentNames) {
        String chainPrefix = chainType.name().toLowerCase();
        return new StageDefinition(
            stageCode,
            stageName,
            chainPrefix + "-" + stageCode + "-input.v1",
            chainPrefix + "-" + stageCode + "-output.v1",
            chainPrefix + "-" + stageCode + ".v1",
            chainPrefix + "-" + stageCode + "-retrieval.v1",
            collaborationMode,
            capabilityType,
            List.copyOf(agentNames),
            List.copyOf(mergePriorityAgentNames),
            partialSchemas(stageCode)
        );
    }

    private static List<StagePartialSchema> partialSchemas(String stageCode) {
        return switch (stageCode) {
            case "I00" -> List.of(
                schema("GoalAgent", List.of("subject", "scene", "style", "aspectRatio", "count",
                    "goalClarityScore", "safetyScore"), List.of("subject", "scene", "style", "aspectRatio",
                    "count", "goalClarityScore", "safetyScore", "size", "purpose", "forbiddenContent"))
            );
            case "I10" -> List.of(
                schema("SubjectAgent", List.of("subject"), List.of("subject", "aspectRatio", "identityNotes")),
                schema("StyleAgent", List.of("palette"), List.of("palette", "aspectRatio", "style", "lighting",
                    "composition")),
                schema("ConstraintAgent", List.of("aspectRatio", "forbiddenTerms"), List.of("aspectRatio",
                    "forbiddenTerms", "safetyBoundary", "brandForbiddenTerms"))
            );
            case "I20" -> List.of(
                schema("PromptAgent", List.of("positivePrompt", "promptVariables"), List.of("positivePrompt",
                    "aspectRatio", "promptVariables")),
                schema("NegativePromptAgent", List.of("negativePrompt"), List.of("negativePrompt",
                    "excludedTerms")),
                schema("PromptSafetyAgent", List.of("safetyPassed"), List.of("safetyPassed", "vetoReason",
                    "sensitiveTerms"))
            );
            case "I30", "V30" -> List.of(
                schema("CapabilityAgent", List.of("providerId", "apiKeyId"), List.of("providerId", "apiKeyId",
                    "model", "freeModelGatePassed", "durationSeconds", "aspectRatio",
                    "nativeHumanVoiceSupported")),
                schema("ProviderFitAgent", List.of("selectedProviderId"), List.of("selectedProviderId", "reason",
                    "retryPolicy"))
            );
            case "V00" -> List.of(
                schema("GoalAgent", List.of("theme", "durationSeconds", "aspectRatio", "style",
                    "voiceoverRequirement", "outputFormat", "goalClarityScore", "safetyScore"), List.of("theme",
                    "durationSeconds", "aspectRatio", "style", "voiceoverRequirement", "outputFormat",
                    "goalClarityScore", "safetyScore", "subtitleRequired", "storyGoal"))
            );
            case "V10" -> List.of(
                schema("StoryAgent", List.of("story"), List.of("story", "hook", "emotionalCurve")),
                schema("VisualAgent", List.of("visualStyle"), List.of("visualStyle", "scene", "palette",
                    "aspectRatio")),
                schema("MotionAgent", List.of("motion"), List.of("motion", "cameraMove", "rhythm")),
                schema("ConstraintAgent", List.of("aspectRatio", "durationSeconds"), List.of("aspectRatio",
                    "durationSeconds", "nativeVoiceRequired", "forbiddenTerms"))
            );
            case "V20" -> List.of(
                schema("PromptAgent", List.of("positivePrompt", "durationSeconds", "aspectRatio",
                    "voiceoverRequirement"), List.of("positivePrompt", "durationSeconds",
                    "aspectRatio", "voiceoverRequirement", "continuityConstraintRefs",
                    "characterContinuityRefs", "motionPromptRefs", "visualStyleRefs")),
                schema("MotionPromptAgent", List.of("motionPrompt"), List.of("motionPrompt", "cameraMove",
                    "rhythm")),
                schema("ContinuityAgent", List.of("continuityConstraints", "characterContinuity",
                    "visualStyleConstraint"), List.of("continuityConstraints", "characterContinuity",
                    "sceneContinuity", "visualStyleConstraint")),
                schema("PromptSafetyAgent", List.of("safetyPassed"), List.of("safetyPassed", "vetoReason",
                    "sensitiveTerms"))
            );
            default -> List.of();
        };
    }

    private static StagePartialSchema schema(String agentName, List<String> requiredFieldNames,
            List<String> allowedFieldNames) {
        return new StagePartialSchema(agentName, requiredFieldNames, allowedFieldNames);
    }

    public record StageDefinition(
            String stageCode,
            String stageName,
            String inputSchemaId,
            String outputSchemaId,
            String rubricVersion,
            String retrievalPolicyId,
            String collaborationMode,
            String capabilityType,
            List<String> agentNames,
            List<String> mergePriorityAgentNames,
            List<StagePartialSchema> partialSchemas
    ) {
    }
}
