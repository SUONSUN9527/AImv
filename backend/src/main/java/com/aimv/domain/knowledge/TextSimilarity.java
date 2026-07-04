package com.aimv.domain.knowledge;

import java.util.Set;
import java.util.TreeSet;

/**
 * 文本特征与关键词重合度的确定性计算，供 HybridRanker 的 keyword 分和离线 rerank 打分共用。
 * 特征 = 空白/标点切分 token ∪ CJK 字符二元组，重合度为 query 特征被 doc 覆盖的比例。
 */
public final class TextSimilarity {

    private TextSimilarity() {
    }

    public static Set<String> features(String text) {
        Set<String> features = new TreeSet<>();
        if (text == null || text.isBlank()) {
            return features;
        }
        String normalized = text.toLowerCase();
        for (String token : normalized.split("[\\s\\p{Punct}，。、；：！？（）【】]+")) {
            if (!token.isBlank()) {
                features.add("t:" + token);
            }
        }
        String compact = normalized.replaceAll("\\s+", "");
        for (int index = 0; index + 1 < compact.length(); index++) {
            features.add("b:" + compact.substring(index, index + 2));
        }
        return features;
    }

    public static double overlap(Set<String> queryFeatures, String content) {
        if (queryFeatures.isEmpty()) {
            return 0.0;
        }
        Set<String> contentFeatures = features(content);
        if (contentFeatures.isEmpty()) {
            return 0.0;
        }
        long shared = queryFeatures.stream().filter(contentFeatures::contains).count();
        return (double) shared / queryFeatures.size();
    }

    public static double overlap(String query, String content) {
        return overlap(features(query), content);
    }
}
