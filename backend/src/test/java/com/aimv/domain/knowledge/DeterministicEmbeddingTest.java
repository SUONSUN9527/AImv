package com.aimv.domain.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DeterministicEmbeddingTest {

    @Test
    void producesIdenticalVectorForIdenticalText() {
        float[] first = DeterministicEmbedding.embed("城市侦探 雨夜 霓虹街头");
        float[] second = DeterministicEmbedding.embed("城市侦探 雨夜 霓虹街头");

        assertThat(first).containsExactly(second);
        assertThat(first).hasSize(DeterministicEmbedding.DEFAULT_DIMENSIONS);
    }

    @Test
    void normalizesVectorToUnitLength() {
        float[] vector = DeterministicEmbedding.embed("都市悬疑短剧封面");

        double norm = 0.0;
        for (float value : vector) {
            norm += (double) value * value;
        }
        assertThat(Math.sqrt(norm)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-5));
    }

    @Test
    void overlappingTextsAreMoreSimilarThanUnrelatedTexts() {
        float[] query = DeterministicEmbedding.embed("城市侦探 雨夜 霓虹街头 悬疑");
        float[] related = DeterministicEmbedding.embed("城市侦探 街头 悬疑 反转");
        float[] unrelated = DeterministicEmbedding.embed("美食教程 蛋糕 甜点 烘焙");

        double relatedScore = DeterministicEmbedding.cosine(query, related);
        double unrelatedScore = DeterministicEmbedding.cosine(query, unrelated);

        assertThat(relatedScore).isGreaterThan(unrelatedScore);
    }
}
