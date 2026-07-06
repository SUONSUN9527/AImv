package com.aimv.infrastructure.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 轻量 LLM 摘要器：用 qwen-turbo（便宜）把「多轮对话的旧轮次」语义压缩成一句「已确立设定」，
 * 供连续对话在超上下文阈值时压缩记忆而不丢关键信息（主体/风格/角色）。
 * 直连 DashScope OpenAI 兼容端点；Key 取自 aimv.dashscope.api-key / 环境变量。失败回退调用方处理。
 */
@Component
public class LlmSummarizer {

    private final RestClient restClient;
    private final String apiKey;
    private final String model;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LlmSummarizer(
            @Value("${aimv.dashscope.openai-base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
            String openAiBaseUrl,
            @Value("${aimv.dashscope.api-key:${AIMV_DASHSCOPE_API_KEY:}}") String apiKey,
            @Value("${aimv.dashscope.summary-model:qwen-turbo}") String model) {
        this.restClient = RestClient.builder().baseUrl(openAiBaseUrl).build();
        this.apiKey = apiKey == null ? "" : apiKey.strip();
        this.model = model;
    }

    public boolean available() {
        return !apiKey.isBlank();
    }

    /** 把若干旧轮次请求压缩成一句「已确立设定」摘要；不可用或失败时返回 null，由调用方回退。 */
    public String summarize(List<String> oldRequests) {
        if (!available() || oldRequests == null || oldRequests.isEmpty()) {
            return null;
        }
        String joined = String.join(" → ", oldRequests);
        String system = "你是对话记忆压缩器，为「连续生成同一主体」服务。把用户多轮创作请求压缩成一句中文摘要，"
            + "**逐字保留决定人物/主体外貌的锚点信息**（性别、年龄、发型发色、五官特征、身材、服饰款式与配色、"
            + "标志性道具），以及风格、场景、已确定的修改；去掉寒暄和重复。"
            + "外貌类关键词必须原样保留、不得改写或概括，确保后续生成是同一角色的连续镜头。只输出摘要本身，40~80字。";
        try {
            Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                    Map.of("role", "system", "content", system),
                    Map.of("role", "user", "content", "历史创作请求：" + joined)),
                "temperature", 0.3,
                "max_tokens", 200);
            String raw = restClient.post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
            JsonNode node = objectMapper.readTree(raw);
            String content = node.path("choices").path(0).path("message").path("content").asText("");
            return content.isBlank() ? null : content.strip();
        } catch (Exception exception) {
            return null;
        }
    }

    /** 便于测试/调用：读取超时（当前直用默认）。 */
    Duration timeout() {
        return Duration.ofSeconds(30);
    }
}
