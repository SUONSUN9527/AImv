package com.aimv.application.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.aimv.application.knowledge.KnowledgeApplicationService.RetrievalResult;
import com.aimv.domain.knowledge.DeterministicEmbedding;
import com.aimv.domain.knowledge.EmbeddingModel;
import com.aimv.domain.knowledge.KnowledgeChunk;
import com.aimv.domain.knowledge.RerankModel;
import com.aimv.domain.shared.ChainType;
import com.aimv.infrastructure.knowledge.InMemoryKnowledgeRepository;
import org.junit.jupiter.api.Test;

/**
 * RAG 应用层的语义检索行为：向量真实参与排序、命中分数落库、跨链路 namespace 隔离。
 * 用确定性 EmbeddingModel（离线回退同款）保证可复现。
 */
class KnowledgeApplicationServiceRagTest {

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

    private final RerankModel noRerank = (chainType, query, documents) -> java.util.List.of();

    private KnowledgeApplicationService service() {
        return new KnowledgeApplicationService(new InMemoryKnowledgeRepository(), embeddingModel, noRerank);
    }

    private KnowledgeApplicationService service(RerankModel rerankModel) {
        return new KnowledgeApplicationService(new InMemoryKnowledgeRepository(), embeddingModel, rerankModel);
    }

    @Test
    void ranksSemanticallyRelevantChunkAboveUnrelatedOnes() {
        KnowledgeApplicationService service = service();
        String namespace = "project:p1:chain:c1";
        service.ingest(namespace, ChainType.IMAGE, "I10", "CHAIN_CONTEXT", "s1",
            "城市侦探 雨夜 霓虹街头 悬疑 反转");
        service.ingest(namespace, ChainType.IMAGE, "I10", "CHAIN_CONTEXT", "s2",
            "美食教程 蛋糕 甜点 烘焙 奶油");
        service.ingest(namespace, ChainType.IMAGE, "I10", "CHAIN_CONTEXT", "s3",
            "半导体 芯片 制造 光刻 晶圆");

        RetrievalResult result = service.retrieve(namespace, ChainType.IMAGE, "I10",
            "侦探 雨夜 街头 悬疑", 5);

        assertThat(result.chunks()).isNotEmpty();
        assertThat(result.chunks().get(0).content()).contains("侦探");
    }

    @Test
    void persistsRetrievalHitScoresFromHybridRanking() {
        KnowledgeApplicationService service = service();
        String namespace = "project:p1:chain:c1";
        service.ingest(namespace, ChainType.IMAGE, "I10", "CHAIN_CONTEXT", "s1", "都市 悬疑 短剧 封面");
        service.ingest(namespace, ChainType.IMAGE, "I10", "CHAIN_CONTEXT", "s2", "海边 日落 风景 摄影");

        RetrievalResult result = service.retrieve(namespace, ChainType.IMAGE, "I10", "都市 悬疑", 5);

        var record = service.record(result.retrievalRecordId());
        assertThat(record.hits()).isNotEmpty();
        assertThat(record.hits().get(0).vectorScore()).isGreaterThan(0.0);
        assertThat(record.hits()).anySatisfy(hit -> assertThat(hit.keywordScore()).isGreaterThan(0.0));
    }

    @Test
    void rerankReordersFusedCandidatesAndPersistsRerankScore() {
        // rerank 模型把第二个文档判为最相关，检索结果应据此重排到首位。
        RerankModel promoteSecond = (chainType, query, documents) -> {
            java.util.List<Double> scores = new java.util.ArrayList<>();
            for (int index = 0; index < documents.size(); index++) {
                scores.add(documents.get(index).contains("反转结局") ? 0.99 : 0.10);
            }
            return scores;
        };
        KnowledgeApplicationService service = service(promoteSecond);
        String namespace = "project:p1:chain:c1";
        service.ingest(namespace, ChainType.IMAGE, "I10", "CHAIN_CONTEXT", "s1", "都市 悬疑 侦探 街头");
        service.ingest(namespace, ChainType.IMAGE, "I10", "CHAIN_CONTEXT", "s2", "都市 悬疑 反转结局 记忆点");

        RetrievalResult result = service.retrieve(namespace, ChainType.IMAGE, "I10", "都市 悬疑", 5);

        assertThat(result.chunks().get(0).content()).contains("反转结局");
        var record = service.record(result.retrievalRecordId());
        assertThat(record.hits().get(0).rerankScore()).isEqualTo(0.99);
    }

    @Test
    void keepsFusedOrderWhenRerankUnavailable() {
        KnowledgeApplicationService service = service();
        String namespace = "project:p1:chain:c1";
        service.ingest(namespace, ChainType.IMAGE, "I10", "CHAIN_CONTEXT", "s1", "侦探 雨夜 街头 悬疑 反转");
        service.ingest(namespace, ChainType.IMAGE, "I10", "CHAIN_CONTEXT", "s2", "美食 蛋糕 甜点 烘焙");

        RetrievalResult result = service.retrieve(namespace, ChainType.IMAGE, "I10", "侦探 雨夜 悬疑", 5);

        assertThat(result.chunks().get(0).content()).contains("侦探");
        var record = service.record(result.retrievalRecordId());
        assertThat(record.hits().get(0).rerankScore()).isNull();
    }

    @Test
    void doesNotLeakOtherChainPrivateChunks() {
        KnowledgeApplicationService service = service();
        String imageNamespace = "project:p1:chain:image-1";
        String videoNamespace = "project:p1:chain:video-1";
        service.ingest(imageNamespace, ChainType.IMAGE, "I10", "CHAIN_CONTEXT", "s1", "图片私有 侦探 街头");
        service.ingest(videoNamespace, ChainType.VIDEO, "V10", "CHAIN_CONTEXT", "s2", "视频私有 短剧 配音");

        RetrievalResult result = service.retrieve(imageNamespace, ChainType.IMAGE, "I10", "私有", 5);

        assertThat(result.chunks()).extracting(KnowledgeChunk::namespace).containsOnly(imageNamespace);
        assertThat(result.chunks()).noneSatisfy(chunk ->
            assertThat(chunk.content()).contains("视频私有"));
    }
}
