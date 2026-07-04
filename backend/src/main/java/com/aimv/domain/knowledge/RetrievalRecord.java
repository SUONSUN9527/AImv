package com.aimv.domain.knowledge;

import com.aimv.domain.shared.ChainType;
import java.time.Instant;
import java.util.List;

public record RetrievalRecord(
        String retrievalRecordId,
        String namespace,
        ChainType chainType,
        String stageCode,
        String query,
        List<RetrievalHit> hits,
        RagCoverage coverage,
        Instant createdAt
) {

    public RetrievalRecord {
        hits = hits == null ? List.of() : List.copyOf(hits);
        if (coverage == null) {
            coverage = new RagCoverage(false, false, false, false, false, false);
        }
    }

    /**
     * 兼容旧调用：按融合顺序返回命中的 chunkId 列表。
     */
    public List<String> hitChunkIds() {
        return hits.stream().map(RetrievalHit::chunkId).toList();
    }
}
