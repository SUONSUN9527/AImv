package com.aimv.interfaces.knowledge;

import com.aimv.domain.shared.ChainType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RetrieveKnowledgeRequest(
        @NotBlank String namespace,
        @NotNull ChainType chainType,
        @NotBlank String stageCode,
        @NotBlank String query,
        Integer topK
) {
}
