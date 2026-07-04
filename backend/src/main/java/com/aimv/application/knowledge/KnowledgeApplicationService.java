package com.aimv.application.knowledge;

import com.aimv.domain.knowledge.EmbeddedChunk;
import com.aimv.domain.knowledge.EmbeddingModel;
import com.aimv.domain.knowledge.HybridRanker;
import com.aimv.domain.knowledge.KnowledgeChunk;
import com.aimv.domain.knowledge.KnowledgeRepository;
import com.aimv.domain.knowledge.RagCoverage;
import com.aimv.domain.knowledge.RerankFusion;
import com.aimv.domain.knowledge.RerankModel;
import com.aimv.domain.knowledge.RetrievalHit;
import com.aimv.domain.knowledge.RetrievalRecord;
import com.aimv.domain.shared.ChainType;
import com.aimv.shared.error.BusinessException;
import com.aimv.shared.error.ResourceNotFoundException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeApplicationService {

    private static final int DEFAULT_TOP_K = 5;
    private static final int MAX_TOP_K = 20;
    private static final int RERANK_CANDIDATE_LIMIT = 30;
    private static final String CHAIN_CONTEXT_SOURCE_TYPE = "CHAIN_CONTEXT";
    private static final String GLOBAL_PUBLIC_NAMESPACE = "global:public";
    private static final String NEXT_STAGE_CONTEXT_SOURCE_TYPE = "NEXT_STAGE_CONTEXT";
    private static final Pattern FIELD_VALUE_PATTERN = Pattern.compile(
        "([A-Za-z][A-Za-z0-9_]*)\\s*=\\s*([^;；,，\\n\\r]+)");
    private static final Pattern PROJECT_NAMESPACE_PATTERN = Pattern.compile(
        "^project:[^:]+(?::chain:[^:]+(?::stage:[^:]+)?)?$");
    private static final String REVIEW_REPORT_SOURCE_TYPE = "REVIEW_REPORT";
    private static final String STAGE_MAP_SOURCE_TYPE = "STAGE_MAP";
    private static final String USER_GOAL_SOURCE_TYPE = "USER_GOAL";

    private final KnowledgeRepository knowledgeRepository;
    private final EmbeddingModel embeddingModel;
    private final RerankModel rerankModel;

    public KnowledgeApplicationService(KnowledgeRepository knowledgeRepository, EmbeddingModel embeddingModel,
            RerankModel rerankModel) {
        this.knowledgeRepository = knowledgeRepository;
        this.embeddingModel = embeddingModel;
        this.rerankModel = rerankModel;
    }

    public KnowledgeChunk ingest(String namespace, ChainType chainType, String stageCode, String sourceType,
            String sourceId, String content) {
        assertNamespaceMatchesChain(namespace, chainType);
        KnowledgeChunk chunk = new KnowledgeChunk("chunk-" + UUID.randomUUID(), namespace, chainType, stageCode,
            sourceType, sourceId, content, hash(content), Instant.now());
        float[] denseVector = embeddingModel.embed(chainType, content);
        return knowledgeRepository.save(chunk, denseVector, embeddingModel.modelName(chainType));
    }

    /**
     * Hybrid 检索：跨可读 namespace 收集候选（含 dense 向量），用 query 向量做余弦相似度
     * 与关键词重合的 RRF 融合排序，取融合后的前 topK，并把分项分数落 retrieval_hit。
     */
    public RetrievalResult retrieve(String namespace, ChainType chainType, String stageCode, String query,
            Integer topK) {
        assertNamespaceMatchesChain(namespace, chainType);
        int limitedTopK = normalizeTopK(topK);
        List<EmbeddedChunk> candidates = readableNamespaces(namespace).stream()
            .flatMap(readableNamespace -> knowledgeRepository
                .retrieveEmbedded(readableNamespace, chainType, stageCode)
                .stream())
            .toList();
        Map<String, KnowledgeChunk> chunkById = new LinkedHashMap<>();
        candidates.forEach(candidate -> chunkById.put(candidate.chunk().chunkId(), candidate.chunk()));

        float[] queryVector = embeddingModel.embed(chainType, query);
        List<RetrievalHit> fusedHits = HybridRanker.rank(query, queryVector, candidates);
        List<RetrievalHit> rerankedHits = applyRerank(chainType, query, fusedHits, chunkById);
        List<RetrievalHit> rankedHits = rerankedHits.stream()
            .limit(limitedTopK)
            .toList();
        List<KnowledgeChunk> chunks = rankedHits.stream()
            .map(hit -> chunkById.get(hit.chunkId()))
            .toList();

        assertEvidenceConsistent(chunks);
        List<KnowledgeChunk> privateCoverageChunks = chunks.stream()
            .filter(chunk -> namespace.equals(chunk.namespace()))
            .toList();
        RagCoverage coverage = coverage(stageCode, privateCoverageChunks);
        RetrievalRecord record = new RetrievalRecord("retrieval-" + UUID.randomUUID(), namespace, chainType,
            stageCode, query, rankedHits, coverage, Instant.now());
        knowledgeRepository.saveRecord(record);
        return new RetrievalResult(record.retrievalRecordId(), namespace, chainType, stageCode, chunks, coverage);
    }

    /**
     * 对 hybrid 融合后的前 RERANK_CANDIDATE_LIMIT 个候选调用 rerank 模型重排（技术文档 11.3 第 5 步）。
     * 未配置 rerank key 或分数缺失时保持融合顺序。
     */
    private List<RetrievalHit> applyRerank(ChainType chainType, String query, List<RetrievalHit> fusedHits,
            Map<String, KnowledgeChunk> chunkById) {
        if (fusedHits.isEmpty()) {
            return fusedHits;
        }
        int candidateLimit = Math.min(RERANK_CANDIDATE_LIMIT, fusedHits.size());
        List<String> documents = fusedHits.subList(0, candidateLimit).stream()
            .map(hit -> chunkById.get(hit.chunkId()).content())
            .toList();
        List<Double> rerankScores = rerankModel.rerank(chainType, query, documents);
        return RerankFusion.apply(fusedHits, rerankScores, candidateLimit);
    }

    public java.util.Optional<KnowledgeChunk> findLatestBySourceType(String namespace, ChainType chainType,
            String stageCode, String sourceType) {
        assertNamespaceMatchesChain(namespace, chainType);
        return knowledgeRepository.findLatestBySourceType(namespace, chainType, stageCode, sourceType);
    }

    public RetrievalRecord record(String retrievalRecordId) {
        return knowledgeRepository.findRecord(retrievalRecordId)
            .orElseThrow(() -> new ResourceNotFoundException("检索记录不存在"));
    }

    public ReindexResult reindex(String namespace, ChainType chainType) {
        assertNamespaceMatchesChain(namespace, chainType);
        return new ReindexResult(namespace, chainType, knowledgeRepository.countByNamespace(namespace), "REINDEXED");
    }

    private int normalizeTopK(Integer topK) {
        if (topK == null || topK <= 0) {
            return DEFAULT_TOP_K;
        }
        return Math.min(topK, MAX_TOP_K);
    }

    private void assertNamespaceMatchesChain(String namespace, ChainType chainType) {
        if (!isSupportedNamespace(namespace)) {
            throw new BusinessException(HttpStatus.CONFLICT, "RAG_NAMESPACE_INVALID",
                "RAG namespace 必须使用 global:public、project:{projectId} 或项目私有链路格式");
        }
        if (isChainPrivateNamespace(namespace)
                && knowledgeRepository.existsByNamespaceAndDifferentChainType(namespace, chainType)) {
            throw new BusinessException(HttpStatus.CONFLICT, "RAG_NAMESPACE_MISMATCH",
                "RAG namespace 与链路类型不匹配，禁止跨链路读取上下文");
        }
    }

    private boolean isSupportedNamespace(String namespace) {
        return GLOBAL_PUBLIC_NAMESPACE.equals(namespace)
            || PROJECT_NAMESPACE_PATTERN.matcher(namespace).matches();
    }

    private boolean isChainPrivateNamespace(String namespace) {
        return namespace.contains(":chain:");
    }

    private List<String> readableNamespaces(String namespace) {
        List<String> namespaces = new ArrayList<>();
        namespaces.add(namespace);
        if (namespace.contains(":stage:")) {
            namespaces.add(namespace.substring(0, namespace.indexOf(":stage:")));
        }
        if (namespace.startsWith("project:")) {
            namespaces.add(projectNamespace(namespace));
            namespaces.add(GLOBAL_PUBLIC_NAMESPACE);
        }
        return namespaces.stream()
            .distinct()
            .toList();
    }

    private String projectNamespace(String namespace) {
        int chainSeparatorIndex = namespace.indexOf(":chain:");
        if (chainSeparatorIndex < 0) {
            return namespace;
        }
        return namespace.substring(0, chainSeparatorIndex);
    }

    private void assertEvidenceConsistent(List<KnowledgeChunk> chunks) {
        Map<String, FieldEvidence> firstEvidenceByField = new LinkedHashMap<>();
        for (KnowledgeChunk chunk : chunks) {
            Matcher matcher = FIELD_VALUE_PATTERN.matcher(chunk.content());
            while (matcher.find()) {
                String fieldName = matcher.group(1).strip();
                String fieldValue = matcher.group(2).strip();
                FieldEvidence firstEvidence = firstEvidenceByField.get(fieldName);
                if (firstEvidence == null) {
                    firstEvidenceByField.put(fieldName, new FieldEvidence(fieldValue, chunk.chunkId()));
                    continue;
                }
                if (!firstEvidence.value().equals(fieldValue)) {
                    throw new BusinessException(HttpStatus.CONFLICT, "RAG_EVIDENCE_CONFLICT",
                        "RAG 证据字段冲突，等待人工确认: " + fieldName
                            + " 在 " + firstEvidence.chunkId() + " 与 " + chunk.chunkId()
                            + " 中取值不一致");
                }
            }
        }
    }

    private RagCoverage coverage(String stageCode, List<KnowledgeChunk> chunks) {
        boolean hasGoal = hasSourceType(chunks, USER_GOAL_SOURCE_TYPE);
        boolean hasStageMap = hasSourceType(chunks, STAGE_MAP_SOURCE_TYPE);
        boolean hasCurrentStage = hasSourceType(chunks, CHAIN_CONTEXT_SOURCE_TYPE);
        boolean hasPreviousHandoff = hasSourceType(chunks, NEXT_STAGE_CONTEXT_SOURCE_TYPE);
        boolean hasPreviousReviewReport = hasSourceType(chunks, REVIEW_REPORT_SOURCE_TYPE);
        boolean initialStage = stageCode != null && stageCode.endsWith("00");
        boolean passed = hasGoal && hasStageMap && hasCurrentStage
            && (initialStage || hasPreviousHandoff && hasPreviousReviewReport);
        return new RagCoverage(hasGoal, hasStageMap, hasCurrentStage, hasPreviousHandoff,
            hasPreviousReviewReport, passed);
    }

    private boolean hasSourceType(List<KnowledgeChunk> chunks, String sourceType) {
        return chunks.stream()
            .anyMatch(chunk -> sourceType.equals(chunk.sourceType()));
    }

    private String hash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is required", exception);
        }
    }

    public record RetrievalResult(
            String retrievalRecordId,
            String namespace,
            ChainType chainType,
            String stageCode,
            List<KnowledgeChunk> chunks,
            RagCoverage coverage
    ) {
    }

    public record ReindexResult(
            String namespace,
            ChainType chainType,
            long chunkCount,
            String status
    ) {
    }

    private record FieldEvidence(
            String value,
            String chunkId
    ) {
    }
}
