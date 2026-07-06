package com.aimv.interfaces.project;

import com.aimv.application.project.ProjectApplicationService;
import com.aimv.domain.chain.LatestChainRun;
import com.aimv.shared.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectApplicationService projectApplicationService;

    public ProjectController(ProjectApplicationService projectApplicationService) {
        this.projectApplicationService = projectApplicationService;
    }

    @PostMapping
    public ApiResponse<ProjectDto> create(@Valid @RequestBody CreateProjectRequest request) {
        return ApiResponse.ok(ProjectDto.from(projectApplicationService.create(request.title(), request.goal())));
    }

    @GetMapping
    public ApiResponse<List<ProjectDto>> listRecent() {
        // 一次批量取回「项目→最新链路(id+状态)」映射，避免每个项目单独查（N+1）。
        Map<String, LatestChainRun> latestRuns = projectApplicationService.latestChainRunByProject();
        return ApiResponse.ok(projectApplicationService.listRecent().stream()
            .map(project -> {
                LatestChainRun latest = latestRuns.get(project.projectId());
                return ProjectDto.from(project,
                    latest == null ? null : latest.chainRunId(),
                    latest == null ? null : latest.status());
            })
            .toList());
    }

    @DeleteMapping("/{projectId}")
    public ApiResponse<Void> delete(@PathVariable String projectId) {
        projectApplicationService.delete(projectId);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{projectId}/pin")
    public ApiResponse<Void> setPinned(@PathVariable String projectId, @RequestBody PinProjectRequest request) {
        projectApplicationService.setPinned(projectId, request.pinned());
        return ApiResponse.ok(null);
    }
}
