package com.aimv.interfaces.project;

import com.aimv.domain.project.CreativeProject;
import java.time.Instant;

public record ProjectDto(
        String projectId,
        String title,
        String goal,
        Instant createdAt,
        String latestChainRunId,
        String latestChainRunStatus,
        Instant pinnedAt
) {

    /** 创建场景：项目刚建，尚无链路。 */
    public static ProjectDto from(CreativeProject project) {
        return from(project, null, null);
    }

    /** 列表场景：带上最新链路 id + 状态 + 置顶时间，前端据此决定可点击性、真实状态与置顶排序。 */
    public static ProjectDto from(CreativeProject project, String latestChainRunId, String latestChainRunStatus) {
        return new ProjectDto(project.projectId(), project.title(), project.goal(), project.createdAt(),
            latestChainRunId, latestChainRunStatus, project.pinnedAt());
    }
}
