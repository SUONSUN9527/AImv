package com.aimv.application.capability;

import com.aimv.domain.capability.ApiCapabilityCatalog;
import com.aimv.domain.capability.ApiConfigRepository;
import com.aimv.domain.capability.ApiCredential;
import com.aimv.domain.capability.CapabilityAcquireDecision;
import com.aimv.domain.capability.CapabilityDescriptor;
import com.aimv.domain.capability.CapabilityDiscoveryResult;
import com.aimv.domain.capability.CapabilityRequirement;
import com.aimv.domain.shared.ChainType;
import com.aimv.shared.error.BusinessException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class CapabilityApplicationService {

    private final ApiConfigRepository apiConfigRepository;

    public CapabilityApplicationService(ApiConfigRepository apiConfigRepository) {
        this.apiConfigRepository = apiConfigRepository;
    }

    public List<CapabilityDescriptor> listRegisteredCapabilities() {
        return ApiCapabilityCatalog.descriptors().stream()
            .sorted(Comparator.comparing(CapabilityDescriptor::capabilityType))
            .toList();
    }

    public CapabilityDiscoveryResult discover(ChainType chainType, String stageCode) {
        Map<String, ApiCredential> selectedByCapability = apiConfigRepository.findSelectedCredentials(chainType)
            .stream()
            .collect(Collectors.toMap(ApiCredential::capabilityType, Function.identity()));

        List<CapabilityRequirement> requirements = ApiCapabilityCatalog.slots(chainType).stream()
            .map(slot -> {
                ApiCredential selected = selectedByCapability.get(slot.capabilityType());
                String selectedApiKeyId = selected == null ? null : selected.apiKeyId();
                return new CapabilityRequirement(slot.capabilityType(), slot.label(), true, selected != null,
                    selectedApiKeyId);
            })
            .toList();

        return new CapabilityDiscoveryResult(chainType, stageCode, requirements);
    }

    public CapabilityAcquireDecision acquire(String capabilityType, boolean downloadModelWeights) {
        if (downloadModelWeights) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "LOCAL_MODEL_DOWNLOAD_REJECTED",
                "技术文档禁止本地下载或部署大模型权重");
        }
        return new CapabilityAcquireDecision(capabilityType, false, "WAITING_CAPABILITY",
            "当前实现只登记云端 HTTP adapter 能力，不自动安装第三方能力");
    }

    public CapabilityDescriptor verify(String capabilityId) {
        return ApiCapabilityCatalog.descriptors().stream()
            .filter(descriptor -> descriptor.capabilityId().equals(capabilityId)
                || descriptor.capabilityType().equals(capabilityId))
            .findFirst()
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "CAPABILITY_NOT_FOUND", "能力不存在"));
    }
}
