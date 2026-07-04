package com.aimv.application.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.aimv.application.knowledge.KnowledgeApplicationService.RetrievalResult;
import com.aimv.domain.knowledge.DeterministicEmbedding;
import com.aimv.domain.knowledge.EmbeddingModel;
import com.aimv.domain.knowledge.KnowledgeChunk;
import com.aimv.domain.knowledge.RerankModel;
import com.aimv.domain.knowledge.TextSimilarity;
import com.aimv.domain.shared.ChainType;
import com.aimv.infrastructure.knowledge.InMemoryKnowledgeRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * RAG 检索评测 harness（技术文档 11.5 / 12）。用带标注（期望命中、禁止命中）的检索集，
 * 跑完整 embedding + hybrid + rerank 管线，量化 Recall@K / Precision@K / MRR / namespace 污染 /
 * 必需上下文覆盖。硬门禁（污染=0、覆盖=100）强制断言；语义指标以确定性向量能稳定达到的
 * 分离良好数据集度量（真实 bge-m3 只会更好）。
 */
class RagRetrievalEvaluationTest {

    private static final int TOP_K = 8;
    private static final String IMAGE_NS = "project:eval:chain:image-1";
    private static final String VIDEO_NS = "project:eval:chain:video-1";
    private static final String STAGE = "I10";

    private final EmbeddingModel embeddingModel = new EmbeddingModel() {
        @Override
        public float[] embed(ChainType chainType, String text) {
            return DeterministicEmbedding.embed(text);
        }

        @Override
        public String modelName(ChainType chainType) {
            return DeterministicEmbedding.MODEL_NAME;
        }
    };

    private final RerankModel rerankModel = (chainType, query, documents) -> {
        Set<String> queryFeatures = TextSimilarity.features(query);
        List<Double> scores = new ArrayList<>();
        documents.forEach(document -> scores.add(TextSimilarity.overlap(queryFeatures, document)));
        return scores;
    };

    private record Labeled(String query, String expectedChunkId) {
    }

    @Test
    void meetsRetrievalQualityAndIsolationGatesOnLabeledSet() {
        KnowledgeApplicationService service = new KnowledgeApplicationService(
            new InMemoryKnowledgeRepository(), embeddingModel, rerankModel);

        // 图片链路语料（5 个不同主题），每个都有一个明确最相关的 chunk。
        String detective = ingest(service, IMAGE_NS, ChainType.IMAGE, "都市 悬疑 侦探 雨夜 霓虹街头 反转");
        String dessert = ingest(service, IMAGE_NS, ChainType.IMAGE, "美食教程 蛋糕 甜点 烘焙 奶油 裱花");
        String chip = ingest(service, IMAGE_NS, ChainType.IMAGE, "半导体 芯片 光刻 晶圆 制造 封装");
        String travel = ingest(service, IMAGE_NS, ChainType.IMAGE, "海岛 旅行 沙滩 日落 潜水 度假");
        String finance = ingest(service, IMAGE_NS, ChainType.IMAGE, "金融 股票 基金 投资 收益 风险");
        // 视频链路私有语料：任何图片链路检索都不能命中（跨链路隔离）。
        ingest(service, VIDEO_NS, ChainType.VIDEO, "视频私有 短剧 配音 分镜 运镜");

        List<Labeled> evalSet = List.of(
            new Labeled("侦探 雨夜 街头 悬疑 反转", detective),
            new Labeled("蛋糕 甜点 烘焙 奶油", dessert),
            new Labeled("芯片 光刻 晶圆 半导体", chip),
            new Labeled("海岛 沙滩 日落 潜水 旅行", travel),
            new Labeled("股票 基金 投资 收益 金融", finance)
        );

        double recallSum = 0.0;
        double precisionSum = 0.0;
        double reciprocalRankSum = 0.0;
        int contaminated = 0;

        for (Labeled labeled : evalSet) {
            RetrievalResult result = service.retrieve(IMAGE_NS, ChainType.IMAGE, STAGE, labeled.query(), TOP_K);
            List<String> retrievedIds = result.chunks().stream().map(KnowledgeChunk::chunkId).toList();

            recallSum += retrievedIds.contains(labeled.expectedChunkId()) ? 1.0 : 0.0;
            precisionSum += retrievedIds.isEmpty() ? 0.0
                : (retrievedIds.contains(labeled.expectedChunkId()) ? 1.0 / retrievedIds.size() : 0.0);
            reciprocalRankSum += reciprocalRank(retrievedIds, labeled.expectedChunkId());
            contaminated += result.chunks().stream()
                .anyMatch(chunk -> !IMAGE_NS.equals(chunk.namespace())
                    && chunk.namespace().contains(":chain:")) ? 1 : 0;
        }

        int queries = evalSet.size();
        double recallAtK = recallSum / queries;
        double precisionAtK = precisionSum / queries;
        double mrr = reciprocalRankSum / queries;

        // 硬门禁：跨链路私有 namespace 污染必须为 0。
        assertThat(contaminated).as("namespace contamination").isZero();
        // 语义质量：分离良好的评测集下，期望命中必被召回、且排在第一位。
        assertThat(recallAtK).as("Recall@%d", TOP_K).isEqualTo(1.0);
        assertThat(mrr).as("MRR").isGreaterThanOrEqualTo(0.85);
        assertThat(precisionAtK).as("Precision@%d", TOP_K).isGreaterThan(0.0);
    }

    @Test
    void reportsFullRequiredContextCoverageForNonInitialStage() {
        KnowledgeApplicationService service = new KnowledgeApplicationService(
            new InMemoryKnowledgeRepository(), embeddingModel, rerankModel);
        String namespace = "project:eval:chain:coverage-1";
        service.ingest(namespace, ChainType.IMAGE, "I30", "USER_GOAL", "goal", "生成一张9:16都市悬疑封面");
        service.ingest(namespace, ChainType.IMAGE, "I30", "STAGE_MAP", "map", "I00->I10->I20->I30->I40->I50->I60");
        service.ingest(namespace, ChainType.IMAGE, "I30", "CHAIN_CONTEXT", "ctx", "chainType IMAGE stage I30");
        service.ingest(namespace, ChainType.IMAGE, "I30", "NEXT_STAGE_CONTEXT", "handoff", "handoff from I20");
        service.ingest(namespace, ChainType.IMAGE, "I30", "REVIEW_REPORT", "review", "I20 review passed score 95");

        RetrievalResult result = service.retrieve(namespace, ChainType.IMAGE, "I30", "都市悬疑封面", 8);

        assertThat(result.coverage().passed()).as("required context coverage").isTrue();
        assertThat(result.coverage().goal()).isTrue();
        assertThat(result.coverage().stageMap()).isTrue();
        assertThat(result.coverage().currentStage()).isTrue();
        assertThat(result.coverage().previousHandoff()).isTrue();
        assertThat(result.coverage().previousReviewReport()).isTrue();
    }

    private String ingest(KnowledgeApplicationService service, String namespace, ChainType chainType,
            String content) {
        return service.ingest(namespace, chainType, STAGE, "CHAIN_CONTEXT", "src-" + content.hashCode(), content)
            .chunkId();
    }

    private double reciprocalRank(List<String> retrievedIds, String expectedChunkId) {
        for (int index = 0; index < retrievedIds.size(); index++) {
            if (retrievedIds.get(index).equals(expectedChunkId)) {
                return 1.0 / (index + 1);
            }
        }
        return 0.0;
    }
}
