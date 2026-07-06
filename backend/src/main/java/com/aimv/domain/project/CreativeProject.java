package com.aimv.domain.project;

import java.time.Instant;

public record CreativeProject(
        String projectId,
        String title,
        String goal,
        Instant createdAt,
        Instant pinnedAt
) {

    /** 新建项目默认未置顶（pinnedAt 为 null）。 */
    public CreativeProject(String projectId, String title, String goal, Instant createdAt) {
        this(projectId, title, goal, createdAt, null);
    }
}
