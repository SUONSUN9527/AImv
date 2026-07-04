package com.aimv.domain.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.aimv.domain.shared.ChainType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class HybridRankerTest {

    @Test
    void ranksSemanticallyClosestChunkFirst() {
        EmbeddedChunk detective = chunk("chunk-detective", "城市侦探 雨夜 霓虹街头 悬疑 反转", 0);
        EmbeddedChunk dessert = chunk("chunk-dessert", "美食教程 蛋糕 甜点 烘焙 奶油", 1);
        EmbeddedChunk chip = chunk("chunk-chip", "半导体 芯片 制造 光刻 晶圆", 2);

        List<RetrievalHit> hits = HybridRanker.rank("侦探 雨夜 街头 悬疑",
            DeterministicEmbedding.embed("侦探 雨夜 街头 悬疑"), List.of(dessert, chip, detective));

        assertThat(hits).extracting(RetrievalHit::chunkId).containsExactly(
            "chunk-detective", "chunk-dessert", "chunk-chip");
        assertThat(hits.get(0).vectorScore()).isGreaterThan(hits.get(2).vectorScore());
        assertThat(hits.get(0).keywordScore()).isGreaterThan(0.0);
    }

    @Test
    void keepsAllCandidatesAndScoresEveryHit() {
        EmbeddedChunk first = chunk("chunk-a", "都市 悬疑 短剧", 0);
        EmbeddedChunk second = chunk("chunk-b", "都市 悬疑 封面", 1);

        List<RetrievalHit> hits = HybridRanker.rank("都市 悬疑",
            DeterministicEmbedding.embed("都市 悬疑"), List.of(first, second));

        assertThat(hits).hasSize(2);
        assertThat(hits).allSatisfy(hit -> {
            assertThat(hit.vectorScore()).isGreaterThan(0.0);
            assertThat(hit.keywordScore()).isGreaterThanOrEqualTo(0.0);
        });
    }

    private EmbeddedChunk chunk(String id, String content, int order) {
        KnowledgeChunk knowledgeChunk = new KnowledgeChunk(id, "project:p1:chain:c1", ChainType.IMAGE, "I10",
            "CHAIN_CONTEXT", "source-" + id, content, "sha256:" + id,
            Instant.ofEpochMilli(1_000 + order));
        return new EmbeddedChunk(knowledgeChunk, DeterministicEmbedding.embed(content),
            DeterministicEmbedding.MODEL_NAME);
    }
}
