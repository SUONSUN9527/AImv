package com.aimv.interfaces.project;

import com.aimv.application.project.ProjectApplicationService;
import com.aimv.shared.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
        // 一次批量取回「项目→最新链路」映射，避免每个项目单独查（N+1）。
        Map<String, String> latestChainRunIds = projectApplicationService.latestChainRunIdByProject();
        return ApiResponse.ok(projectApplicationService.listRecent().stream()
            .map(project -> ProjectDto.from(project, latestChainRunIds.get(project.projectId())))
            .toList());
    }
}
