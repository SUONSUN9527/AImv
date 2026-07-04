package com.aimv.domain.knowledge;

import com.aimv.domain.shared.ChainType;
import java.util.List;
import java.util.Optional;

public interface KnowledgeRepository {

    KnowledgeChunk save(KnowledgeChunk chunk, float[] denseVector, String embeddingModel);

    Optional<KnowledgeChunk> findChunk(String chunkId);

    /**
     * 返回某 namespace 下匹配 (chainType, stageCode) 的全部候选 chunk 及其 dense 向量，
     * 供应用层做 hybrid 排序。
     */
    List<EmbeddedChunk> retrieveEmbedded(String namespace, ChainType chainType, String stageCode);

    /**
     * 兼容旧调用：返回匹配 chunk（按 createdAt），不做语义排序。真实检索走
     * {@link #retrieveEmbedded} + HybridRanker。
     */
    default List<KnowledgeChunk> retrieve(String namespace, ChainType chainType, String stageCode, String query,
            int topK) {
        return retrieveEmbedded(namespace, chainType, stageCode).stream()
            .map(EmbeddedChunk::chunk)
            .limit(topK)
            .toList();
    }

    Optional<KnowledgeChunk> findLatestBySourceType(String namespace, ChainType chainType, String stageCode,
            String sourceType);

    boolean existsByNamespaceAndDifferentChainType(String namespace, ChainType chainType);

    long countByNamespace(String namespace);

    RetrievalRecord saveRecord(RetrievalRecord record);

    Optional<RetrievalRecord> findRecord(String retrievalRecordId);
}
