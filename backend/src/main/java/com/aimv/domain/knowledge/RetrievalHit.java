package com.aimv.domain.knowledge;

/**
 * 一次检索中命中的 chunk 及其分项分数（关键词、向量、rerank），按最终融合顺序排列。
 */
public record RetrievalHit(
        String chunkId,
        double keywordScore,
        double vectorScore,
        Double rerankScore
) {
}
