package com.aimv.domain.knowledge;

import com.aimv.domain.shared.ChainType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record EvidencePack(
        String schemaVersion,
        String retrievalRecordId,
        String namespace,
        ChainType chainType,
        String stageCode,
        RagCoverage coverage,
        List<String> citationChunkIds,
        List<String> requiredConstraints,
        List<CitedChunk> citations
) {

    private static final int SNIPPET_LIMIT = 240;

    public EvidencePack {
        citationChunkIds = List.copyOf(citationChunkIds);
        requiredConstraints = List.copyOf(requiredConstraints);
        citations = List.copyOf(citations);
    }

    public static EvidencePack from(String retrievalRecordId, String namespace, ChainType chainType,
            String stageCode, String query, RagCoverage coverage, List<KnowledgeChunk> chunks) {
        List<CitedChunk> citations = chunks.stream()
            .map(EvidencePack::citation)
            .toList();
        List<String> citationChunkIds = citations.stream()
            .map(CitedChunk::chunkId)
            .toList();
        return new EvidencePack("1.0", retrievalRecordId, namespace, chainType, stageCode,
            coverage, citationChunkIds, requiredConstraints(chainType, stageCode, query), citations);
    }

    public Map<String, Object> toPromptInput() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("schemaVersion", schemaVersion);
        input.put("retrievalRecordId", retrievalRecordId);
        input.put("namespace", namespace);
        input.put("chainType", chainType.name());
        input.put("stageCode", stageCode);
        input.put("coverage", coverageInput());
        input.put("citationChunkIds", citationChunkIds);
        input.put("requiredConstraints", requiredConstraints);
        input.put("citations", citations.stream()
            .map(CitedChunk::toPromptInput)
            .toList());
        return Map.copyOf(input);
    }

    private Map<String, Object> coverageInput() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("goal", coverage.goal());
        input.put("stageMap", coverage.stageMap());
        input.put("currentStage", coverage.currentStage());
        input.put("previousHandoff", coverage.previousHandoff());
        input.put("previousReviewReport", coverage.previousReviewReport());
        input.put("passed", coverage.passed());
        return Map.copyOf(input);
    }

    private static CitedChunk citation(KnowledgeChunk chunk) {
        return new CitedChunk(chunk.chunkId(), chunk.sourceType(), chunk.sourceId(), chunk.stageCode(),
            chunk.contentHash(), snippet(chunk.content()));
    }

    private static String snippet(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").strip();
        if (normalized.length() <= SNIPPET_LIMIT) {
            return normalized;
        }
        return normalized.substring(0, SNIPPET_LIMIT);
    }

    private static List<String> requiredConstraints(ChainType chainType, String stageCode, String query) {
        List<String> constraints = new ArrayList<>();
        constraints.add("chainType=" + chainType.name());
        constraints.add("stageCode=" + stageCode);
        if (chainType == ChainType.IMAGE) {
            constraints.add("imageCount=1");
        }
        if (chainType == ChainType.VIDEO) {
            constraints.add("durationSeconds=10");
            constraints.add("voiceoverRequirement=HUMAN_VOICE_REQUIRED");
        }
        if (query != null && query.contains("9:16")) {
            constraints.add("aspectRatio=9:16");
        }
        return List.copyOf(constraints);
    }

    public record CitedChunk(
            String chunkId,
            String sourceType,
            String sourceId,
            String stageCode,
            String contentHash,
            String contentSnippet
    ) {

        private Map<String, Object> toPromptInput() {
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("chunkId", chunkId);
            input.put("sourceType", sourceType);
            input.put("sourceId", sourceId);
            input.put("stageCode", stageCode);
            input.put("contentHash", contentHash);
            input.put("contentSnippet", contentSnippet);
            return Map.copyOf(input);
        }
    }
}
