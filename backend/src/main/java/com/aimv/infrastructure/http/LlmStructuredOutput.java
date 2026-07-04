package com.aimv.infrastructure.http;

import com.aimv.domain.provider.ProviderHttpRequest;
import com.aimv.domain.provider.ProviderHttpResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 与具体 provider 无关的 LLM 结构化输出引擎。负责：根据 payload 里的 partialSchema 构造
 * system/user prompt，要求模型只输出一个 JSON 对象，解析为 partialOutput；解析失败带原因重试，
 * 重试耗尽返回 parseError（不含 partialOutput）由上层判失败。HTTP 细节由各适配器提供的
 * {@link ChatFunction} 承担，DashScope 与 Pollinations 等 OpenAI 兼容文本 provider 共用本引擎。
 */
final class LlmStructuredOutput {

    private static final String SUCCESS_STATUS = "SUCCEEDED";
    private static final int STRUCTURED_OUTPUT_MAX_ATTEMPTS = 3;
    private static final int EVIDENCE_PROMPT_LIMIT = 6000;

    private final String adapterKind;
    private final String quotaSnapshot;
    private final String jobIdPrefix;
    private final ObjectMapper objectMapper = new ObjectMapper();

    LlmStructuredOutput(String adapterKind, String quotaSnapshot, String jobIdPrefix) {
        this.adapterKind = adapterKind;
        this.quotaSnapshot = quotaSnapshot;
        this.jobIdPrefix = jobIdPrefix;
    }

    /**
     * 单轮聊天调用：输入 system/user prompt 和可选的图片 URL（评审阶段传入候选图，供视觉模型看图打分），
     * 返回 provider 响应 id 和文本内容。imageUrls 为空时应发纯文本消息。
     */
    interface ChatFunction {
        Completion call(String systemPrompt, String userPrompt, List<String> imageUrls);
    }

    record Completion(String id, String content) {
    }

    String systemPrompt() {
        return "你是 AImv 短剧创作生产链路中的一个固定职责 agent 节点。"
            + "你只完成当前阶段分配给当前 agent 的字段任务，不自由发挥，不输出解释。"
            + "当要求输出 JSON 时，只输出一个合法 JSON 对象，不要包含 markdown 代码块标记。";
    }

    ProviderHttpResponse respond(ProviderHttpRequest request, ChatFunction chat) {
        List<String> imageUrls = reviewImageUrls(request);
        PartialSchema partialSchema = partialSchema(request);
        if (partialSchema == null) {
            Completion completion = chat.call(systemPrompt(), userPrompt(request, null, null, imageUrls),
                imageUrls);
            return plainResponse(request, completion);
        }
        String lastFailure = null;
        Completion lastCompletion = new Completion(null, "");
        for (int attempt = 1; attempt <= STRUCTURED_OUTPUT_MAX_ATTEMPTS; attempt++) {
            lastCompletion = chat.call(systemPrompt(),
                userPrompt(request, partialSchema, lastFailure, imageUrls), imageUrls);
            ParseResult parseResult = parseStructuredOutput(lastCompletion.content(), partialSchema);
            if (parseResult.error() == null) {
                return structuredResponse(request, lastCompletion, parseResult.fields(), attempt);
            }
            lastFailure = parseResult.error();
        }
        return parseFailureResponse(request, lastCompletion, lastFailure);
    }

    /**
     * 评审阶段（I50）payload 里携带的候选图片 URL（candidateEvidence.candidateRefs），
     * 传给视觉模型让其真正"看图"评分，而非凭 prompt 猜测。
     */
    @SuppressWarnings("unchecked")
    private List<String> reviewImageUrls(ProviderHttpRequest request) {
        Object candidateEvidence = inputValue(request, "candidateEvidence");
        if (!(candidateEvidence instanceof Map<?, ?> evidence)) {
            return List.of();
        }
        Object refs = evidence.get("candidateRefs");
        if (!(refs instanceof List<?> values)) {
            return List.of();
        }
        List<String> urls = new ArrayList<>();
        for (Object value : values) {
            String url = String.valueOf(value);
            if (url.startsWith("http://") || url.startsWith("https://")) {
                urls.add(url);
            }
        }
        return List.copyOf(urls);
    }

    private String userPrompt(ProviderHttpRequest request, PartialSchema partialSchema, String lastFailure,
            List<String> imageUrls) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("当前阶段: ").append(safe(request.stageCode()));
        Object stageName = inputValue(request, "stageName");
        if (stageName != null) {
            prompt.append("（").append(stageName).append("）");
        }
        prompt.append("\n当前 agent: ").append(safe(request.nodeName()));
        if (!imageUrls.isEmpty()) {
            prompt.append("\n请仔细查看随附的候选图片，据实评估其构图、清晰度、主体完整度，"
                + "以及与用户目标和 prompt 的匹配度，再据此给出各项分数。");
        }
        Object userGoal = inputValue(request, "userGoal");
        if (userGoal != null) {
            prompt.append("\n用户目标: ").append(userGoal);
        }
        Object previousStageOutput = inputValue(request, "previousStageOutput");
        if (previousStageOutput instanceof Map<?, ?> previous && !previous.isEmpty()) {
            prompt.append("\n上一阶段合并输出(JSON): ").append(toJson(previous));
        }
        Object evidencePack = inputValue(request, "evidencePack");
        if (evidencePack != null) {
            prompt.append("\nRAG 证据包(JSON，只能引用其中事实): ")
                .append(truncate(toJson(evidencePack), EVIDENCE_PROMPT_LIMIT));
        }
        if (partialSchema == null) {
            prompt.append("\n请输出可用于当前阶段的简明结构化中文结果。");
            return prompt.toString();
        }
        prompt.append("\n\n输出要求：只输出一个 JSON 对象（json object），不允许输出其他文本。");
        prompt.append("\n必须包含以下字段: ").append(String.join(", ", partialSchema.requiredFields()));
        prompt.append("\n只允许出现以下字段: ").append(String.join(", ", partialSchema.allowedFields()));
        prompt.append("\n字段类型规则：以 Score 结尾的字段是 0-100 的整数；");
        prompt.append("safetyPassed、humanVoiceAudible、freeModelGatePassed、nativeVoiceRequired 是布尔值；");
        prompt.append("以 Refs 结尾的字段、promptVariables、forbiddenTerms、excludedTerms、sensitiveTerms 是字符串数组；");
        prompt.append("durationSeconds、count 是整数；其余字段是字符串。");
        prompt.append("\n评分规则：");
        prompt.append("safetyScore 是安全合规分——只要内容不涉及暴力、色情、违法、侵权、真人仿冒、未成年人等风险，"
            + "就必须给 100；只有确实存在安全风险时才低于 100。");
        prompt.append("count 图片数量固定为 1；durationSeconds 固定为 10；aspectRatio 按用户目标（通常 9:16）。");
        prompt.append("其余质量分（如 goalClarityScore、visualQualityScore、goalMatchScore、promptConsistencyScore、"
            + "shortDramaScore）请基于用户目标和 RAG 证据客观评估，达标内容可给 90 以上，不要无依据地全给满分。");
        if (lastFailure != null) {
            prompt.append("\n\n上一次输出不合格，原因: ").append(lastFailure)
                .append("\n请修复以上问题后重新输出完整 JSON 对象。");
        }
        return prompt.toString();
    }

    private Object inputValue(ProviderHttpRequest request, String key) {
        return request.input() == null ? null : request.input().get(key);
    }

    private PartialSchema partialSchema(ProviderHttpRequest request) {
        Object schema = inputValue(request, "partialSchema");
        if (!(schema instanceof Map<?, ?> schemaMap)) {
            return null;
        }
        List<String> required = stringList(schemaMap.get("required"));
        List<String> allowed = stringList(schemaMap.get("allowed"));
        if (required.isEmpty() && allowed.isEmpty()) {
            return null;
        }
        return new PartialSchema(required, allowed.isEmpty() ? required : allowed);
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof Iterable<?> values)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        values.forEach(item -> result.add(String.valueOf(item)));
        return List.copyOf(result);
    }

    private ParseResult parseStructuredOutput(String content, PartialSchema partialSchema) {
        String jsonText = stripCodeFences(content);
        Map<String, Object> parsed;
        try {
            parsed = objectMapper.readValue(jsonText,
                objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
        } catch (JsonProcessingException exception) {
            return ParseResult.failure("输出不是合法 JSON 对象: " + exception.getOriginalMessage());
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : parsed.entrySet()) {
            if (partialSchema.allowedFields().contains(entry.getKey())) {
                fields.put(entry.getKey(), entry.getValue());
            }
        }
        List<String> missing = partialSchema.requiredFields().stream()
            .filter(fieldName -> !fields.containsKey(fieldName))
            .toList();
        if (!missing.isEmpty()) {
            return ParseResult.failure("缺少必填字段: " + String.join(", ", missing));
        }
        return ParseResult.success(fields);
    }

    private String stripCodeFences(String content) {
        String text = content == null ? "" : content.strip();
        if (text.startsWith("```")) {
            int firstLineBreak = text.indexOf('\n');
            if (firstLineBreak > 0) {
                text = text.substring(firstLineBreak + 1);
            }
            int closingFence = text.lastIndexOf("```");
            if (closingFence >= 0) {
                text = text.substring(0, closingFence);
            }
            text = text.strip();
        }
        int firstBrace = text.indexOf('{');
        int lastBrace = text.lastIndexOf('}');
        if (firstBrace > 0 && lastBrace > firstBrace) {
            text = text.substring(firstBrace, lastBrace + 1);
        }
        return text;
    }

    private ProviderHttpResponse plainResponse(ProviderHttpRequest request, Completion completion) {
        String providerJobId = providerJobId(request, completion);
        return new ProviderHttpResponse(providerJobId, SUCCESS_STATUS, completion.content(), List.of(),
            baseMetadata(request, providerJobId), quotaSnapshot);
    }

    private ProviderHttpResponse structuredResponse(ProviderHttpRequest request, Completion completion,
            Map<String, Object> fields, int attempts) {
        String providerJobId = providerJobId(request, completion);
        Map<String, Object> metadata = new LinkedHashMap<>(baseMetadata(request, providerJobId));
        metadata.put("partialOutput", Map.copyOf(fields));
        metadata.put("structuredOutputAttempts", attempts);
        return new ProviderHttpResponse(providerJobId, SUCCESS_STATUS, truncate(completion.content(), 512),
            List.of(), Map.copyOf(metadata), quotaSnapshot);
    }

    private ProviderHttpResponse parseFailureResponse(ProviderHttpRequest request, Completion completion,
            String failure) {
        String providerJobId = providerJobId(request, completion);
        Map<String, Object> metadata = new LinkedHashMap<>(baseMetadata(request, providerJobId));
        metadata.put("parseError", failure == null ? "结构化输出解析失败" : failure);
        return new ProviderHttpResponse(providerJobId, SUCCESS_STATUS,
            "LLM 结构化输出经 " + STRUCTURED_OUTPUT_MAX_ATTEMPTS + " 次尝试仍不合格: " + failure
                + "; 原始输出: " + truncate(completion.content(), 256),
            List.of(), Map.copyOf(metadata), quotaSnapshot);
    }

    private Map<String, Object> baseMetadata(ProviderHttpRequest request, String providerJobId) {
        return Map.of(
            "adapterKind", adapterKind,
            "capabilityType", request.capabilityType(),
            "stageCode", request.stageCode(),
            "provider", request.provider(),
            "providerResponseId", providerJobId,
            "quotaSource", "not_returned_by_api"
        );
    }

    private String providerJobId(ProviderHttpRequest request, Completion completion) {
        return isBlank(completion.id()) ? jobIdPrefix + request.traceId() : completion.id();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return String.valueOf(value);
        }
    }

    private String truncate(String text, int limit) {
        if (text == null) {
            return "";
        }
        return text.length() <= limit ? text : text.substring(0, limit);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record PartialSchema(
            List<String> requiredFields,
            List<String> allowedFields
    ) {
    }

    private record ParseResult(
            Map<String, Object> fields,
            String error
    ) {

        static ParseResult success(Map<String, Object> fields) {
            return new ParseResult(fields, null);
        }

        static ParseResult failure(String error) {
            return new ParseResult(Map.of(), error);
        }
    }
}
