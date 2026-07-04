package com.aimv.interfaces.capability;

import com.aimv.application.capability.CapabilityApplicationService;
import com.aimv.domain.capability.CapabilityAcquireDecision;
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
public class CapabilityController {

    private final CapabilityApplicationService capabilityApplicationService;

    public CapabilityController(CapabilityApplicationService capabilityApplicationService) {
        this.capabilityApplicationService = capabilityApplicationService;
    }

    @GetMapping("/capabilities")
    public ApiResponse<List<CapabilityDto>> list() {
        return ApiResponse.ok(capabilityApplicationService.listRegisteredCapabilities().stream()
            .map(CapabilityDto::from)
            .toList());
    }

    @PostMapping("/capabilities:discover")
    public ApiResponse<CapabilityDiscoveryDto> discover(@Valid @RequestBody CapabilityDiscoverRequest request) {
        return ApiResponse.ok(CapabilityDiscoveryDto.from(capabilityApplicationService.discover(request.chainType(),
            request.stageCode())));
    }

    @PostMapping("/capabilities:acquire")
    public ApiResponse<CapabilityAcquireDecision> acquire(@Valid @RequestBody CapabilityAcquireRequest request) {
        return ApiResponse.ok(capabilityApplicationService.acquire(request.capabilityType(),
            request.downloadModelWeights()));
    }

    @PostMapping("/capabilities/{capabilityId}:verify")
    public ApiResponse<CapabilityDto> verify(@PathVariable String capabilityId) {
        return ApiResponse.ok(CapabilityDto.from(capabilityApplicationService.verify(capabilityId)));
    }

    public record CapabilityDiscoverRequest(
            ChainType chainType,
            String stageCode
    ) {
    }
}
