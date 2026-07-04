package com.aimv.interfaces.project;

import com.aimv.domain.project.CreativeProject;
import java.time.Instant;

public record ProjectDto(
        String projectId,
        String title,
        String goal,
        Instant createdAt,
        String latestChainRunId
) {

    /** 创建场景：项目刚建，尚无链路。 */
    public static ProjectDto from(CreativeProject project) {
        return from(project, null);
    }

    /** 列表场景：带上该项目最新链路ID（可为 null），前端据此决定历史项是否可点击。 */
    public static ProjectDto from(CreativeProject project, String latestChainRunId) {
        return new ProjectDto(project.projectId(), project.title(), project.goal(), project.createdAt(),
            latestChainRunId);
    }
}
