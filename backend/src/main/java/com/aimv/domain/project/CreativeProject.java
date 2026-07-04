package com.aimv.domain.project;

import java.time.Instant;

public record CreativeProject(
        String projectId,
        String title,
        String goal,
        Instant createdAt
) {
}
