package com.aimv.interfaces.chain;

import jakarta.validation.constraints.NotBlank;

public record CreateChainRunRequest(
        @NotBlank String userGoal
) {
}
