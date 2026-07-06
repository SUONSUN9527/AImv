package com.aimv.domain.project;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository {

    CreativeProject save(CreativeProject project);

    Optional<CreativeProject> findById(String projectId);

    List<CreativeProject> findRecent(int limit);

    /** 删除一段对话（project）及其级联的链路/知识数据；不存在时静默返回。 */
    void delete(String projectId);

    /** 置顶/取消置顶：pinnedAt 非空表示置顶，null 表示取消；项目不存在时静默返回。 */
    void updatePinned(String projectId, java.time.Instant pinnedAt);
}
