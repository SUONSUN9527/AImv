package com.aimv.domain.knowledge;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 确定性文本向量化，用于离线/测试路径以及未配置云端 embedding key 时的回退。
 *
 * 这不是本地部署的大模型，而是一个确定性的"特征哈希 + 随机投影"函数：把文本切成
 * 空白 token 和 CJK 字符二元组，按特征频次哈希进定维向量并 L2 归一化。共享特征越多的
 * 两段文本余弦相似度越高，足以让语义相近的 chunk 在检索里排序更高，且完全可复现。
 * 生产环境应通过 {@link EmbeddingModel} 走云端免费额度 embedding，两端只要各自内部一致即可。
 */
public final class DeterministicEmbedding {

    public static final int DEFAULT_DIMENSIONS = 1024;
    public static final String MODEL_NAME = "deterministic-hash-v1";

    private DeterministicEmbedding() {
    }

    public static float[] embed(String text) {
        return embed(text, DEFAULT_DIMENSIONS);
    }

    public static float[] embed(String text, int dimensions) {
        float[] vector = new float[dimensions];
        if (text == null || text.isBlank()) {
            vector[0] = 1.0f;
            return vector;
        }
        Map<String, Integer> features = features(text);
        for (Map.Entry<String, Integer> feature : features.entrySet()) {
            int index = Math.floorMod(hash(feature.getKey()), dimensions);
            vector[index] += feature.getValue();
        }
        normalize(vector);
        return vector;
    }

    public static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0.0;
        }
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int index = 0; index < a.length; index++) {
            dot += (double) a[index] * b[index];
            normA += (double) a[index] * a[index];
            normB += (double) b[index] * b[index];
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private static Map<String, Integer> features(String text) {
        Map<String, Integer> features = new LinkedHashMap<>();
        String normalized = text.toLowerCase();
        for (String token : normalized.split("[\\s\\p{Punct}，。、；：！？（）【】]+")) {
            if (!token.isBlank()) {
                features.merge("t:" + token, 1, Integer::sum);
            }
        }
        String compact = normalized.replaceAll("\\s+", "");
        for (int index = 0; index + 1 < compact.length(); index++) {
            features.merge("b:" + compact.substring(index, index + 2), 1, Integer::sum);
        }
        if (features.isEmpty()) {
            features.put("t:" + normalized, 1);
        }
        return features;
    }

    private static void normalize(float[] vector) {
        double norm = 0.0;
        for (float value : vector) {
            norm += (double) value * value;
        }
        if (norm == 0.0) {
            vector[0] = 1.0f;
            return;
        }
        float inverse = (float) (1.0 / Math.sqrt(norm));
        for (int index = 0; index < vector.length; index++) {
            vector[index] *= inverse;
        }
    }

    private static int hash(String feature) {
        int hash = 0x811c9dc5;
        for (byte b : feature.getBytes(StandardCharsets.UTF_8)) {
            hash ^= (b & 0xff);
            hash *= 0x01000193;
        }
        return hash;
    }
}
