package com.aimv.application.agent;

import com.aimv.domain.agent.AgentDefinition;
import com.aimv.domain.agent.AgentNodeRun;
import com.aimv.domain.agent.AgentNodeRunRepository;
import com.aimv.domain.agent.AgentNodeRunStatus;
import com.aimv.domain.capability.ApiCredential;
import com.aimv.domain.capability.ApiKeyStatus;
import com.aimv.domain.capability.FreeModelGate;
import com.aimv.domain.capability.FreeModelGateStatus;
import com.aimv.domain.externaljob.ExternalJob;
import com.aimv.domain.externaljob.ExternalJobRepository;
import com.aimv.domain.externaljob.ExternalJobStatus;
import com.aimv.domain.provider.ProviderCapabilityEvidence;
import com.aimv.domain.provider.ProviderHttpGateway;
import com.aimv.domain.provider.ProviderHttpRequest;
import com.aimv.domain.provider.ProviderHttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DefaultAgentNodeFactory implements AgentNodeFactory {

    private static final String FREE_PROVIDER_RETRY_POLICY = "FREE_PROVIDER_RETRY_ONLY";
    /**
     * payload 中携带该 key 时，节点不调用外部 provider，直接把该 map 作为 partialOutput 落地。
     * 用于能力预检等可以由后端确定性事实回答的阶段，避免为拿一个结构化结论去调用大模型。
     */
    public static final String DETERMINISTIC_PARTIAL_OUTPUT_KEY = "deterministicPartialOutput";
    public static final String DETERMINISTIC_METADATA_KEY = "deterministicMetadata";

    private final AgentNodeRunRepository agentNodeRunRepository;
    private final ExternalJobRepository externalJobRepository;
    private final ProviderHttpGateway providerHttpGateway;

    public DefaultAgentNodeFactory(AgentNodeRunRepository agentNodeRunRepository,
            ExternalJobRepository externalJobRepository,
            ProviderHttpGateway providerHttpGateway) {
        this.agentNodeRunRepository = agentNodeRunRepository;
        this.externalJobRepository = externalJobRepository;
        this.providerHttpGateway = providerHttpGateway;
    }

    @Override
    public BaseAgentNode create(AgentDefinition definition, String chainRunId, String stageRunId, String nodeRunId) {
        return input -> executeNode(definition, chainRunId, stageRunId, nodeRunId, input);
    }

    private AgentNodeResult executeNode(AgentDefinition definition, String chainRunId, String stageRunId,
            String nodeRunId, AgentNodeInput input) {
        if (input.payload().get(DETERMINISTIC_PARTIAL_OUTPUT_KEY) instanceof Map<?, ?> deterministicOutput) {
            return executeDeterministicNode(definition, chainRunId, stageRunId, nodeRunId, input,
                deterministicOutput);
        }
        return executeProviderNode(definition, chainRunId, stageRunId, nodeRunId, input);
    }

    private AgentNodeResult executeDeterministicNode(AgentDefinition definition, String chainRunId,
            String stageRunId, String nodeRunId, AgentNodeInput input, Map<?, ?> deterministicOutput) {
        ApiCredential credential = input.credential();
        Instant now = Instant.now();
        FreeModelGate freeModelGate = freeModelGate(credential, now);
        Map<String, Object> partialOutput = new LinkedHashMap<>();
        deterministicOutput.forEach((key, value) -> partialOutput.put(String.valueOf(key), value));
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("adapterKind", "LOCAL_DETERMINISTIC");
        metadata.put("capabilityType", definition.capabilityType());
        metadata.put("stageCode", input.stageCode());
        metadata.put("provider", credential.provider());
        if (input.payload().get(DETERMINISTIC_METADATA_KEY) instanceof Map<?, ?> deterministicMetadata) {
            deterministicMetadata.forEach((key, value) -> metadata.put(String.valueOf(key), value));
        }
        metadata.put("partialOutput", Map.copyOf(partialOutput));
        String providerJobId = "local-deterministic-" + nodeRunId;
        AgentNodeRunStatus nodeStatus = ProviderCapabilityEvidence.satisfiesCapability(
            definition.capabilityType(), metadata) ? AgentNodeRunStatus.SUCCEEDED : AgentNodeRunStatus.FAILED;
        String outputSummary = nodeStatus == AgentNodeRunStatus.SUCCEEDED
            ? definition.nodeName() + " 基于已选凭证与能力注册信息完成确定性判定"
            : ProviderCapabilityEvidence.failureSummary(definition.capabilityType());
        AgentNodeRun nodeRun = new AgentNodeRun(nodeRunId, chainRunId, stageRunId, input.stageCode(),
            definition.nodeName(), nodeStatus, definition.capabilityType(), credential.provider(),
            credential.model(), providerJobId, freeModelGate, input.retrievalRecordId(), outputSummary, now,
            Instant.now());
        return new AgentNodeResult(agentNodeRunRepository.save(nodeRun), Map.copyOf(metadata), List.of());
    }

    private AgentNodeResult executeProviderNode(AgentDefinition definition, String chainRunId, String stageRunId,
            String nodeRunId, AgentNodeInput input) {
        ApiCredential credential = input.credential();
        Instant startedAt = Instant.now();
        FreeModelGate freeModelGate = freeModelGate(credential, startedAt);
        ProviderHttpRequest request = new ProviderHttpRequest("trace-" + UUID.randomUUID(), chainRunId, stageRunId,
            input.stageCode(), nodeRunId, definition.nodeName(), definition.capabilityType(),
            credential.provider(), credential.model(), freeModelGate.freeModelGateId(), credential.apiKeyId(),
            credential.maskedKey(), Map.copyOf(input.payload()));
        ProviderHttpResponse response = providerHttpGateway.invoke(request);
        Instant finishedAt = Instant.now();
        ExternalJobStatus externalJobStatus = ExternalJobStatus.fromProviderStatus(response.status());
        externalJobRepository.save(new ExternalJob("external-job-" + UUID.randomUUID(),
            response.providerJobId(), chainRunId, stageRunId, definition.capabilityType(), credential.provider(),
            externalJobStatus, FREE_PROVIDER_RETRY_POLICY, 0, requestHash(request), response.providerMetadata(),
            finishedAt, finishedAt));
        AgentNodeRunStatus nodeStatus = nodeStatus(definition.capabilityType(), externalJobStatus,
            response.providerMetadata());
        String outputSummary = outputSummary(definition.capabilityType(), response, nodeStatus,
            externalJobStatus);
        AgentNodeRun nodeRun = new AgentNodeRun(nodeRunId, chainRunId, stageRunId, input.stageCode(),
            definition.nodeName(), nodeStatus, definition.capabilityType(), credential.provider(),
            credential.model(), response.providerJobId(), freeModelGate, input.retrievalRecordId(),
            outputSummary, startedAt, finishedAt);
        return new AgentNodeResult(agentNodeRunRepository.save(nodeRun),
            Map.copyOf(response.providerMetadata()),
            response.artifactRefs() == null ? List.of() : List.copyOf(response.artifactRefs()));
    }

    /**
     * FreeModelGate 证据来自用户凭证的真实验证状态（verify 接口实测通过后记为 PASSED），
     * 不再无条件伪造 passed=true。
     */
    private FreeModelGate freeModelGate(ApiCredential credential, Instant checkedAt) {
        boolean passed = credential.freeModelGateStatus() == FreeModelGateStatus.PASSED
            && credential.status() != ApiKeyStatus.DELETED
            && credential.status() != ApiKeyStatus.INVALID;
        String quotaSnapshot = credential.lastVerifiedAt() == null
            ? "unverified"
            : "verified-free-key:lastVerifiedAt=" + credential.lastVerifiedAt();
        return new FreeModelGate("free-gate-" + UUID.randomUUID(), passed, credential.provider(),
            credential.model(), credential.capabilityType(), "free", false, quotaSnapshot, checkedAt);
    }

    private AgentNodeRunStatus nodeStatus(String capabilityType, ExternalJobStatus externalJobStatus,
            Map<String, Object> metadata) {
        if (externalJobStatus != ExternalJobStatus.SUCCEEDED) {
            return AgentNodeRunStatus.FAILED;
        }
        return ProviderCapabilityEvidence.satisfiesCapability(capabilityType, metadata)
            ? AgentNodeRunStatus.SUCCEEDED : AgentNodeRunStatus.FAILED;
    }

    private String outputSummary(String capabilityType, ProviderHttpResponse response, AgentNodeRunStatus nodeStatus,
            ExternalJobStatus externalJobStatus) {
        if (nodeStatus == AgentNodeRunStatus.FAILED && externalJobStatus == ExternalJobStatus.SUCCEEDED) {
            return ProviderCapabilityEvidence.failureSummary(capabilityType);
        }
        return response.outputSummary();
    }

    private String requestHash(ProviderHttpRequest request) {
        String fingerprint = request.chainRunId() + "|"
            + request.stageRunId() + "|"
            + request.stageCode() + "|"
            + request.nodeRunId() + "|"
            + request.nodeName() + "|"
            + request.capabilityType() + "|"
            + request.provider() + "|"
            + request.model() + "|"
            + request.freeModelGateId() + "|"
            + request.apiKeyId() + "|"
            + new TreeMap<>(request.input());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(fingerprint.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }
}
