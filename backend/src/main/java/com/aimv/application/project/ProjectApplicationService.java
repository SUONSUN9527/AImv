package com.aimv.application.project;

import com.aimv.domain.chain.ChainRunRepository;
import com.aimv.domain.chain.LatestChainRun;
import com.aimv.domain.project.CreativeProject;
import com.aimv.domain.project.ProjectRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ProjectApplicationService {

    private static final int RECENT_PROJECT_LIMIT = 50;

    private final ProjectRepository projectRepository;
    private final ChainRunRepository chainRunRepository;

    public ProjectApplicationService(ProjectRepository projectRepository, ChainRunRepository chainRunRepository) {
        this.projectRepository = projectRepository;
        this.chainRunRepository = chainRunRepository;
    }

    public CreativeProject create(String title, String goal) {
        CreativeProject project = new CreativeProject("project-" + UUID.randomUUID(), title, goal, Instant.now());
        return projectRepository.save(project);
    }

    public List<CreativeProject> listRecent() {
        return projectRepository.findRecent(RECENT_PROJECT_LIMIT);
    }

    /** 删除一段对话及其全部链路/知识数据（外键级联）。幂等：项目不存在也不报错。 */
    public void delete(String projectId) {
        projectRepository.delete(projectId);
    }

    /** 置顶/取消置顶一段对话。置顶时记录当前时间，取消时清空。 */
    public void setPinned(String projectId, boolean pinned) {
        projectRepository.updatePinned(projectId, pinned ? Instant.now() : null);
    }

    /** projectId → 该项目最新链路（id + 状态），供前端把历史项做成可点击直达并展示真实状态。 */
    public Map<String, LatestChainRun> latestChainRunByProject() {
        return chainRunRepository.latestChainRunByProject();
    }
}
