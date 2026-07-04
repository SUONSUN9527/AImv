package com.aimv.interfaces.knowledge;

import com.aimv.domain.knowledge.RagCoverage;
import com.aimv.domain.knowledge.RetrievalHit;
import com.aimv.domain.knowledge.RetrievalRecord;
import com.aimv.domain.shared.ChainType;
import java.time.Instant;
import java.util.List;

public record RetrievalRecordDto(
        String retrievalRecordId,
        String namespace,
        ChainType chainType,
        String stageCode,
        String query,
        List<String> hitChunkIds,
        List<RetrievalHitDto> hits,
        RagCoverage coverage,
        Instant createdAt
) {

    public static RetrievalRecordDto from(RetrievalRecord record) {
        List<RetrievalHitDto> hits = record.hits().stream()
            .map(RetrievalHitDto::from)
            .toList();
        return new RetrievalRecordDto(record.retrievalRecordId(), record.namespace(), record.chainType(),
            record.stageCode(), record.query(), record.hitChunkIds(), hits, record.coverage(),
            record.createdAt());
    }

    public record RetrievalHitDto(
            String chunkId,
            double keywordScore,
            double vectorScore,
            Double rerankScore
    ) {

        static RetrievalHitDto from(RetrievalHit hit) {
            return new RetrievalHitDto(hit.chunkId(), hit.keywordScore(), hit.vectorScore(), hit.rerankScore());
        }
    }
}
