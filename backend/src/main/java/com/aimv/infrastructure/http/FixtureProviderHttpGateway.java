package com.aimv.infrastructure.http;

import com.aimv.domain.knowledge.DeterministicEmbedding;
import com.aimv.domain.provider.ProviderHttpRequest;
import com.aimv.domain.provider.ProviderHttpResponse;
import com.aimv.domain.provider.ProviderCapabilityEvidence;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class FixtureProviderHttpGateway {

    ProviderHttpResponse invoke(ProviderHttpRequest request) {
        return new ProviderHttpResponse(
            "provider-job-" + UUID.randomUUID(),
            "SUCCEEDED",
            request.stageCode() + " 通过 fixture HTTP adapter 完成，未调用本地模型或付费模型",
            artifactRefs(request),
            metadata(request),
            "fixture-free-quota:available"
        );
    }

    private List<String> artifactRefs(ProviderHttpRequest request) {
        if ("I40".equals(request.stageCode())) {
            return List.of(
                "/assets/fixture/image-candidate-1.svg",
                "/assets/fixture/image-candidate-2.svg",
                "/assets/fixture/image-candidate-3.svg",
                "/assets/fixture/image-candidate-4.svg"
            );
        }
        if ("V40".equals(request.stageCode())) {
            return List.of("/assets/fixture/final-video.mp4");
        }
        return List.of();
    }

    private Map<String, Object> metadata(ProviderHttpRequest request) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("adapterKind", "HTTP_ADAPTER");
        metadata.put("capabilityType", request.capabilityType());
        metadata.put("stageCode", request.stageCode());
        metadata.putAll(stageEvidence(request));
        if ("rag.embedding.free".equals(request.capabilityType())) {
            metadata.put("denseVector", deterministicEmbedding(request));
            metadata.put("embeddingModel", DeterministicEmbedding.MODEL_NAME);
        }
        if ("rag.rerank.free".equals(request.capabilityType())) {
            metadata.put("rerankScores", deterministicRerankScores(request));
        }
        Map<String, Object> partialOutput = partialOutput(request);
        if (!partialOutput.isEmpty()) {
            metadata.put("partialOutput", partialOutput);
        }
        return Map.copyOf(metadata);
    }

    private List<Double> deterministicEmbedding(ProviderHttpRequest request) {
        float[] vector = DeterministicEmbedding.embed(embeddingText(request));
        List<Double> values = new java.util.ArrayList<>(vector.length);
        for (float value : vector) {
            values.add((double) value);
        }
        return List.copyOf(values);
    }

    private String embeddingText(ProviderHttpRequest request) {
        if (request.input() == null) {
            return "";
        }
        Object text = request.input().get("text");
        if (text instanceof String value && !value.isBlank()) {
            return value;
        }
        Object query = request.input().get("query");
        return query instanceof String value ? value : String.valueOf(request.input());
    }

    private List<Double> deterministicRerankScores(ProviderHttpRequest request) {
        String query = request.input() == null ? "" : String.valueOf(request.input().get("query"));
        Object documentsValue = request.input() == null ? null : request.input().get("documents");
        if (!(documentsValue instanceof List<?> documents) || documents.isEmpty()) {
            return List.of();
        }
        java.util.Set<String> queryFeatures = com.aimv.domain.knowledge.TextSimilarity.features(query);
        List<Double> scores = new java.util.ArrayList<>(documents.size());
        for (Object document : documents) {
            scores.add(com.aimv.domain.knowledge.TextSimilarity.overlap(queryFeatures,
                String.valueOf(document)));
        }
        return List.copyOf(scores);
    }

    private Map<String, Object> stageEvidence(ProviderHttpRequest request) {
        if ("I40".equals(request.stageCode())) {
            return Map.of(
                "candidateCount", 4,
                "aspectRatio", "9:16",
                "artifactIntegrityScore", 100
            );
        }
        if ("V40".equals(request.stageCode())) {
            return Map.of(
                "completeShortVideoSupported", true,
                "nativeHumanVoiceSupported", true,
                "candidateCount", 1,
                "durationSeconds", 10,
                "aspectRatio", "9:16",
                "decodeIntegrityScore", 100,
                "hasHumanVoice", true
            );
        }
        if ("I50".equals(request.stageCode())) {
            return Map.of("finalScore", 96, "safetyScore", 100, "artifactIntegrityScore", 100);
        }
        if ("V50".equals(request.stageCode())) {
            return Map.of(
                "finalScore", 92,
                "decodeIntegrityScore", 100,
                "safetyScore", 100,
                "shortDramaScore", 92,
                "humanVoiceAudible", true
            );
        }
        if (ProviderCapabilityEvidence.VIDEO_FULL_WITH_VOICE_CAPABILITY.equals(request.capabilityType())) {
            return Map.of(
                "completeShortVideoSupported", true,
                "nativeHumanVoiceSupported", true,
                "durationSeconds", 10,
                "aspectRatio", "9:16",
                "hasHumanVoice", true
            );
        }
        return Map.of();
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
            case "CapabilityAgent" -> capabilityPartialOutput(request);
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
            case "VisualQualityAgent" -> Map.of("visualQualityScore", 95, "artifactIntegrityScore", 100);
            case "GoalMatchAgent" -> goalMatchPartialOutput(request);
            case "SafetyReviewAgent" -> Map.of("safetyScore", 100);
            case "DecodeReviewAgent" -> Map.of("decodeIntegrityScore", 100);
            case "MotionQualityAgent" -> Map.of("motionQualityScore", 92);
            case "VoiceReviewAgent" -> Map.of("voiceoverQualityScore", 93, "humanVoiceAudible", true);
            default -> Map.of();
        };
    }

    private Map<String, Object> goalMatchPartialOutput(ProviderHttpRequest request) {
        if (request.stageCode().startsWith("V")) {
            return Map.of("goalMatchScore", 92, "promptConsistencyScore", 92, "shortDramaScore", 92);
        }
        return Map.of("goalMatchScore", 96, "promptConsistencyScore", 95);
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

    private Map<String, Object> capabilityPartialOutput(ProviderHttpRequest request) {
        return Map.of(
            "providerId", request.provider(),
            "apiKeyId", request.apiKeyId(),
            "model", request.model(),
            "freeModelGatePassed", true
        );
    }
}
