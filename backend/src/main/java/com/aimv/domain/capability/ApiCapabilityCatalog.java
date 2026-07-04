package com.aimv.domain.capability;

import com.aimv.domain.shared.ChainType;
import java.util.List;
import java.util.Map;

public final class ApiCapabilityCatalog {

    private static final List<CapabilityDescriptor> DESCRIPTORS = List.of(
        new CapabilityDescriptor("cap-llm-text-free", "llm.text.free", "文本规划 LLM",
            List.of(ChainType.IMAGE, ChainType.VIDEO), "HTTP_ADAPTER", true, true),
        new CapabilityDescriptor("cap-rag-embedding-free", "rag.embedding.free", "RAG Embedding",
            List.of(ChainType.IMAGE, ChainType.VIDEO), "HTTP_ADAPTER", true, true),
        new CapabilityDescriptor("cap-rag-rerank-free", "rag.rerank.free", "RAG Rerank",
            List.of(ChainType.IMAGE, ChainType.VIDEO), "HTTP_ADAPTER", true, true),
        new CapabilityDescriptor("cap-image-generate-free", "image.generate.free", "图片生成",
            List.of(ChainType.IMAGE), "HTTP_ADAPTER", true, true),
        new CapabilityDescriptor("cap-video-full-voice-free", "video.generate.full_with_voice.free",
            "完整视频带配音生成", List.of(ChainType.VIDEO), "HTTP_ADAPTER", true, true)
    );

    private static final Map<ChainType, List<SlotDefinition>> DEFINITIONS = Map.of(
        ChainType.IMAGE,
        List.of(
            new SlotDefinition("llm.text.free", "文本规划 LLM"),
            new SlotDefinition("rag.embedding.free", "RAG Embedding"),
            new SlotDefinition("rag.rerank.free", "RAG Rerank"),
            new SlotDefinition("image.generate.free", "图片生成")
        ),
        ChainType.VIDEO,
        List.of(
            new SlotDefinition("llm.text.free", "文本规划 LLM"),
            new SlotDefinition("rag.embedding.free", "RAG Embedding"),
            new SlotDefinition("rag.rerank.free", "RAG Rerank"),
            new SlotDefinition("video.generate.full_with_voice.free", "完整视频带配音生成")
        )
    );

    private ApiCapabilityCatalog() {
    }

    public static List<SlotDefinition> slots(ChainType chainType) {
        return DEFINITIONS.get(chainType);
    }

    public static List<CapabilityDescriptor> descriptors() {
        return DESCRIPTORS;
    }

    public static boolean supports(ChainType chainType, String capabilityType) {
        return slots(chainType).stream()
            .anyMatch(slot -> slot.capabilityType().equals(capabilityType));
    }

    public record SlotDefinition(String capabilityType, String label) {
    }
}
