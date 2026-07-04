package com.aimv.interfaces.knowledge;

import com.aimv.domain.shared.ChainType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReindexKnowledgeRequest(
        @NotBlank String namespace,
        @NotNull ChainType chainType
) {
}
