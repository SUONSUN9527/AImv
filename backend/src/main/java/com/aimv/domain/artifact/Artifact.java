package com.aimv.domain.artifact;

import com.aimv.domain.shared.ChainType;
import java.time.Instant;
import java.util.Map;

public record Artifact(
        String artifactId,
        String chainRunId,
        ChainType chainType,
        ArtifactKind artifactKind,
        String displayName,
        String url,
        String contentHash,
        Map<String, Object> metadata,
        Instant createdAt
) {
}
