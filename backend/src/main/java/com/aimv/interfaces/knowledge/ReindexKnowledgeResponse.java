package com.aimv.interfaces.knowledge;

import com.aimv.application.knowledge.KnowledgeApplicationService.ReindexResult;
import com.aimv.domain.shared.ChainType;

public record ReindexKnowledgeResponse(
        String namespace,
        ChainType chainType,
        long chunkCount,
        String status
) {

    public static ReindexKnowledgeResponse from(ReindexResult result) {
        return new ReindexKnowledgeResponse(result.namespace(), result.chainType(), result.chunkCount(),
            result.status());
    }
}
