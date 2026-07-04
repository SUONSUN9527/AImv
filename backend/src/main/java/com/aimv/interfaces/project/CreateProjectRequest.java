package com.aimv.interfaces.project;

import jakarta.validation.constraints.NotBlank;

public record CreateProjectRequest(
        @NotBlank String title,
        @NotBlank String goal
) {
}
