package com.aimv.application.config;

import com.aimv.domain.capability.ApiCapabilityCatalog;
import com.aimv.domain.capability.ApiConfigRepository;
import com.aimv.domain.capability.ApiConfigSlot;
import com.aimv.domain.capability.ApiCredential;
import com.aimv.domain.capability.ApiKeyStatus;
import com.aimv.domain.capability.FreeModelGateStatus;
import com.aimv.domain.provider.ProviderCapabilityEvidence;
import com.aimv.domain.provider.ProviderHttpGateway;
import com.aimv.domain.provider.ProviderHttpRequest;
import com.aimv.domain.shared.ChainType;
import com.aimv.shared.error.BusinessException;
import com.aimv.shared.error.ResourceNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ApiConfigApplicationService {

    private static final String FIXTURE_PROVIDER = "fixture-free";
    private static final String VERIFY_STAGE_CODE = "VERIFY";
    private static final String VERIFY_NODE_NAME = "FreeModelGateVerify";
    private static final String SUCCEEDED = "SUCCEEDED";

    private final ApiConfigRepository apiConfigRepository;
    private final ProviderHttpGateway providerHttpGateway;

    public ApiConfigApplicationService(ApiConfigRepository apiConfigRepository,
            ProviderHttpGateway providerHttpGateway) {
        this.apiConfigRepository = apiConfigRepository;
        this.providerHttpGateway = providerHttpGateway;
    }

    public List<ApiConfigSlot> listSlots(ChainType chainType) {
        return ApiCapabilityCatalog.slots(chainType).stream()
            .map(slot -> new ApiConfigSlot(chainType, slot.capabilityType(), slot.label(), true,
                apiConfigRepository.findCredentials(chainType, slot.capabilityType())))
            .toList();
    }

    public ApiCredential addKey(ChainType chainType, String capabilityType, String provider, String label,
            String apiKey, String model) {
        requireSupported(chainType, capabilityType);
        ApiCredential credential = new ApiCredential(
            "key-" + UUID.randomUUID(),
            chainType,
            capabilityType,
            provider,
            label,
            ApiKeyMasker.hash(apiKey),
            ApiKeyProtector.encrypt(apiKey),
            ApiKeyMasker.mask(apiKey),
            model == null || model.isBlank() ? "free-model" : model,
            ApiKeyStatus.AVAILABLE,
            false,
            null,
            FreeModelGateStatus.PENDING_VERIFY
        );
        return apiConfigRepository.save(credential);
    }

    public ApiCredential verify(String apiKeyId) {
        ApiCredential credential = credential(apiKeyId);
        if (!FIXTURE_PROVIDER.equals(credential.provider())) {
            verifyThroughAdapter(credential);
        }
        return apiConfigRepository.save(credential.verified(Instant.now()));
    }

    public ApiCredential select(String apiKeyId) {
        ApiCredential credential = credential(apiKeyId);
        // 用户显式启用某个 key 即视为授权使用（含自带付费 key）。免费门禁只是成本自动化，
        // 不再硬拦付费 key 被设为使用中——否则 *.free 能力永远用不了付费模型。
        apiConfigRepository.findCredentials(credential.chainType(), credential.capabilityType())
            .forEach(existing -> apiConfigRepository.save(existing.selected(false)));

        return apiConfigRepository.save(credential.selected(true));
    }

    public void delete(String apiKeyId) {
        ApiCredential credential = credential(apiKeyId);
        if (credential.selected()) {
            throw new BusinessException(HttpStatus.CONFLICT, "DELETE_SELECTED_KEY_REJECTED",
                "不能删除正在使用中的唯一 key，请先选择另一个可用 key");
        }
        apiConfigRepository.save(credential.deleted());
    }

    private ApiCredential credential(String apiKeyId) {
        return apiConfigRepository.findCredential(apiKeyId)
            .orElseThrow(() -> new ResourceNotFoundException("API key 不存在"));
    }

    private void requireSupported(ChainType chainType, String capabilityType) {
        if (!ApiCapabilityCatalog.supports(chainType, capabilityType)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "CHAIN_CAPABILITY_MISMATCH",
                "当前链路不支持该能力配置");
        }
    }

    private void verifyThroughAdapter(ApiCredential credential) {
        var response = providerHttpGateway.invoke(new ProviderHttpRequest(
            "trace-" + UUID.randomUUID(),
            "verify-chain-" + UUID.randomUUID(),
            "verify-stage-" + UUID.randomUUID(),
            VERIFY_STAGE_CODE,
            "verify-node-" + UUID.randomUUID(),
            VERIFY_NODE_NAME,
            credential.capabilityType(),
            credential.provider(),
            credential.model(),
            "gate-" + UUID.randomUUID(),
            credential.apiKeyId(),
            credential.maskedKey(),
            Map.of("operation", "VERIFY_API_KEY", "freeOnly", true)
        ));
        if (!SUCCEEDED.equals(response.status())) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "FREE_MODEL_GATE_FAILED",
                "免费模型门禁验证未通过");
        }
        if (!ProviderCapabilityEvidence.satisfiesCapability(credential.capabilityType(),
            response.providerMetadata())) {
            throw new BusinessException(HttpStatus.CONFLICT, "FREE_MODEL_GATE_FAILED",
                ProviderCapabilityEvidence.failureSummary(credential.capabilityType()));
        }
    }
}
