package com.aimv.application.chain;

import com.aimv.application.chain.ChainRunExecutionService.ChainExecutionCommand;
import com.aimv.domain.artifact.Artifact;
import com.aimv.domain.artifact.ArtifactRepository;
import com.aimv.domain.capability.ApiCapabilityCatalog;
import com.aimv.domain.capability.ApiConfigRepository;
import com.aimv.domain.capability.ApiCredential;
import com.aimv.domain.capability.ApiKeyStatus;
import com.aimv.domain.capability.ApiSelectionSnapshot;
import com.aimv.domain.capability.FreeModelGateStatus;
import com.aimv.domain.chain.ChainRun;
import com.aimv.domain.chain.ChainRunRepository;
import com.aimv.domain.chain.ChainRunStatus;
import com.aimv.domain.chain.StageCatalog;
import com.aimv.domain.chain.StageRun;
import com.aimv.domain.chain.StageRunStatus;
import com.aimv.domain.project.ProjectRepository;
import com.aimv.domain.shared.ChainType;
import com.aimv.shared.error.BusinessException;
import com.aimv.shared.error.ResourceNotFoundException;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 链路用例入口。启动前的凭证校验同步完成（配置错误立即以业务错误返回），
 * 链路本身交给 {@link ChainRunExecutionService} 在执行器中推进：
 * 生产环境为后台线程池（前端轮询中间状态），测试环境为同步执行器。
 */
@Service
public class ChainRunApplicationService {

    private final ProjectRepository projectRepository;
    private final ChainRunRepository chainRunRepository;
    private final ArtifactRepository artifactRepository;
    private final ApiConfigRepository apiConfigRepository;
    private final ChainRunExecutionService executionService;
    private final TaskExecutor chainRunTaskExecutor;

    public ChainRunApplicationService(ProjectRepository projectRepository, ChainRunRepository chainRunRepository,
            ArtifactRepository artifactRepository, ApiConfigRepository apiConfigRepository,
            ChainRunExecutionService executionService,
            @Qualifier("chainRunTaskExecutor") TaskExecutor chainRunTaskExecutor) {
        this.projectRepository = projectRepository;
        this.chainRunRepository = chainRunRepository;
        this.artifactRepository = artifactRepository;
        this.apiConfigRepository = apiConfigRepository;
        this.executionService = executionService;
        this.chainRunTaskExecutor = chainRunTaskExecutor;
    }

    public ChainRun start(String projectId, ChainType chainType, String userGoal) {
        projectRepository.findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("项目不存在"));

        List<ApiCredential> selectedCredentials = selectedCredentials(chainType);
        String chainRunId = "chain-" + UUID.randomUUID();
        Map<String, ApiCredential> selectedByCapability = selectedCredentials.stream()
            .collect(Collectors.toMap(ApiCredential::capabilityType, Function.identity()));

        Instant now = Instant.now();
        String firstStageCode = StageCatalog.stages(chainType).get(0).stageCode();
        // 顺序关键：先落库 chain_run，再存 api_selection_snapshot（快照外键引用 chain_run）。
        chainRunRepository.save(new ChainRun(chainRunId, projectId, chainType, userGoal,
            ChainRunStatus.EXECUTING, firstStageCode, List.of(), List.of(), null, now, now));
        selectedCredentials.forEach(credential -> executionService.saveSnapshot(chainRunId, credential));

        chainRunTaskExecutor.execute(() -> executionService.execute(new ChainExecutionCommand(
            chainRunId, projectId, chainType, userGoal, selectedByCapability, 0, Set.of())));
        return get(chainRunId);
    }

    public ChainRun get(String chainRunId) {
        return chainRunRepository.findById(chainRunId)
            .orElseThrow(() -> new ResourceNotFoundException("链路运行不存在"));
    }

    public ChainRun cancel(String chainRunId) {
        ChainRun current = get(chainRunId);
        ChainRun cancelled = new ChainRun(current.chainRunId(), current.projectId(), current.chainType(),
            current.userGoal(), ChainRunStatus.CANCELLED, current.currentStageCode(), current.stageRuns(),
            current.artifacts(), "用户取消", current.createdAt(), Instant.now());
        return chainRunRepository.save(cancelled);
    }

    public ChainRun redoStage(String stageRunId) {
        ChainRun current = chainRunRepository.findByStageRunId(stageRunId)
            .orElseThrow(() -> new ResourceNotFoundException("阶段运行不存在"));
        if (current.status() == ChainRunStatus.EXECUTING) {
            throw new BusinessException(HttpStatus.CONFLICT, "CHAIN_RUN_EXECUTING",
                "链路仍在执行中，不能重做阶段");
        }
        int redoRunIndex = stageIndex(current, stageRunId);
        StageRun redoStage = current.stageRuns().get(redoRunIndex);
        int redoCatalogIndex = catalogIndex(current.chainType(), redoStage.stageCode());
        Map<String, ApiCredential> credentialByCapability = credentialsForRedo(current, redoStage);
        List<StageRun> keptStages = List.copyOf(current.stageRuns().subList(0, redoRunIndex));

        artifactRepository.deleteByChainRunId(current.chainRunId());
        chainRunRepository.save(new ChainRun(current.chainRunId(), current.projectId(), current.chainType(),
            current.userGoal(), ChainRunStatus.EXECUTING, redoStage.stageCode(), keptStages, List.of(), null,
            current.createdAt(), Instant.now()));

        chainRunTaskExecutor.execute(() -> executionService.execute(new ChainExecutionCommand(
            current.chainRunId(), current.projectId(), current.chainType(), current.userGoal(),
            credentialByCapability, redoCatalogIndex, Set.of())));
        return get(current.chainRunId());
    }

    public List<Artifact> artifacts() {
        return artifactRepository.findAll();
    }

    public List<ApiSelectionSnapshot> snapshots(String chainRunId) {
        get(chainRunId);
        return apiConfigRepository.findSnapshots(chainRunId);
    }

    private List<ApiCredential> selectedCredentials(ChainType chainType) {
        Map<String, ApiCredential> selectedByCapability = apiConfigRepository.findSelectedCredentials(chainType)
            .stream()
            .collect(Collectors.toMap(ApiCredential::capabilityType, Function.identity()));

        List<String> missing = ApiCapabilityCatalog.slots(chainType).stream()
            .map(ApiCapabilityCatalog.SlotDefinition::capabilityType)
            .filter(capabilityType -> !selectedByCapability.containsKey(capabilityType))
            .toList();

        if (!missing.isEmpty()) {
            throw new BusinessException(HttpStatus.CONFLICT, "API_CAPABILITY_NOT_CONFIGURED",
                "链路启动缺少使用中的免费能力配置: " + String.join(", ", missing));
        }

        List<String> failedGateCapabilities = ApiCapabilityCatalog.slots(chainType).stream()
            .map(ApiCapabilityCatalog.SlotDefinition::capabilityType)
            .filter(capabilityType -> !freeGatePassed(selectedByCapability.get(capabilityType)))
            .toList();

        if (!failedGateCapabilities.isEmpty()) {
            throw new BusinessException(HttpStatus.CONFLICT, "FREE_MODEL_GATE_FAILED",
                "链路启动前免费模型门禁未通过: " + String.join(", ", failedGateCapabilities));
        }

        return ApiCapabilityCatalog.slots(chainType).stream()
            .map(slot -> selectedByCapability.get(slot.capabilityType()))
            .toList();
    }

    private boolean freeGatePassed(ApiCredential credential) {
        // 用户显式启用（selected→ACTIVE）即视为可用，含自带付费 key；
        // 不再强制 freeModelGate==PASSED（那会拦住付费模型）。
        return credential != null && credential.status() == ApiKeyStatus.ACTIVE;
    }

    private int stageIndex(ChainRun chainRun, String stageRunId) {
        for (int index = 0; index < chainRun.stageRuns().size(); index++) {
            if (chainRun.stageRuns().get(index).stageRunId().equals(stageRunId)) {
                return index;
            }
        }
        throw new ResourceNotFoundException("阶段运行不存在");
    }

    private int catalogIndex(ChainType chainType, String stageCode) {
        List<StageCatalog.StageDefinition> stages = StageCatalog.stages(chainType);
        return IntStream.range(0, stages.size())
            .filter(index -> stages.get(index).stageCode().equals(stageCode))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("阶段定义不存在"));
    }

    private Map<String, ApiCredential> credentialsForRedo(ChainRun chainRun, StageRun redoStage) {
        if (isRecoveryReselectionStage(redoStage)) {
            List<ApiCredential> selectedCredentials = selectedCredentials(chainRun.chainType());
            selectedCredentials.forEach(credential ->
                executionService.saveSnapshot(chainRun.chainRunId(), credential));
            return selectedCredentials.stream()
                .collect(Collectors.toMap(ApiCredential::capabilityType, Function.identity()));
        }
        return snapshotCredentials(chainRun);
    }

    private boolean isRecoveryReselectionStage(StageRun stageRun) {
        return (stageRun.status() == StageRunStatus.WAITING_USER
                || stageRun.status() == StageRunStatus.WAITING_CAPABILITY)
            && "recovery-policy.v1".equals(stageRun.reviewReport().rubricVersion());
    }

    private Map<String, ApiCredential> snapshotCredentials(ChainRun chainRun) {
        Map<String, ApiCredential> credentialByCapability = new LinkedHashMap<>();
        apiConfigRepository.findSnapshots(chainRun.chainRunId()).stream()
            .sorted(Comparator.comparing(ApiSelectionSnapshot::createdAt))
            .forEach(snapshot -> credentialByCapability.put(snapshot.capabilityType(),
                snapshotCredential(snapshot)));
        return Map.copyOf(credentialByCapability);
    }

    private ApiCredential snapshotCredential(ApiSelectionSnapshot snapshot) {
        return new ApiCredential(snapshot.apiKeyId(), snapshot.chainType(), snapshot.capabilityType(),
            snapshot.provider(), "snapshot", "", "", snapshot.maskedKey(), snapshot.model(), ApiKeyStatus.ACTIVE,
            true, snapshot.createdAt(), FreeModelGateStatus.PASSED);
    }
}
