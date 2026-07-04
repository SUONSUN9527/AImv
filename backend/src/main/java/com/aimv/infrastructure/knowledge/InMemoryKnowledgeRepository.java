package com.aimv.infrastructure.knowledge;

import com.aimv.domain.knowledge.EmbeddedChunk;
import com.aimv.domain.knowledge.KnowledgeChunk;
import com.aimv.domain.knowledge.KnowledgeRepository;
import com.aimv.domain.knowledge.RetrievalRecord;
import com.aimv.domain.shared.ChainType;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!postgres")
public class InMemoryKnowledgeRepository implements KnowledgeRepository {

    private final Map<String, StoredChunk> chunks = new ConcurrentHashMap<>();
    private final Map<String, RetrievalRecord> records = new ConcurrentHashMap<>();

    @Override
    public KnowledgeChunk save(KnowledgeChunk chunk, float[] denseVector, String embeddingModel) {
        chunks.put(chunk.chunkId(), new StoredChunk(chunk, denseVector.clone(), embeddingModel));
        return chunk;
    }

    @Override
    public Optional<KnowledgeChunk> findChunk(String chunkId) {
        StoredChunk stored = chunks.get(chunkId);
        return Optional.ofNullable(stored).map(StoredChunk::chunk);
    }

    @Override
    public List<EmbeddedChunk> retrieveEmbedded(String namespace, ChainType chainType, String stageCode) {
        return chunks.values().stream()
            .filter(stored -> stored.chunk().namespace().equals(namespace))
            .filter(stored -> stored.chunk().chainType() == chainType)
            .filter(stored -> stored.chunk().stageCode().equals(stageCode))
            .sorted(Comparator.comparing(stored -> stored.chunk().createdAt()))
            .map(stored -> new EmbeddedChunk(stored.chunk(), stored.denseVector(), stored.embeddingModel()))
            .toList();
    }

    @Override
    public Optional<KnowledgeChunk> findLatestBySourceType(String namespace, ChainType chainType, String stageCode,
            String sourceType) {
        return chunks.values().stream()
            .map(StoredChunk::chunk)
            .filter(chunk -> chunk.namespace().equals(namespace))
            .filter(chunk -> chunk.chainType() == chainType)
            .filter(chunk -> chunk.stageCode().equals(stageCode))
            .filter(chunk -> chunk.sourceType().equals(sourceType))
            .max(Comparator.comparing(KnowledgeChunk::createdAt));
    }

    @Override
    public long countByNamespace(String namespace) {
        return chunks.values().stream()
            .filter(stored -> stored.chunk().namespace().equals(namespace))
            .count();
    }

    @Override
    public boolean existsByNamespaceAndDifferentChainType(String namespace, ChainType chainType) {
        return chunks.values().stream()
            .anyMatch(stored -> stored.chunk().namespace().equals(namespace)
                && stored.chunk().chainType() != chainType);
    }

    @Override
    public RetrievalRecord saveRecord(RetrievalRecord record) {
        records.put(record.retrievalRecordId(), record);
        return record;
    }

    @Override
    public Optional<RetrievalRecord> findRecord(String retrievalRecordId) {
        return Optional.ofNullable(records.get(retrievalRecordId));
    }

    private record StoredChunk(
            KnowledgeChunk chunk,
            float[] denseVector,
            String embeddingModel
    ) {
    }
}
