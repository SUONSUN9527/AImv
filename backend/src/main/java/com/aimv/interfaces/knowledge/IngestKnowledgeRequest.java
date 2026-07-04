package com.aimv.interfaces.knowledge;

import com.aimv.domain.shared.ChainType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record IngestKnowledgeRequest(
        @NotBlank String namespace,
        @NotNull ChainType chainType,
        @NotBlank String stageCode,
        @NotBlank String sourceType,
        @NotBlank String sourceId,
        @NotBlank String content
) {
}
