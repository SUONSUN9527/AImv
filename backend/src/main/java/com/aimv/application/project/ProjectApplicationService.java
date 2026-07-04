package com.aimv.application.project;

import com.aimv.domain.chain.ChainRunRepository;
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

    /** projectId → 该项目最新链路ID，供前端把项目历史项也做成可点击直达 workspace。 */
    public Map<String, String> latestChainRunIdByProject() {
        return chainRunRepository.latestChainRunIdByProject();
    }
}
