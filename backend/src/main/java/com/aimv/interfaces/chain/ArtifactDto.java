package com.aimv.interfaces.chain;

import com.aimv.domain.artifact.Artifact;
import com.aimv.domain.artifact.ArtifactKind;
import com.aimv.domain.shared.ChainType;
import java.time.Instant;
import java.util.Map;

public record ArtifactDto(
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

    static ArtifactDto from(Artifact artifact) {
        return new ArtifactDto(artifact.artifactId(), artifact.chainRunId(), artifact.chainType(),
            artifact.artifactKind(), artifact.displayName(), artifact.url(), artifact.contentHash(),
            artifact.metadata(), artifact.createdAt());
    }
}
