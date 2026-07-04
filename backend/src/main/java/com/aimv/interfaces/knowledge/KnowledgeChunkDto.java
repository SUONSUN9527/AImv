package com.aimv.interfaces.knowledge;

import com.aimv.domain.knowledge.KnowledgeChunk;
import com.aimv.domain.shared.ChainType;
import java.time.Instant;

public record KnowledgeChunkDto(
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

    public static KnowledgeChunkDto from(KnowledgeChunk chunk) {
        return new KnowledgeChunkDto(chunk.chunkId(), chunk.namespace(), chunk.chainType(), chunk.stageCode(),
            chunk.sourceType(), chunk.sourceId(), chunk.content(), chunk.contentHash(), chunk.createdAt());
    }
}
