package com.aimv.application.conversation;

import com.aimv.infrastructure.http.LlmSummarizer;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 连续对话「记忆」构建 + 上下文压缩：
 * 不超阈值就把各轮请求全量拼接；超阈值时保留最近若干轮 verbatim（近期上下文最关键），
 * 其余旧轮用 LLM 语义压缩成一句「已确立设定」（保住主体/风格/角色），LLM 不可用则启发式保锚点。
 * 阈值可按模型上下文预算配置（aimv.conversation.context-threshold-chars）。
 */
@Service
public class ConversationMemoryService {

    private final LlmSummarizer summarizer;
    private final int thresholdChars;
    private final int recentVerbatim;

    public ConversationMemoryService(LlmSummarizer summarizer,
            @Value("${aimv.conversation.context-threshold-chars:4000}") int thresholdChars,
            @Value("${aimv.conversation.recent-verbatim-turns:4}") int recentVerbatim) {
        this.summarizer = summarizer;
        this.thresholdChars = thresholdChars;
        this.recentVerbatim = Math.max(1, recentVerbatim);
    }

    public String build(List<String> requests) {
        List<String> turns = requests == null ? List.of()
            : requests.stream().map(text -> text == null ? "" : text.strip()).filter(text -> !text.isBlank()).toList();
        if (turns.isEmpty()) {
            return "";
        }
        String full = String.join(" → ", turns);
        if (full.length() <= thresholdChars || turns.size() <= recentVerbatim) {
            return full;
        }
        int splitAt = turns.size() - recentVerbatim;
        List<String> older = turns.subList(0, splitAt);
        List<String> recent = turns.subList(splitAt, turns.size());
        String summary = summarizer.summarize(older);
        if (summary == null || summary.isBlank()) {
            // LLM 不可用/失败 → 启发式：至少保住最初设定锚点，不做无脑截断。
            summary = "最初设定「" + older.get(0) + "」等 " + older.size() + " 轮的既定主体与风格";
        }
        return "已确立：" + summary + " → " + String.join(" → ", recent);
    }
}
