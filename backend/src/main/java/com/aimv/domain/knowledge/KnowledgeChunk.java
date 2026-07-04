package com.aimv.domain.knowledge;

import com.aimv.domain.shared.ChainType;
import java.time.Instant;

public record KnowledgeChunk(
        String chunkId,
        String namespace,
        ChainType chainType,
        String stageCode,
        String sourceType,
        String sourceId,
        String content,
        String contentHash,
        Instant createdAt
) {
}
