package com.aimv.domain.knowledge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 混合检索排序：对候选 chunk 分别计算向量余弦分和关键词重合分，各自排名后用
 * Reciprocal Rank Fusion (RRF) 融合成最终顺序（技术文档 11.3）。纯函数、无外部依赖，
 * 内存仓库和 Postgres 仓库共用同一套排序逻辑，保证两端结果一致、可测试。
 */
public final class HybridRanker {

    private static final int RRF_K = 60;

    private HybridRanker() {
    }

    public static List<RetrievalHit> rank(String query, float[] queryVector, List<EmbeddedChunk> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        Set<String> queryFeatures = TextSimilarity.features(query);
        List<Scored> scored = new ArrayList<>();
        for (EmbeddedChunk candidate : candidates) {
            double vectorScore = DeterministicEmbedding.cosine(queryVector, candidate.denseVector());
            double keywordScore = TextSimilarity.overlap(queryFeatures, candidate.chunk().content());
            scored.add(new Scored(candidate.chunk().chunkId(), candidate.chunk().createdAt().toEpochMilli(),
                keywordScore, vectorScore));
        }

        Map<String, Integer> vectorRank = ranks(scored, Comparator
            .comparingDouble(Scored::vectorScore).reversed()
            .thenComparingLong(Scored::createdAtMillis)
            .thenComparing(Scored::chunkId));
        Map<String, Integer> keywordRank = ranks(scored, Comparator
            .comparingDouble(Scored::keywordScore).reversed()
            .thenComparingLong(Scored::createdAtMillis)
            .thenComparing(Scored::chunkId));

        scored.sort(Comparator
            .comparingDouble((Scored candidate) -> rrf(vectorRank.get(candidate.chunkId()))
                + rrf(keywordRank.get(candidate.chunkId())))
            .reversed()
            .thenComparingLong(Scored::createdAtMillis)
            .thenComparing(Scored::chunkId));

        List<RetrievalHit> hits = new ArrayList<>();
        for (Scored candidate : scored) {
            hits.add(new RetrievalHit(candidate.chunkId(), round(candidate.keywordScore()),
                round(candidate.vectorScore()), null));
        }
        return List.copyOf(hits);
    }

    private static double rrf(int rank) {
        return 1.0 / (RRF_K + rank);
    }

    private static Map<String, Integer> ranks(List<Scored> scored, Comparator<Scored> comparator) {
        List<Scored> ordered = new ArrayList<>(scored);
        ordered.sort(comparator);
        Map<String, Integer> ranks = new LinkedHashMap<>();
        for (int index = 0; index < ordered.size(); index++) {
            ranks.put(ordered.get(index).chunkId(), index + 1);
        }
        return ranks;
    }

    private static double round(double value) {
        return Math.round(value * 100000.0) / 100000.0;
    }

    private record Scored(
            String chunkId,
            long createdAtMillis,
            double keywordScore,
            double vectorScore
    ) {
    }
}
