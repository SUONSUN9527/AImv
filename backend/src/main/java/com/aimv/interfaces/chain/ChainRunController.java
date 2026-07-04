package com.aimv.interfaces.chain;

import com.aimv.application.chain.ChainRunApplicationService;
import com.aimv.domain.shared.ChainType;
import com.aimv.shared.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ChainRunController {

    private final ChainRunApplicationService chainRunApplicationService;

    public ChainRunController(ChainRunApplicationService chainRunApplicationService) {
        this.chainRunApplicationService = chainRunApplicationService;
    }

    @PostMapping("/projects/{projectId}/image-chain-runs")
    public ApiResponse<ChainRunDto> startImage(@PathVariable String projectId,
            @Valid @RequestBody CreateChainRunRequest request) {
        return ApiResponse.ok(ChainRunDto.from(
            chainRunApplicationService.start(projectId, ChainType.IMAGE, request.userGoal())));
    }

    @PostMapping("/projects/{projectId}/video-chain-runs")
    public ApiResponse<ChainRunDto> startVideo(@PathVariable String projectId,
            @Valid @RequestBody CreateChainRunRequest request) {
        return ApiResponse.ok(ChainRunDto.from(
            chainRunApplicationService.start(projectId, ChainType.VIDEO, request.userGoal())));
    }

    @GetMapping("/chain-runs/{chainRunId}")
    public ApiResponse<ChainRunDto> get(@PathVariable String chainRunId) {
        return ApiResponse.ok(ChainRunDto.from(chainRunApplicationService.get(chainRunId)));
    }

    @PostMapping("/chain-runs/{chainRunId}:cancel")
    public ApiResponse<ChainRunDto> cancel(@PathVariable String chainRunId) {
        return ApiResponse.ok(ChainRunDto.from(chainRunApplicationService.cancel(chainRunId)));
    }

    @PostMapping("/stage-runs/{stageRunId}:redo")
    public ApiResponse<ChainRunDto> redo(@PathVariable String stageRunId) {
        return ApiResponse.ok(ChainRunDto.from(chainRunApplicationService.redoStage(stageRunId)));
    }

    @GetMapping("/artifacts")
    public ApiResponse<List<ArtifactDto>> artifacts() {
        return ApiResponse.ok(chainRunApplicationService.artifacts().stream().map(ArtifactDto::from).toList());
    }

    @GetMapping("/chain-runs/{chainRunId}/api-selection-snapshot")
    public ApiResponse<List<ApiSelectionSnapshotDto>> snapshots(@PathVariable String chainRunId) {
        return ApiResponse.ok(chainRunApplicationService.snapshots(chainRunId).stream()
            .map(ApiSelectionSnapshotDto::from)
            .toList());
    }
}
