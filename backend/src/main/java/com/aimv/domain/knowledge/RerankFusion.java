package com.aimv.domain.knowledge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 把 rerank 相关性分数应用到 hybrid 融合后的候选顺序上：对前 candidateLimit 个候选按
 * rerank 分数降序重排（分数写入 hit.rerankScore），其余保持融合顺序追加在后。
 * rerank 分数缺失或数量不匹配时原样返回融合顺序（不写 rerankScore）。纯函数，便于测试。
 */
public final class RerankFusion {

    private RerankFusion() {
    }

    public static List<RetrievalHit> apply(List<RetrievalHit> fusedHits, List<Double> rerankScores,
            int candidateLimit) {
        if (fusedHits.isEmpty()) {
            return List.of();
        }
        int limit = Math.min(candidateLimit, fusedHits.size());
        if (rerankScores == null || rerankScores.size() != limit) {
            return fusedHits;
        }

        List<RankedCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < limit; index++) {
            RetrievalHit hit = fusedHits.get(index);
            candidates.add(new RankedCandidate(index, rerankScores.get(index), hit));
        }
        candidates.sort(Comparator
            .comparingDouble(RankedCandidate::rerankScore).reversed()
            .thenComparingInt(RankedCandidate::fusedOrder));

        List<RetrievalHit> reordered = new ArrayList<>(fusedHits.size());
        for (RankedCandidate candidate : candidates) {
            RetrievalHit hit = candidate.hit();
            reordered.add(new RetrievalHit(hit.chunkId(), hit.keywordScore(), hit.vectorScore(),
                round(candidate.rerankScore())));
        }
        for (int index = limit; index < fusedHits.size(); index++) {
            reordered.add(fusedHits.get(index));
        }
        return List.copyOf(reordered);
    }

    private static double round(double value) {
        return Math.round(value * 100000.0) / 100000.0;
    }

    private record RankedCandidate(
            int fusedOrder,
            double rerankScore,
            RetrievalHit hit
    ) {
    }
}
