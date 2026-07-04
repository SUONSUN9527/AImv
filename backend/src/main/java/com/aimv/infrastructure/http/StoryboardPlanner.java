package com.aimv.infrastructure.http;

import java.util.ArrayList;
import java.util.List;

/**
 * 分镜规划：把一段故事/旁白拆成固定数量的镜头场景，并给出贯穿全片的人物锚点，
 * 以保证多镜头出图时人物形象一致。纯函数、可单测；多镜头合成器用它生成每个镜头的 prompt。
 */
final class StoryboardPlanner {

    private StoryboardPlanner() {
    }

    /**
     * 贯穿全片的一致性锚点：固定的电影感 + 竖屏 + "同一主角"约束，配合固定随机种子，
     * 让每个镜头的主角外貌/服饰保持一致。
     */
    static String characterAnchor(String goal) {
        String subject = goal == null ? "" : goal.replaceAll("\\s+", " ").strip();
        if (subject.length() > 60) {
            subject = subject.substring(0, 60);
        }
        return "cinematic vertical 9:16, consistent same main character throughout, "
            + "coherent art style, dramatic lighting, " + subject;
    }

    /**
     * 把故事拆成 count 个镜头场景。先按中英文句读切句，句子多则合并、少则复用补足，
     * 保证返回恰好 count 段且都非空。
     */
    static List<String> shots(String story, int count) {
        int shotCount = Math.max(1, count);
        List<String> sentences = splitSentences(story);
        if (sentences.isEmpty()) {
            sentences = List.of("cinematic dramatic scene");
        }
        List<String> shots = new ArrayList<>(shotCount);
        for (int index = 0; index < shotCount; index++) {
            shots.add(sentences.get(index % sentences.size()));
        }
        return List.copyOf(shots);
    }

    private static List<String> splitSentences(String story) {
        List<String> sentences = new ArrayList<>();
        if (story == null || story.isBlank()) {
            return sentences;
        }
        for (String piece : story.split("[。！？!?;；\\n]+")) {
            String trimmed = piece.replaceAll("\\s+", " ").strip();
            if (trimmed.length() >= 2) {
                sentences.add(trimmed);
            }
        }
        return sentences;
    }
}
