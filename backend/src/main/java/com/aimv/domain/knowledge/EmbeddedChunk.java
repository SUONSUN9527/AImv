package com.aimv.domain.knowledge;

/**
 * 一个知识 chunk 及其 dense 向量。检索候选集在应用层按 hybrid 排序时使用。
 */
public record EmbeddedChunk(
        KnowledgeChunk chunk,
        float[] denseVector,
        String embeddingModel
) {
}
