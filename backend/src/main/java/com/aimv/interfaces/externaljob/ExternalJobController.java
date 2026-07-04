package com.aimv.interfaces.externaljob;

import com.aimv.application.externaljob.ExternalJobApplicationService;
import com.aimv.shared.api.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chain-runs/{chainRunId}/external-jobs")
public class ExternalJobController {

    private final ExternalJobApplicationService externalJobApplicationService;

    public ExternalJobController(ExternalJobApplicationService externalJobApplicationService) {
        this.externalJobApplicationService = externalJobApplicationService;
    }

    @GetMapping
    public ApiResponse<List<ExternalJobDto>> list(@PathVariable String chainRunId) {
        List<ExternalJobDto> jobs = externalJobApplicationService.listByChainRunId(chainRunId)
            .stream()
            .map(ExternalJobDto::from)
            .toList();
        return ApiResponse.ok(jobs);
    }
}
