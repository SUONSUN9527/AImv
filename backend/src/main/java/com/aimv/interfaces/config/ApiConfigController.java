package com.aimv.interfaces.config;

import com.aimv.application.config.ApiConfigApplicationService;
import com.aimv.domain.shared.ChainType;
import com.aimv.shared.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ApiConfigController {

    private final ApiConfigApplicationService apiConfigApplicationService;

    public ApiConfigController(ApiConfigApplicationService apiConfigApplicationService) {
        this.apiConfigApplicationService = apiConfigApplicationService;
    }

    @GetMapping("/api-configs")
    public ApiResponse<List<ApiConfigSlotDto>> list(@RequestParam ChainType chainType) {
        return ApiResponse.ok(apiConfigApplicationService.listSlots(chainType).stream()
            .map(ApiConfigSlotDto::from)
            .toList());
    }

    @PostMapping("/api-configs/{chainType}/{capabilityType}/keys")
    public ApiResponse<ApiKeySummaryDto> addKey(@PathVariable ChainType chainType,
            @PathVariable String capabilityType, @Valid @RequestBody AddApiKeyRequest request) {
        return ApiResponse.ok(ApiKeySummaryDto.from(apiConfigApplicationService.addKey(chainType, capabilityType,
            request.provider(), request.label(), request.apiKey(), request.model())));
    }

    @PostMapping("/api-keys/{apiKeyId}:verify")
    public ApiResponse<ApiKeySummaryDto> verify(@PathVariable String apiKeyId) {
        return ApiResponse.ok(ApiKeySummaryDto.from(apiConfigApplicationService.verify(apiKeyId)));
    }

    @PostMapping("/api-keys/{apiKeyId}:select")
    public ApiResponse<ApiKeySummaryDto> select(@PathVariable String apiKeyId) {
        return ApiResponse.ok(ApiKeySummaryDto.from(apiConfigApplicationService.select(apiKeyId)));
    }

    @DeleteMapping("/api-keys/{apiKeyId}")
    public ApiResponse<Void> delete(@PathVariable String apiKeyId) {
        apiConfigApplicationService.delete(apiKeyId);
        return ApiResponse.ok(null);
    }
}
