package com.aimv.infrastructure.knowledge;

import com.aimv.domain.knowledge.EmbeddedChunk;
import com.aimv.domain.knowledge.KnowledgeChunk;
import com.aimv.domain.knowledge.KnowledgeRepository;
import com.aimv.domain.knowledge.RagCoverage;
import com.aimv.domain.knowledge.RetrievalHit;
import com.aimv.domain.knowledge.RetrievalRecord;
import com.aimv.domain.shared.ChainType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("postgres")
public class PostgresKnowledgeRepository implements KnowledgeRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PostgresKnowledgeRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public KnowledgeChunk save(KnowledgeChunk chunk, float[] denseVector, String embeddingModel) {
        String documentId = "doc-" + UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO knowledge_document (
                id, chain_type, namespace, source_type, source_id, stage_code, language,
                visibility, content_hash, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, 'zh-CN', 'CHAIN_PRIVATE', ?, ?)
            """, documentId, chunk.chainType().name(), chunk.namespace(), chunk.sourceType(), chunk.sourceId(),
            chunk.stageCode(), chunk.contentHash(), Timestamp.from(chunk.createdAt()));
        jdbcTemplate.update("""
            INSERT INTO knowledge_chunk (
                id, document_id, namespace, chain_type, stage_code, source_type, source_id,
                chunk_index, content, content_summary, token_count, language, content_hash, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, left(?, 256), ?, 'zh-CN', ?, ?)
            """, chunk.chunkId(), documentId, chunk.namespace(), chunk.chainType().name(), chunk.stageCode(),
            chunk.sourceType(), chunk.sourceId(), chunk.content(), chunk.content(), chunk.content().length(),
            chunk.contentHash(), Timestamp.from(chunk.createdAt()));
        jdbcTemplate.update("""
            INSERT INTO knowledge_embedding (
                id, chunk_id, embedding_model, embedding_dimension, dense_vector, created_at
            ) VALUES (?, ?, ?, ?, ?::vector, ?)
            ON CONFLICT (chunk_id, embedding_model) DO UPDATE
            SET dense_vector = EXCLUDED.dense_vector,
                embedding_dimension = EXCLUDED.embedding_dimension
            """, "emb-" + UUID.randomUUID(), chunk.chunkId(), embeddingModel, denseVector.length,
            toVectorLiteral(denseVector), Timestamp.from(chunk.createdAt()));
        return chunk;
    }

    @Override
    public Optional<KnowledgeChunk> findChunk(String chunkId) {
        List<KnowledgeChunk> chunks = jdbcTemplate.query("""
            SELECT id, namespace, chain_type, stage_code, source_type, source_id,
                   content, content_hash, created_at
            FROM knowledge_chunk
            WHERE id = ?
            """, this::mapChunk, chunkId);
        return chunks.stream().findFirst();
    }

    @Override
    public List<EmbeddedChunk> retrieveEmbedded(String namespace, ChainType chainType, String stageCode) {
        return jdbcTemplate.query("""
            SELECT c.id, c.namespace, c.chain_type, c.stage_code, c.source_type, c.source_id,
                   c.content, c.content_hash, c.created_at,
                   e.dense_vector::text AS dense_vector, e.embedding_model
            FROM knowledge_chunk c
            LEFT JOIN knowledge_embedding e ON e.chunk_id = c.id
            WHERE c.namespace = ?
              AND c.chain_type = ?
              AND c.stage_code = ?
            ORDER BY c.created_at ASC
            """, this::mapEmbeddedChunk, namespace, chainType.name(), stageCode);
    }

    @Override
    public Optional<KnowledgeChunk> findLatestBySourceType(String namespace, ChainType chainType, String stageCode,
            String sourceType) {
        List<KnowledgeChunk> chunks = jdbcTemplate.query("""
            SELECT id, namespace, chain_type, stage_code, source_type, source_id,
                   content, content_hash, created_at
            FROM knowledge_chunk
            WHERE namespace = ?
              AND chain_type = ?
              AND stage_code = ?
              AND source_type = ?
            ORDER BY created_at DESC
            LIMIT 1
            """, this::mapChunk, namespace, chainType.name(), stageCode, sourceType);
        return chunks.stream().findFirst();
    }

    @Override
    public long countByNamespace(String namespace) {
        Long count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM knowledge_chunk WHERE namespace = ?", Long.class, namespace);
        return count == null ? 0 : count;
    }

    @Override
    public boolean existsByNamespaceAndDifferentChainType(String namespace, ChainType chainType) {
        Boolean exists = jdbcTemplate.queryForObject("""
            SELECT EXISTS (
                SELECT 1
                FROM knowledge_chunk
                WHERE namespace = ?
                  AND chain_type <> ?
            )
            """, Boolean.class, namespace, chainType.name());
        return Boolean.TRUE.equals(exists);
    }

    @Override
    @Transactional
    public RetrievalRecord saveRecord(RetrievalRecord record) {
        jdbcTemplate.update("""
            INSERT INTO retrieval_record (
                id, namespace, chain_type, stage_code, query, top_k, coverage_json, passed, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
            """, record.retrievalRecordId(), record.namespace(), record.chainType().name(), record.stageCode(),
            record.query(), record.hitChunkIds().size(), json(record.coverage()), record.coverage().passed(),
            Timestamp.from(record.createdAt()));
        List<RetrievalHit> hits = record.hits();
        for (int index = 0; index < hits.size(); index++) {
            RetrievalHit hit = hits.get(index);
            jdbcTemplate.update("""
                INSERT INTO retrieval_hit (
                    id, retrieval_record_id, chunk_id, rank_order, keyword_score, vector_score, rerank_score
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """, "hit-" + UUID.randomUUID(), record.retrievalRecordId(), hit.chunkId(),
                index + 1, hit.keywordScore(), hit.vectorScore(), hit.rerankScore());
        }
        return record;
    }

    @Override
    public Optional<RetrievalRecord> findRecord(String retrievalRecordId) {
        List<RetrievalRecord> records = jdbcTemplate.query("""
            SELECT id, namespace, chain_type, stage_code, query, coverage_json, passed, created_at
            FROM retrieval_record
            WHERE id = ?
            """, (resultSet, rowNum) -> mapRecord(resultSet), retrievalRecordId);
        return records.stream().findFirst();
    }

    private RetrievalRecord mapRecord(ResultSet resultSet) throws SQLException {
        String retrievalRecordId = resultSet.getString("id");
        List<RetrievalHit> hits = jdbcTemplate.query("""
            SELECT chunk_id, keyword_score, vector_score, rerank_score
            FROM retrieval_hit
            WHERE retrieval_record_id = ?
            ORDER BY rank_order ASC
            """, (hitResultSet, rowNum) -> new RetrievalHit(hitResultSet.getString("chunk_id"),
                hitResultSet.getDouble("keyword_score"), hitResultSet.getDouble("vector_score"),
                (Double) hitResultSet.getObject("rerank_score")), retrievalRecordId);
        return new RetrievalRecord(retrievalRecordId, resultSet.getString("namespace"),
            ChainType.valueOf(resultSet.getString("chain_type")), resultSet.getString("stage_code"),
            resultSet.getString("query"), hits, coverage(resultSet), toInstant(resultSet, "created_at"));
    }

    private RagCoverage coverage(ResultSet resultSet) throws SQLException {
        String coverageJson = resultSet.getString("coverage_json");
        try {
            RagCoverage coverage = objectMapper.readValue(coverageJson, RagCoverage.class);
            if (coverage.passed() == resultSet.getBoolean("passed")) {
                return coverage;
            }
            return new RagCoverage(coverage.goal(), coverage.stageMap(), coverage.currentStage(),
                coverage.previousHandoff(), coverage.previousReviewReport(), resultSet.getBoolean("passed"));
        } catch (JsonProcessingException exception) {
            throw new SQLException("Failed to parse retrieval coverage JSON", exception);
        }
    }

    private String json(RagCoverage coverage) {
        try {
            return objectMapper.writeValueAsString(coverage);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize retrieval coverage", exception);
        }
    }

    private KnowledgeChunk mapChunk(ResultSet resultSet, int rowNum) throws SQLException {
        return new KnowledgeChunk(resultSet.getString("id"), resultSet.getString("namespace"),
            ChainType.valueOf(resultSet.getString("chain_type")), resultSet.getString("stage_code"),
            resultSet.getString("source_type"), resultSet.getString("source_id"), resultSet.getString("content"),
            resultSet.getString("content_hash"), toInstant(resultSet, "created_at"));
    }

    private EmbeddedChunk mapEmbeddedChunk(ResultSet resultSet, int rowNum) throws SQLException {
        KnowledgeChunk chunk = mapChunk(resultSet, rowNum);
        float[] denseVector = parseVector(resultSet.getString("dense_vector"));
        String embeddingModel = resultSet.getString("embedding_model");
        return new EmbeddedChunk(chunk, denseVector, embeddingModel);
    }

    private String toVectorLiteral(float[] vector) {
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(vector[index]);
        }
        return builder.append(']').toString();
    }

    private float[] parseVector(String literal) {
        if (literal == null || literal.isBlank()) {
            return new float[0];
        }
        String trimmed = literal.strip();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        if (trimmed.isBlank()) {
            return new float[0];
        }
        String[] parts = trimmed.split(",");
        float[] vector = new float[parts.length];
        for (int index = 0; index < parts.length; index++) {
            vector[index] = Float.parseFloat(parts[index].strip());
        }
        return vector;
    }

    private Instant toInstant(ResultSet resultSet, String columnName) throws SQLException {
        return resultSet.getTimestamp(columnName).toInstant();
    }
}
