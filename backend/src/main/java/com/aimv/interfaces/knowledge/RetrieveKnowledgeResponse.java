package com.aimv.interfaces.knowledge;

import com.aimv.application.knowledge.KnowledgeApplicationService.RetrievalResult;
import com.aimv.domain.knowledge.RagCoverage;
import com.aimv.domain.shared.ChainType;
import java.util.List;

public record RetrieveKnowledgeResponse(
        String retrievalRecordId,
        String namespace,
        ChainType chainType,
        String stageCode,
        List<KnowledgeChunkDto> chunks,
        RagCoverage coverage
) {

    public static RetrieveKnowledgeResponse from(RetrievalResult result) {
        return new RetrieveKnowledgeResponse(result.retrievalRecordId(), result.namespace(), result.chainType(),
            result.stageCode(), result.chunks().stream()
                .map(KnowledgeChunkDto::from)
                .toList(), result.coverage());
    }
}
