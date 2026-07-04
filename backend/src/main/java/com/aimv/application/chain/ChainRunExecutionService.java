package com.aimv.application.chain;

import com.aimv.application.agent.AgentNodeFactory;
import com.aimv.application.agent.AgentNodeInput;
import com.aimv.application.agent.AgentNodeResult;
import com.aimv.application.agent.DefaultAgentNodeFactory;
import com.aimv.application.knowledge.KnowledgeApplicationService;
import com.aimv.application.knowledge.KnowledgeApplicationService.RetrievalResult;
import com.aimv.domain.agent.AgentDefinition;
import com.aimv.domain.agent.AgentNodeRunStatus;
import com.aimv.domain.artifact.Artifact;
import com.aimv.domain.artifact.ArtifactKind;
import com.aimv.domain.artifact.ArtifactRepository;
import com.aimv.domain.capability.ApiConfigRepository;
import com.aimv.domain.capability.ApiCredential;
import com.aimv.domain.capability.ApiKeyStatus;
import com.aimv.domain.capability.ApiSelectionSnapshot;
import com.aimv.domain.capability.FreeModelGate;
import com.aimv.domain.capability.FreeModelGateStatus;
import com.aimv.domain.chain.ChainRun;
import com.aimv.domain.chain.ChainRunRepository;
import com.aimv.domain.chain.ChainRunStatus;
import com.aimv.domain.chain.EvidenceCheckPolicy;
import com.aimv.domain.chain.EvidenceCheckReport;
import com.aimv.domain.chain.EvidenceClaim;
import com.aimv.domain.chain.NextStageContext;
import com.aimv.domain.chain.ReviewReport;
import com.aimv.domain.chain.StageCatalog;
import com.aimv.domain.chain.StageCoordinationResult;
import com.aimv.domain.chain.StageCoordinator;
import com.aimv.domain.chain.StagePartialOutput;
import com.aimv.domain.chain.StagePartialSchema;
import com.aimv.domain.chain.StageReviewPolicy;
import com.aimv.domain.chain.StageRun;
import com.aimv.domain.chain.StageRunStatus;
import com.aimv.domain.knowledge.EvidencePack;
import com.aimv.domain.knowledge.KnowledgeChunk;
import com.aimv.domain.shared.ChainType;
import com.aimv.shared.error.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 链路执行器：按 StageCatalog 逐阶段推进，每个阶段完成后立即落库，
 * 让 GET /api/chain-runs/{id} 能观测到真实的中间状态；产物来自 provider
 * 真实返回的 artifact 引用，不再返回硬编码 fixture 路径。
 */
@Service
public class ChainRunExecutionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChainRunExecutionService.class);

    /** 质量评审不达标时，同凭证「重生成+复审」的最大次数（harness loop，靠生成随机性重试达标）。 */
    private static final int MAX_QUALITY_RETRIES = 3;

    private static final String CONTEXT_SCHEMA_VERSION = "1.0";
    private static final String CHAIN_CONTEXT_SOURCE_TYPE = "CHAIN_CONTEXT";
    private static final String NEXT_STAGE_CONTEXT_SOURCE_TYPE = "NEXT_STAGE_CONTEXT";
    private static final String REVIEW_REPORT_SOURCE_TYPE = "REVIEW_REPORT";
    private static final String STAGE_OUTPUT_SOURCE_TYPE = "STAGE_OUTPUT";
    private static final String RAG_EVIDENCE_CONFLICT_CODE = "RAG_EVIDENCE_CONFLICT";
    private static final String STAGE_MAP_SOURCE_TYPE = "STAGE_MAP";
    private static final String USER_GOAL_SOURCE_TYPE = "USER_GOAL";
    private static final String FINAL_HANDOFF_STAGE = "DONE";
    private static final String NO_CANDIDATE_REF = "unavailable://no-candidate-ref";

    private final ChainRunRepository chainRunRepository;
    private final ArtifactRepository artifactRepository;
    private final ApiConfigRepository apiConfigRepository;
    private final KnowledgeApplicationService knowledgeApplicationService;
    private final AgentNodeFactory agentNodeFactory;
    private final ObjectMapper objectMapper;
    private final StageCoordinator stageCoordinator = new StageCoordinator();

    public ChainRunExecutionService(ChainRunRepository chainRunRepository, ArtifactRepository artifactRepository,
            ApiConfigRepository apiConfigRepository, KnowledgeApplicationService knowledgeApplicationService,
            AgentNodeFactory agentNodeFactory, ObjectMapper objectMapper) {
        this.chainRunRepository = chainRunRepository;
        this.artifactRepository = artifactRepository;
        this.apiConfigRepository = apiConfigRepository;
        this.knowledgeApplicationService = knowledgeApplicationService;
        this.agentNodeFactory = agentNodeFactory;
        this.objectMapper = objectMapper;
    }

    public record ChainExecutionCommand(
            String chainRunId,
            String projectId,
            ChainType chainType,
            String userGoal,
            Map<String, ApiCredential> credentials,
            int startCatalogIndex,
            Set<String> exhaustedGenerationApiKeyIds
    ) {
    }

    public void execute(ChainExecutionCommand command) {
        try {
            runChain(command);
        } catch (Exception exception) {
            LOGGER.error("chain run {} execution failed", command.chainRunId(), exception);
            markFailed(command.chainRunId(), exception);
        }
    }

    private void runChain(ChainExecutionCommand command) {
        List<StageCatalog.StageDefinition> stages = StageCatalog.stages(command.chainType());
        Map<String, ApiCredential> credentials = command.credentials();
        Set<String> exhaustedGenerationApiKeyIds = new LinkedHashSet<>(command.exhaustedGenerationApiKeyIds());
        Map<String, Map<String, Object>> stageOutputs = new LinkedHashMap<>();
        String namespace = chainNamespace(command.projectId(), command.chainRunId());
        StageBuildResult generationEvidence = null;
        StageBuildResult reviewEvidence = null;
        int index = command.startCatalogIndex();

        while (index < stages.size()) {
            if (isCancelled(command.chainRunId())) {
                return;
            }
            StageBuildResult result = buildStageWithReviewRetry(command, namespace, stages, index, credentials,
                stageOutputs, generationEvidence);
            if (!appendStage(command.chainRunId(), result.stageRun())) {
                return;
            }
            if (isQualityReviewFailure(result.stageRun())) {
                int generationIndex = index - 1;
                int preflightIndex = index - 2;
                StageBuildResult reviewRetry = result;
                boolean passedAfterRetry = false;
                // harness 循环：同凭证「重生成 + 复审」最多 MAX_QUALITY_RETRIES 次，直到质量达标。
                // 生成本身有随机性（i2v/t2v/出图），多试几次常能过；不再一次不过就直接报错。
                for (int attempt = 0; attempt < MAX_QUALITY_RETRIES; attempt++) {
                    StageBuildResult generationRetry = buildStage(command, namespace, stages, generationIndex,
                        credentials, stageOutputs, null);
                    if (!appendStage(command.chainRunId(), generationRetry.stageRun())) {
                        return;
                    }
                    if (generationRetry.stageRun().status() != StageRunStatus.SUCCEEDED) {
                        finalizeChain(command.chainRunId(), null, null);
                        return;
                    }
                    generationEvidence = generationRetry;
                    reviewRetry = buildStage(command, namespace, stages, index, credentials,
                        stageOutputs, generationEvidence);
                    if (!appendStage(command.chainRunId(), reviewRetry.stageRun())) {
                        return;
                    }
                    if (reviewRetry.stageRun().status() == StageRunStatus.SUCCEEDED) {
                        reviewEvidence = reviewRetry;
                        passedAfterRetry = true;
                        break;
                    }
                    if (!isQualityReviewFailure(reviewRetry.stageRun())) {
                        finalizeChain(command.chainRunId(), null, null);
                        return;
                    }
                }
                if (passedAfterRetry) {
                    index++;
                    continue;
                }
                // 多次仍不达标 → 尝试备用凭证；无备用则收尾为软阻塞态（非崩溃、可再次生成）。
                ApiCredential currentGeneration = credentials
                    .get(generationCapabilityType(command.chainType()));
                if (currentGeneration != null) {
                    exhaustedGenerationApiKeyIds.add(currentGeneration.apiKeyId());
                }
                Map<String, ApiCredential> alternativeCredentials = alternativeGenerationCredentials(
                    command.chainType(), credentials, exhaustedGenerationApiKeyIds);
                if (alternativeCredentials.isEmpty()) {
                    StageRun recoveryStage = recoveryPreflightBlockStage(command, namespace,
                        stages.get(preflightIndex), reviewRetry.stageRun());
                    appendStage(command.chainRunId(), recoveryStage);
                    finalizeChain(command.chainRunId(), null, null);
                    return;
                }
                saveSnapshot(command.chainRunId(),
                    alternativeCredentials.get(generationCapabilityType(command.chainType())));
                credentials = alternativeCredentials;
                generationEvidence = null;
                index = preflightIndex;
                continue;
            }
            if (result.stageRun().status() != StageRunStatus.SUCCEEDED) {
                finalizeChain(command.chainRunId(), null, null);
                return;
            }
            if (isGenerationStage(result.stageRun().stageCode())) {
                generationEvidence = result;
            }
            if (isQualityReviewStage(result.stageRun().stageCode())) {
                reviewEvidence = result;
            }
            index++;
        }
        finalizeChain(command.chainRunId(), generationEvidence, reviewEvidence);
    }

    private boolean isCancelled(String chainRunId) {
        return chainRunRepository.findById(chainRunId)
            .map(chainRun -> chainRun.status() == ChainRunStatus.CANCELLED)
            .orElse(true);
    }

    /**
     * 追加一个阶段运行并立即落库。返回 false 表示链路已被取消，执行应当停止。
     */
    private boolean appendStage(String chainRunId, StageRun stageRun) {
        ChainRun current = chainRunRepository.findById(chainRunId).orElse(null);
        if (current == null || current.status() == ChainRunStatus.CANCELLED) {
            return false;
        }
        List<StageRun> stageRuns = new ArrayList<>(current.stageRuns());
        stageRuns.add(stageRun);
        chainRunRepository.save(new ChainRun(current.chainRunId(), current.projectId(), current.chainType(),
            current.userGoal(), ChainRunStatus.EXECUTING, stageRun.stageCode(), List.copyOf(stageRuns),
            current.artifacts(), null, current.createdAt(), Instant.now()));
        return true;
    }

    private void finalizeChain(String chainRunId, StageBuildResult generationEvidence,
            StageBuildResult reviewEvidence) {
        ChainRun current = chainRunRepository.findById(chainRunId).orElse(null);
        if (current == null || current.status() == ChainRunStatus.CANCELLED) {
            return;
        }
        List<Artifact> artifacts = List.of();
        if (chainSucceeded(current.stageRuns())) {
            artifacts = buildArtifacts(current, generationEvidence, reviewEvidence);
            artifactRepository.saveAll(artifacts);
        }
        chainRunRepository.save(new ChainRun(current.chainRunId(), current.projectId(), current.chainType(),
            current.userGoal(), status(current.stageRuns()), current.currentStageCode(), current.stageRuns(),
            artifacts, blockingReason(current.stageRuns()), current.createdAt(), Instant.now()));
    }

    private void markFailed(String chainRunId, Exception exception) {
        ChainRun current = chainRunRepository.findById(chainRunId).orElse(null);
        if (current == null || current.status() == ChainRunStatus.CANCELLED) {
            return;
        }
        String reason = exception instanceof BusinessException businessException
            ? businessException.code() + ": " + businessException.getMessage()
            : "链路执行异常: " + exception.getMessage();
        chainRunRepository.save(new ChainRun(current.chainRunId(), current.projectId(), current.chainType(),
            current.userGoal(), ChainRunStatus.FAILED, current.currentStageCode(), current.stageRuns(),
            current.artifacts(), reason, current.createdAt(), Instant.now()));
    }

    private record StageBuildResult(
            StageRun stageRun,
            Map<String, Object> mergedOutput,
            List<String> candidateRefs,
            Map<String, Object> stageMetadata
    ) {
    }

    /**
     * 执行阶段，并在「非质量评审阶段因 LLM 输出变异导致评审不达标」时同阶段重试若干次
     * （如 I00 GoalAgent「scene must not be blank」）。质量评审阶段(I50/V50)有专门的重生成+
     * 换凭证逻辑，此处排除、只把结果原样返回给主循环处理。
     */
    private StageBuildResult buildStageWithReviewRetry(ChainExecutionCommand command, String namespace,
            List<StageCatalog.StageDefinition> stages, int index, Map<String, ApiCredential> credentials,
            Map<String, Map<String, Object>> stageOutputs, StageBuildResult generationEvidence) {
        StageBuildResult result = buildStage(command, namespace, stages, index, credentials, stageOutputs,
            generationEvidence);
        for (int attempt = 0; attempt < MAX_QUALITY_RETRIES && isRetryableStageFailure(result.stageRun()); attempt++) {
            LOGGER.info("阶段 {} 评审不达标(LLM变异)，第 {} 次同阶段重试", result.stageRun().stageCode(), attempt + 1);
            result = buildStage(command, namespace, stages, index, credentials, stageOutputs, generationEvidence);
        }
        return result;
    }

    /**
     * 可重试的阶段失败：该阶段有评审报告且未通过、且不是质量评审阶段（那个走专门逻辑）。
     * 这类失败多为 LLM 结构化输出偶发缺字段，重跑常能过；能力/配置类失败不在此列（不会白重试）。
     */
    private boolean isRetryableStageFailure(StageRun stageRun) {
        return stageRun.status() == StageRunStatus.FAILED
            && stageRun.reviewReport() != null
            && !stageRun.reviewReport().passed()
            && !isQualityReviewStage(stageRun.stageCode());
    }

    private StageBuildResult buildStage(ChainExecutionCommand command, String namespace,
            List<StageCatalog.StageDefinition> stages, int index, Map<String, ApiCredential> credentials,
            Map<String, Map<String, Object>> stageOutputs, StageBuildResult generationEvidence) {
        StageCatalog.StageDefinition stage = stages.get(index);
        String toStageCode = nextStageCode(stages, index);
        String chainRunId = command.chainRunId();
        ChainType chainType = command.chainType();
        String userGoal = command.userGoal();
        Instant now = Instant.now();
        String stageRunId = "stage-" + UUID.randomUUID();
        String sourceId = chainRunId + "-" + stage.stageCode();

        writeRequiredRagContext(chainRunId, chainType, userGoal, namespace, stage, sourceId);
        RetrievalResult retrieval;
        try {
            retrieval = knowledgeApplicationService.retrieve(namespace, chainType, stage.stageCode(), userGoal, 5);
        } catch (BusinessException exception) {
            if (RAG_EVIDENCE_CONFLICT_CODE.equals(exception.code())) {
                return new StageBuildResult(waitingReviewStage(stageRunId, chainRunId, stage,
                    exception.getMessage(), now), Map.of(), List.of(), Map.of());
            }
            throw exception;
        }
        ApiCredential credential = credentials.get(stage.capabilityType());
        EvidencePack evidencePack = EvidencePack.from(retrieval.retrievalRecordId(), retrieval.namespace(),
            chainType, stage.stageCode(), userGoal, retrieval.coverage(), retrieval.chunks());
        Map<String, Object> stageInputContext = stageInputContext(command.projectId(), chainRunId, chainType,
            stageRunId, stage, retrieval);
        List<AgentNodeResult> nodeResults = executeStageAgents(command, namespace, stage, stageRunId, retrieval,
            credential, evidencePack, stageInputContext, stageOutputs, generationEvidence);
        AgentNodeResult failedNodeResult = firstFailedNode(nodeResults);
        if (failedNodeResult != null) {
            return new StageBuildResult(failedCapabilityStage(stageRunId, chainRunId, stage, retrieval,
                nodeResults, failedNodeResult, now), Map.of(), List.of(), Map.of());
        }
        StageCoordinationResult coordinationResult;
        try {
            coordinationResult = stageCoordinator.coordinate(stage, partialOutputs(nodeResults));
        } catch (IllegalArgumentException exception) {
            ReviewReport reviewReport = new ReviewReport(false, 0, stage.rubricVersion(), exception.getMessage());
            return new StageBuildResult(failedReviewStage(stageRunId, chainRunId, stage, retrieval, nodeResults,
                reviewReport, now), Map.of(), List.of(), Map.of());
        }
        Map<String, Object> stageMetadata = withDeterministicIntegrity(stage.stageCode(),
            mergedProviderMetadata(nodeResults, coordinationResult), generationEvidence);
        ReviewReport reviewReport = StageReviewPolicy.review(chainType, stage.stageCode(), stage.stageName(),
            stageMetadata);
        if (!reviewReport.passed()) {
            return new StageBuildResult(failedReviewStage(stageRunId, chainRunId, stage, retrieval, nodeResults,
                reviewReport, now), Map.of(), List.of(), Map.of());
        }
        recordStageOutput(namespace, chainType, stage, stageRunId, stageOutputs, coordinationResult);
        String handoffContextId = writeHandoffContext(chainRunId, chainType, namespace, stageRunId,
            stage.stageCode(), toStageCode, reviewReport, retrieval, coordinationResult);

        StageRun stageRun = new StageRun(
            stageRunId,
            chainRunId,
            stage.stageCode(),
            stage.stageName(),
            StageRunStatus.SUCCEEDED,
            reviewReport,
            retrieval.retrievalRecordId(),
            handoffContextId,
            nodeRunIds(nodeResults),
            freeModelGateIds(nodeResults),
            providerJobIds(nodeResults),
            now,
            Instant.now()
        );
        return new StageBuildResult(stageRun, coordinationResult.mergedOutput(), candidateRefs(nodeResults),
            stageMetadata);
    }

    /**
     * 质量评审阶段（I50/V50）的完整性分是确定性属性（图片能否解码、视频能否解码），
     * 按技术文档 14.4 用生成阶段真实校验出的证据，而不是让看不到产物的文本 LLM 猜测。
     * 把这些确定性分放进 stageMergedOutput（StageReviewPolicy 最先读取的位置）以覆盖 LLM 值。
     */
    private Map<String, Object> withDeterministicIntegrity(String stageCode, Map<String, Object> stageMetadata,
            StageBuildResult generationEvidence) {
        if (!isQualityReviewStage(stageCode) || generationEvidence == null) {
            return stageMetadata;
        }
        Map<String, Object> generationMetadata = generationEvidence.stageMetadata();
        Map<String, Object> deterministic = new LinkedHashMap<>();
        if (stageCode.startsWith("I") && generationMetadata.get("artifactIntegrityScore") instanceof Number score) {
            deterministic.put("artifactIntegrityScore", score.intValue());
        }
        if (stageCode.startsWith("V") && generationMetadata.get("decodeIntegrityScore") instanceof Number score) {
            deterministic.put("decodeIntegrityScore", score.intValue());
        }
        if (deterministic.isEmpty()) {
            return stageMetadata;
        }
        Map<String, Object> merged = new LinkedHashMap<>(stageMetadata);
        Object existing = merged.get("stageMergedOutput");
        Map<String, Object> stageMergedOutput = new LinkedHashMap<>();
        if (existing instanceof Map<?, ?> existingMap) {
            existingMap.forEach((key, value) -> stageMergedOutput.put(String.valueOf(key), value));
        }
        stageMergedOutput.putAll(deterministic);
        merged.put("stageMergedOutput", Map.copyOf(stageMergedOutput));
        return Map.copyOf(merged);
    }

    private void recordStageOutput(String namespace, ChainType chainType, StageCatalog.StageDefinition stage,
            String stageRunId, Map<String, Map<String, Object>> stageOutputs,
            StageCoordinationResult coordinationResult) {
        Map<String, Object> mergedOutput = coordinationResult.mergedOutput();
        if (mergedOutput == null || mergedOutput.isEmpty()) {
            return;
        }
        stageOutputs.put(stage.stageCode(), mergedOutput);
        knowledgeApplicationService.ingest(namespace, chainType, stage.stageCode(), STAGE_OUTPUT_SOURCE_TYPE,
            stageRunId, serialize(mergedOutput));
    }

    private List<String> candidateRefs(List<AgentNodeResult> nodeResults) {
        return nodeResults.stream()
            .flatMap(nodeResult -> nodeResult.artifactRefs().stream())
            .distinct()
            .toList();
    }

    private List<AgentNodeResult> executeStageAgents(ChainExecutionCommand command, String namespace,
            StageCatalog.StageDefinition stage, String stageRunId, RetrievalResult retrieval,
            ApiCredential credential, EvidencePack evidencePack, Map<String, Object> stageInputContext,
            Map<String, Map<String, Object>> stageOutputs, StageBuildResult generationEvidence) {
        List<AgentNodeResult> nodeResults = new ArrayList<>();
        for (String agentName : stage.agentNames()) {
            AgentDefinition definition = new AgentDefinition(agentName, stage.collaborationMode(),
                stage.capabilityType());
            Map<String, Object> payload = agentPayload(command, namespace, stage, agentName, credential,
                evidencePack, stageInputContext, stageOutputs, generationEvidence);
            AgentNodeResult nodeResult = agentNodeFactory
                .create(definition, command.chainRunId(), stageRunId, "node-" + UUID.randomUUID())
                .execute(new AgentNodeInput(stage.stageCode(), retrieval.retrievalRecordId(), credential,
                    payload));
            nodeResults.add(nodeResult);
            if (nodeResult.nodeRun().status() != AgentNodeRunStatus.SUCCEEDED) {
                break;
            }
        }
        return List.copyOf(nodeResults);
    }

    private Map<String, Object> agentPayload(ChainExecutionCommand command, String namespace,
            StageCatalog.StageDefinition stage, String agentName, ApiCredential credential,
            EvidencePack evidencePack, Map<String, Object> stageInputContext,
            Map<String, Map<String, Object>> stageOutputs, StageBuildResult generationEvidence) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userGoal", command.userGoal());
        payload.put("stageName", stage.stageName());
        payload.put("agentName", agentName);
        payload.put("agentNames", stage.agentNames());
        payload.put("stageInputContext", stageInputContext);
        payload.put("inputSchemaId", stage.inputSchemaId());
        payload.put("outputSchemaId", stage.outputSchemaId());
        payload.put("rubricVersion", stage.rubricVersion());
        payload.put("retrievalPolicyId", stage.retrievalPolicyId());
        payload.put("evidencePack", evidencePack.toPromptInput());
        StagePartialSchema partialSchema = partialSchema(stage, agentName);
        if (partialSchema != null) {
            payload.put("partialSchema", Map.of(
                "required", partialSchema.requiredFieldNames(),
                "allowed", partialSchema.allowedFieldNames()
            ));
        } else {
            Map<String, Object> reviewSchema = reviewPartialSchema(stage.stageCode(), agentName);
            if (reviewSchema != null) {
                payload.put("partialSchema", reviewSchema);
            }
        }
        Map<String, Object> previousStageOutput = latestStageOutput(stageOutputs);
        if (!previousStageOutput.isEmpty()) {
            payload.put("previousStageOutput", previousStageOutput);
        }
        if (isGenerationStage(stage.stageCode())) {
            payload.put("prompt", generationPrompt(command, namespace, stageOutputs));
        }
        if (isQualityReviewStage(stage.stageCode()) && generationEvidence != null) {
            payload.put("candidateEvidence", Map.of(
                "candidateRefs", generationEvidence.candidateRefs(),
                "generationMetadata", generationEvidence.stageMetadata()
            ));
        }
        if (isPreflightStage(stage.stageCode())) {
            Map<String, Object> deterministicOutput = deterministicPreflightOutput(command.chainType(),
                agentName, credential);
            payload.put(DefaultAgentNodeFactory.DETERMINISTIC_PARTIAL_OUTPUT_KEY, deterministicOutput);
            payload.put(DefaultAgentNodeFactory.DETERMINISTIC_METADATA_KEY,
                deterministicPreflightMetadata(command.chainType(), credential));
        }
        return Map.copyOf(payload);
    }

    private StagePartialSchema partialSchema(StageCatalog.StageDefinition stage, String agentName) {
        return stage.partialSchemas().stream()
            .filter(schema -> schema.agentName().equals(agentName))
            .findFirst()
            .orElse(null);
    }

    /**
     * I50/V50 评审阶段各评审 agent 的结构化输出 schema。评审阶段是 CANDIDATE_SELECTION，
     * 不进 StageCoordinator 的分工合并校验，但真实 LLM 仍需按此 schema 产出分项分数，
     * 由 {@link #mergeProviderMetadataValue} 聚合后交给 StageReviewPolicy 计算总分。
     */
    private Map<String, Object> reviewPartialSchema(String stageCode, String agentName) {
        // 注意：artifactIntegrityScore / decodeIntegrityScore 不在 LLM schema 里——它们是确定性
        // 校验结果（文档 14.4），由 withDeterministicIntegrity 从生成证据注入，不让 LLM 判定。
        List<String> fields = switch (stageCode + ":" + agentName) {
            case "I50:VisualQualityAgent" -> List.of("visualQualityScore");
            case "I50:GoalMatchAgent" -> List.of("goalMatchScore", "promptConsistencyScore");
            case "I50:SafetyReviewAgent", "V50:SafetyReviewAgent" -> List.of("safetyScore");
            case "V50:DecodeReviewAgent" -> List.of("decodeNotes");
            case "V50:MotionQualityAgent" -> List.of("motionQualityScore");
            case "V50:GoalMatchAgent" -> List.of("goalMatchScore", "promptConsistencyScore", "shortDramaScore",
                "continuityScore");
            case "V50:VoiceReviewAgent" -> List.of("voiceoverQualityScore", "humanVoiceAudible");
            default -> List.of();
        };
        if (fields.isEmpty()) {
            return null;
        }
        return Map.of("required", fields, "allowed", fields);
    }

    private Map<String, Object> latestStageOutput(Map<String, Map<String, Object>> stageOutputs) {
        Map<String, Object> latest = Map.of();
        for (Map<String, Object> output : stageOutputs.values()) {
            latest = output;
        }
        return latest;
    }

    /**
     * 能力预检阶段的结论来自后端已知事实（用户选中的凭证、验证状态），
     * 由确定性逻辑给出，不调用大模型。
     */
    private Map<String, Object> deterministicPreflightOutput(ChainType chainType, String agentName,
            ApiCredential credential) {
        boolean gatePassed = credential != null
            && credential.freeModelGateStatus() == FreeModelGateStatus.PASSED;
        if ("CapabilityAgent".equals(agentName)) {
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("providerId", credential == null ? "" : credential.provider());
            output.put("apiKeyId", credential == null ? "" : credential.apiKeyId());
            output.put("model", credential == null ? "" : credential.model());
            output.put("freeModelGatePassed", gatePassed);
            if (chainType == ChainType.VIDEO) {
                output.put("durationSeconds", 10);
                output.put("aspectRatio", "9:16");
                output.put("nativeHumanVoiceSupported", gatePassed);
            }
            return Map.copyOf(output);
        }
        return Map.of(
            "selectedProviderId", credential == null ? "" : credential.provider(),
            "reason", "用户当前选中的能力凭证，已通过免费模型门禁验证",
            "retryPolicy", "FREE_PROVIDER_RETRY_ONLY"
        );
    }

    private Map<String, Object> deterministicPreflightMetadata(ChainType chainType, ApiCredential credential) {
        if (chainType != ChainType.VIDEO) {
            return Map.of();
        }
        boolean gatePassed = credential != null
            && credential.freeModelGateStatus() == FreeModelGateStatus.PASSED;
        return Map.of(
            "completeShortVideoSupported", gatePassed,
            "nativeHumanVoiceSupported", gatePassed,
            "durationSeconds", 10,
            "aspectRatio", "9:16"
        );
    }

    /**
     * 生成阶段的 prompt 来自 I20/V20 阶段产出的提示词包，而不是原始用户输入。
     * 同一次执行内直接用内存中的合并输出；redo 场景从 RAG 的 STAGE_OUTPUT 恢复。
     */
    private String generationPrompt(ChainExecutionCommand command, String namespace,
            Map<String, Map<String, Object>> stageOutputs) {
        String promptStageCode = command.chainType() == ChainType.IMAGE ? "I20" : "V20";
        Map<String, Object> promptPack = stageOutputs.get(promptStageCode);
        if (promptPack == null || promptPack.isEmpty()) {
            promptPack = loadStageOutput(namespace, command.chainType(), promptStageCode);
        }
        String positivePrompt = textValue(promptPack, "positivePrompt");
        if (positivePrompt.isBlank()) {
            return command.userGoal();
        }
        StringBuilder prompt = new StringBuilder(positivePrompt);
        if (command.chainType() == ChainType.IMAGE) {
            String negativePrompt = textValue(promptPack, "negativePrompt");
            if (!negativePrompt.isBlank()) {
                prompt.append("。画面中不能出现: ").append(negativePrompt);
            }
        } else {
            appendPromptSection(prompt, "镜头与动作", textValue(promptPack, "motionPrompt"));
            appendPromptSection(prompt, "连续性要求", textValue(promptPack, "continuityConstraints"));
            appendPromptSection(prompt, "人物一致性", textValue(promptPack, "characterContinuity"));
            appendPromptSection(prompt, "视觉风格", textValue(promptPack, "visualStyleConstraint"));
        }
        return prompt.toString();
    }

    private void appendPromptSection(StringBuilder prompt, String label, String value) {
        if (!value.isBlank()) {
            prompt.append("。").append(label).append(": ").append(value);
        }
    }

    private String textValue(Map<String, Object> fields, String key) {
        if (fields == null) {
            return "";
        }
        Object value = fields.get(key);
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).strip();
        return "null".equals(text) ? "" : text;
    }

    private Map<String, Object> loadStageOutput(String namespace, ChainType chainType, String stageCode) {
        return knowledgeApplicationService.findLatestBySourceType(namespace, chainType, stageCode,
                STAGE_OUTPUT_SOURCE_TYPE)
            .map(chunk -> parseJsonMap(chunk.content()))
            .orElse(Map.of());
    }

    private Map<String, Object> parseJsonMap(String json) {
        try {
            return objectMapper.readValue(json,
                objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    private boolean isGenerationStage(String stageCode) {
        return stageCode.endsWith("40");
    }

    private boolean isQualityReviewStage(String stageCode) {
        return stageCode.endsWith("50");
    }

    private boolean isPreflightStage(String stageCode) {
        return stageCode.endsWith("30");
    }

    private AgentNodeResult firstFailedNode(List<AgentNodeResult> nodeResults) {
        return nodeResults.stream()
            .filter(nodeResult -> nodeResult.nodeRun().status() != AgentNodeRunStatus.SUCCEEDED)
            .findFirst()
            .orElse(null);
    }

    private Map<String, Object> stageInputContext(String projectId, String chainRunId, ChainType chainType,
            String stageRunId, StageCatalog.StageDefinition stage, RetrievalResult retrieval) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("contextVersion", "v1");
        context.put("projectId", projectId);
        context.put("chainType", chainType.name());
        context.put("chainRunId", chainRunId);
        context.put("stageRunId", stageRunId);
        context.put("currentStage", stage.stageCode());
        context.put("goalRef", firstChunkId(retrieval, USER_GOAL_SOURCE_TYPE));
        context.put("previousHandoffRef", firstChunkId(retrieval, NEXT_STAGE_CONTEXT_SOURCE_TYPE));
        context.put("previousReviewRef", firstChunkId(retrieval, REVIEW_REPORT_SOURCE_TYPE));
        context.put("retrievalPolicyRef", contextRef("rag", chainType, stage.stageCode()));
        context.put("stageDefinitionRef", contextRef("stage", chainType, stage.stageCode()));
        context.put("retrievalRecordId", retrieval.retrievalRecordId());
        return Collections.unmodifiableMap(context);
    }

    private String contextRef(String prefix, ChainType chainType, String stageCode) {
        return prefix + "/" + chainType.name().toLowerCase(Locale.ROOT) + "/"
            + stageCode.toLowerCase(Locale.ROOT) + ".v1.json";
    }

    private String firstChunkId(RetrievalResult retrieval, String sourceType) {
        return retrieval.chunks().stream()
            .filter(chunk -> sourceType.equals(chunk.sourceType()))
            .map(KnowledgeChunk::chunkId)
            .findFirst()
            .orElse(null);
    }

    private void writeRequiredRagContext(String chainRunId, ChainType chainType, String userGoal, String namespace,
            StageCatalog.StageDefinition stage, String sourceId) {
        knowledgeApplicationService.ingest(namespace, chainType, stage.stageCode(), USER_GOAL_SOURCE_TYPE,
            chainRunId + "-goal-" + stage.stageCode(), "userGoal=" + userGoal);
        knowledgeApplicationService.ingest(namespace, chainType, stage.stageCode(), STAGE_MAP_SOURCE_TYPE,
            chainRunId + "-stage-map-" + stage.stageCode(), stageMapContent(chainType));
        String content = "chainType=" + chainType + "; stageCode=" + stage.stageCode() + "; stageName="
            + stage.stageName() + "; userGoal=" + userGoal;
        knowledgeApplicationService.ingest(namespace, chainType, stage.stageCode(), CHAIN_CONTEXT_SOURCE_TYPE,
            sourceId, content);
    }

    private String stageMapContent(ChainType chainType) {
        return StageCatalog.stages(chainType).stream()
            .map(stage -> stage.stageCode() + ":" + stage.stageName())
            .collect(Collectors.joining(" -> "));
    }

    private StageRun failedReviewStage(String stageRunId, String chainRunId, StageCatalog.StageDefinition stage,
            RetrievalResult retrieval, List<AgentNodeResult> nodeResults, ReviewReport reviewReport, Instant now) {
        return new StageRun(
            stageRunId,
            chainRunId,
            stage.stageCode(),
            stage.stageName(),
            StageRunStatus.FAILED,
            reviewReport,
            retrieval.retrievalRecordId(),
            null,
            nodeRunIds(nodeResults),
            freeModelGateIds(nodeResults),
            providerJobIds(nodeResults),
            now,
            now
        );
    }

    private StageRun recoveryPreflightBlockStage(ChainExecutionCommand command, String namespace,
            StageCatalog.StageDefinition stage, StageRun failedReviewStage) {
        Instant now = Instant.now();
        String stageRunId = "stage-" + UUID.randomUUID();
        String sourceId = command.chainRunId() + "-" + stage.stageCode() + "-recovery";
        String content = "chainType=" + command.chainType() + "; stageCode=" + stage.stageCode()
            + "; recoveryFrom=" + failedReviewStage.stageCode() + "; userGoal=" + command.userGoal();
        knowledgeApplicationService.ingest(namespace, command.chainType(), stage.stageCode(),
            CHAIN_CONTEXT_SOURCE_TYPE, sourceId, content);
        RetrievalResult retrieval = knowledgeApplicationService.retrieve(namespace, command.chainType(),
            stage.stageCode(), command.userGoal(), 5);
        StageRunStatus recoveryStatus = recoveryStageStatus(command.chainType());
        String summary = recoverySummary(command.chainType(), stage.stageCode(), failedReviewStage);
        ReviewReport reviewReport = new ReviewReport(false, 0, "recovery-policy.v1", summary);
        return new StageRun(stageRunId, command.chainRunId(), stage.stageCode(), stage.stageName(),
            recoveryStatus, reviewReport, retrieval.retrievalRecordId(), null, List.of(), List.of(), List.of(),
            now, now);
    }

    private StageRun failedCapabilityStage(String stageRunId, String chainRunId,
            StageCatalog.StageDefinition stage, RetrievalResult retrieval, List<AgentNodeResult> nodeResults,
            AgentNodeResult failedNodeResult, Instant now) {
        return new StageRun(
            stageRunId,
            chainRunId,
            stage.stageCode(),
            stage.stageName(),
            StageRunStatus.WAITING_CAPABILITY,
            new ReviewReport(false, 0, "provider-capability-gate.v1",
                failedNodeResult.nodeRun().outputSummary()),
            retrieval.retrievalRecordId(),
            null,
            nodeRunIds(nodeResults),
            freeModelGateIds(nodeResults),
            providerJobIds(nodeResults),
            now,
            now
        );
    }

    private StageRun waitingReviewStage(String stageRunId, String chainRunId, StageCatalog.StageDefinition stage,
            String summary, Instant now) {
        return new StageRun(
            stageRunId,
            chainRunId,
            stage.stageCode(),
            stage.stageName(),
            StageRunStatus.WAITING_REVIEW,
            new ReviewReport(false, 0, "rag-evidence-review.v1", summary),
            null,
            null,
            List.of(),
            List.of(),
            List.of(),
            now,
            now
        );
    }

    private Map<String, Object> mergedProviderMetadata(List<AgentNodeResult> nodeResults) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        for (AgentNodeResult nodeResult : nodeResults) {
            nodeResult.providerMetadata().forEach((key, value) ->
                metadata.merge(key, value, this::mergeProviderMetadataValue));
        }
        return Map.copyOf(metadata);
    }

    /**
     * 合并多个 agent 的 provider 元数据。数值取最小、布尔取与（诚实门禁）；
     * partialOutput 子 map 做字段并集，让 CANDIDATE_SELECTION 评审阶段的多个评审 agent
     * 各自产出的分项分数能聚合到同一份评审输入里。
     */
    @SuppressWarnings("unchecked")
    private Object mergeProviderMetadataValue(Object currentValue, Object nextValue) {
        if (currentValue instanceof Map<?, ?> currentMap && nextValue instanceof Map<?, ?> nextMap) {
            Map<String, Object> merged = new LinkedHashMap<>((Map<String, Object>) currentMap);
            ((Map<String, Object>) nextMap).forEach(merged::putIfAbsent);
            return Map.copyOf(merged);
        }
        if (currentValue instanceof Number currentNumber && nextValue instanceof Number nextNumber) {
            return Math.min(currentNumber.intValue(), nextNumber.intValue());
        }
        if (currentValue instanceof Boolean currentBoolean && nextValue instanceof Boolean nextBoolean) {
            return currentBoolean && nextBoolean;
        }
        return currentValue;
    }

    private Map<String, Object> mergedProviderMetadata(List<AgentNodeResult> nodeResults,
            StageCoordinationResult coordinationResult) {
        Map<String, Object> metadata = new LinkedHashMap<>(mergedProviderMetadata(nodeResults));
        metadata.put("providerJobIds", providerJobIds(nodeResults));
        metadata.put("stageOutputSchemaId", coordinationResult.outputSchemaId());
        metadata.put("stageMergedOutput", coordinationResult.mergedOutput());
        metadata.put("stageConflictResolutions", coordinationResult.conflictResolutions());
        return Map.copyOf(metadata);
    }

    private List<StagePartialOutput> partialOutputs(List<AgentNodeResult> nodeResults) {
        return nodeResults.stream()
            .map(nodeResult -> new StagePartialOutput(nodeResult.nodeRun().nodeName(), partialFields(nodeResult)))
            .toList();
    }

    private Map<String, Object> partialFields(AgentNodeResult nodeResult) {
        Object value = nodeResult.providerMetadata().get("partialOutput");
        if (value instanceof Map<?, ?> rawFields && !rawFields.isEmpty()) {
            Map<String, Object> fields = new LinkedHashMap<>();
            rawFields.forEach((fieldName, fieldValue) -> fields.put(String.valueOf(fieldName), fieldValue));
            return Map.copyOf(fields);
        }
        String summary = nodeResult.nodeRun().outputSummary() == null
            ? nodeResult.nodeRun().status().name() : nodeResult.nodeRun().outputSummary();
        return Map.of(nodeResult.nodeRun().nodeName() + "Summary", summary);
    }

    private List<String> nodeRunIds(List<AgentNodeResult> nodeResults) {
        return nodeResults.stream()
            .map(nodeResult -> nodeResult.nodeRun().nodeRunId())
            .toList();
    }

    private List<String> freeModelGateIds(List<AgentNodeResult> nodeResults) {
        return nodeResults.stream()
            .map(nodeResult -> nodeResult.nodeRun().freeModelGate().freeModelGateId())
            .toList();
    }

    private List<String> providerJobIds(List<AgentNodeResult> nodeResults) {
        return nodeResults.stream()
            .map(nodeResult -> nodeResult.nodeRun().providerJobId())
            .toList();
    }

    ChainRunStatus status(List<StageRun> stageRuns) {
        if (chainSucceeded(stageRuns)) {
            return ChainRunStatus.SUCCEEDED;
        }
        if (!stageRuns.isEmpty()
                && stageRuns.get(stageRuns.size() - 1).status() == StageRunStatus.WAITING_REVIEW) {
            return ChainRunStatus.WAITING_REVIEW;
        }
        if (!stageRuns.isEmpty()
                && stageRuns.get(stageRuns.size() - 1).status() == StageRunStatus.WAITING_USER) {
            return ChainRunStatus.WAITING_USER;
        }
        if (!stageRuns.isEmpty()
                && stageRuns.get(stageRuns.size() - 1).status() == StageRunStatus.FAILED) {
            return ChainRunStatus.FAILED;
        }
        return ChainRunStatus.WAITING_CAPABILITY;
    }

    private StageRunStatus recoveryStageStatus(ChainType chainType) {
        return chainType == ChainType.VIDEO ? StageRunStatus.WAITING_USER : StageRunStatus.WAITING_CAPABILITY;
    }

    private String recoverySummary(ChainType chainType, String stageCode, StageRun failedReviewStage) {
        if (chainType == ChainType.VIDEO) {
            return "连续两次质量评审失败，返回 " + stageCode
                + " 重新选择 provider；没有其他完整视频 provider，等待用户处理: "
                + failedReviewStage.reviewReport().summary();
        }
        return "连续两次质量评审失败，返回 " + stageCode + " 重新选择 provider: "
            + failedReviewStage.reviewReport().summary();
    }

    boolean chainSucceeded(List<StageRun> stageRuns) {
        if (stageRuns.isEmpty()) {
            return false;
        }
        StageRun lastStageRun = stageRuns.get(stageRuns.size() - 1);
        return lastStageRun.status() == StageRunStatus.SUCCEEDED
            && lastStageRun.stageCode().endsWith("60");
    }

    private String blockingReason(List<StageRun> stageRuns) {
        if (chainSucceeded(stageRuns)) {
            return null;
        }
        for (int index = stageRuns.size() - 1; index >= 0; index--) {
            StageRun stageRun = stageRuns.get(index);
            if (stageRun.status() != StageRunStatus.SUCCEEDED) {
                return stageRun.reviewReport().summary();
            }
        }
        return null;
    }

    private boolean isQualityReviewFailure(StageRun stageRun) {
        return stageRun.status() == StageRunStatus.FAILED
            && stageRun.stageCode().endsWith("50")
            && !stageRun.reviewReport().passed();
    }

    private String nextStageCode(List<StageCatalog.StageDefinition> stages, int index) {
        if (index + 1 >= stages.size()) {
            return FINAL_HANDOFF_STAGE;
        }
        return stages.get(index + 1).stageCode();
    }

    private String writeHandoffContext(String chainRunId, ChainType chainType, String namespace, String stageRunId,
            String fromStageCode, String toStageCode, ReviewReport reviewReport, RetrievalResult retrieval,
            StageCoordinationResult coordinationResult) {
        List<String> evidenceChunkIds = evidenceChunkIds(retrieval);
        List<EvidenceClaim> claims = evidenceClaims(fromStageCode, toStageCode, reviewReport, evidenceChunkIds);
        EvidenceCheckReport evidenceCheck = EvidenceCheckPolicy.check(claims);
        assertEvidenceCheckPassed(evidenceCheck);
        NextStageContext context = new NextStageContext(CONTEXT_SCHEMA_VERSION, chainRunId, chainType,
            fromStageCode, toStageCode, List.of(), stageRunId + "-review", evidenceChunkIds, claims, evidenceCheck,
            coordinationResult, List.of());
        String handoffStageCode = FINAL_HANDOFF_STAGE.equals(toStageCode) ? fromStageCode : toStageCode;
        KnowledgeChunk handoffChunk = knowledgeApplicationService.ingest(namespace, chainType, handoffStageCode,
            NEXT_STAGE_CONTEXT_SOURCE_TYPE, stageRunId, serialize(context));
        knowledgeApplicationService.ingest(namespace, chainType, handoffStageCode, REVIEW_REPORT_SOURCE_TYPE,
            stageRunId, serialize(reviewReport));
        return handoffChunk.chunkId();
    }

    private List<EvidenceClaim> evidenceClaims(String fromStageCode, String toStageCode, ReviewReport reviewReport,
            List<String> evidenceChunkIds) {
        boolean hasCitation = !evidenceChunkIds.isEmpty();
        return List.of(
            new EvidenceClaim(fromStageCode + " to " + toStageCode + " handoff is backed by retrieved RAG chunks",
                true, evidenceChunkIds, List.of("KnowledgeApplicationService.retrieve"), hasCitation),
            new EvidenceClaim("reviewReport " + reviewReport.rubricVersion()
                + " passed=" + reviewReport.passed() + " score=" + reviewReport.overallScore(), true,
                evidenceChunkIds, List.of("StageReviewPolicy.review"), hasCitation)
        );
    }

    private void assertEvidenceCheckPassed(EvidenceCheckReport evidenceCheck) {
        if (!evidenceCheck.passed()) {
            throw new BusinessException(org.springframework.http.HttpStatus.CONFLICT, "EVIDENCE_CHECK_FAILED",
                "结构化输出证据检查失败: unsupportedCriticalClaims="
                    + evidenceCheck.unsupportedCriticalClaims());
        }
    }

    private List<String> evidenceChunkIds(RetrievalResult retrieval) {
        return retrieval.chunks().stream()
            .map(KnowledgeChunk::chunkId)
            .toList();
    }

    private String serialize(Object context) {
        try {
            return objectMapper.writeValueAsString(context);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Context serialization failed", exception);
        }
    }

    String generationCapabilityType(ChainType chainType) {
        return chainType == ChainType.IMAGE ? "image.generate.free" : "video.generate.full_with_voice.free";
    }

    private Map<String, ApiCredential> alternativeGenerationCredentials(ChainType chainType,
            Map<String, ApiCredential> selectedByCapability, Set<String> exhaustedGenerationApiKeyIds) {
        String generationCapabilityType = generationCapabilityType(chainType);
        ApiCredential currentGenerationCredential = selectedByCapability.get(generationCapabilityType);
        return apiConfigRepository.findCredentials(chainType, generationCapabilityType).stream()
            .filter(this::availableFreeCredential)
            .filter(credential -> !exhaustedGenerationApiKeyIds.contains(credential.apiKeyId()))
            .filter(credential -> hasDifferentProvider(credential, currentGenerationCredential))
            .findFirst()
            .map(credential -> replaceGenerationCredential(selectedByCapability, generationCapabilityType,
                credential))
            .orElseGet(Map::of);
    }

    private boolean availableFreeCredential(ApiCredential credential) {
        return (credential.status() == ApiKeyStatus.ACTIVE || credential.status() == ApiKeyStatus.AVAILABLE)
            && credential.freeModelGateStatus() == FreeModelGateStatus.PASSED
            && credential.lastVerifiedAt() != null;
    }

    private boolean hasDifferentProvider(ApiCredential candidate, ApiCredential current) {
        return current == null || !candidate.provider().equals(current.provider());
    }

    private Map<String, ApiCredential> replaceGenerationCredential(
            Map<String, ApiCredential> selectedByCapability, String generationCapabilityType,
            ApiCredential generationCredential) {
        Map<String, ApiCredential> nextCredentials = new LinkedHashMap<>(selectedByCapability);
        nextCredentials.put(generationCapabilityType, generationCredential);
        return Map.copyOf(nextCredentials);
    }

    /**
     * 快照中的 FreeModelGate 证据来源于凭证真实验证状态，不再无条件写 passed=true。
     */
    public void saveSnapshot(String chainRunId, ApiCredential credential) {
        boolean passed = credential.freeModelGateStatus() == FreeModelGateStatus.PASSED;
        String quotaSnapshot = credential.lastVerifiedAt() == null
            ? "unverified"
            : "verified-free-key:lastVerifiedAt=" + credential.lastVerifiedAt();
        FreeModelGate freeModelGate = new FreeModelGate("free-gate-" + UUID.randomUUID(), passed,
            credential.provider(), credential.model(), credential.capabilityType(), "free", false,
            quotaSnapshot, Instant.now());
        ApiSelectionSnapshot snapshot = new ApiSelectionSnapshot("snapshot-" + UUID.randomUUID(), chainRunId,
            credential.chainType(), credential.capabilityType(), credential.provider(), credential.apiKeyId(),
            credential.maskedKey(), credential.model(), freeModelGate, Instant.now());
        apiConfigRepository.saveSnapshot(snapshot);
    }

    private String chainNamespace(String projectId, String chainRunId) {
        return "project:" + projectId + ":chain:" + chainRunId;
    }

    private List<Artifact> buildArtifacts(ChainRun chainRun, StageBuildResult generationEvidence,
            StageBuildResult reviewEvidence) {
        List<String> candidateRefs = generationEvidence == null ? List.of() : generationEvidence.candidateRefs();
        Map<String, Object> generationMetadata = generationEvidence == null ? Map.of()
            : generationEvidence.stageMetadata();
        Map<String, Object> reviewOutput = reviewEvidence == null ? Map.of() : reviewEvidence.mergedOutput();
        ReviewReport reviewReport = reviewEvidence == null ? null : reviewEvidence.stageRun().reviewReport();
        String finalUrl = candidateRefs.isEmpty() ? NO_CANDIDATE_REF : candidateRefs.get(0);
        int candidateCount = candidateCount(generationMetadata, candidateRefs);
        String aspectRatio = textValue(generationMetadata, "aspectRatio");

        if (chainRun.chainType() == ChainType.IMAGE) {
            Map<String, Object> finalMetadata = new LinkedHashMap<>();
            finalMetadata.put("goalMatchScore", scoreValue(reviewOutput, reviewReport, "goalMatchScore"));
            finalMetadata.put("aspectRatio", aspectRatio);
            finalMetadata.put("candidateCount", candidateCount);
            Map<String, Object> candidatesMetadata = new LinkedHashMap<>();
            candidatesMetadata.put("candidateCount", candidateCount);
            candidatesMetadata.put("aspectRatio", aspectRatio);
            candidatesMetadata.put("candidateRefs", candidateRefs);
            Map<String, Object> reportMetadata = reviewReportMetadata(reviewReport, reviewOutput,
                chainRun.userGoal());
            return List.of(
                artifact(chainRun, ArtifactKind.FinalImageArtifact, "最终图片", finalUrl, finalMetadata),
                artifact(chainRun, ArtifactKind.ImageCandidateAssets, "图片候选资产", finalUrl,
                    candidatesMetadata),
                artifact(chainRun, ArtifactKind.ImageReviewReport, "图片验收报告",
                    reportUrl(chainRun, reviewEvidence), reportMetadata)
            );
        }

        Map<String, Object> finalMetadata = new LinkedHashMap<>();
        finalMetadata.put("durationSeconds", intMetadataValue(generationMetadata, "durationSeconds", 10));
        finalMetadata.put("aspectRatio", aspectRatio.isBlank() ? "9:16" : aspectRatio);
        finalMetadata.put("hasHumanVoice", booleanMetadataValue(generationMetadata, "hasHumanVoice"));
        finalMetadata.put("clipStitching", false);
        Map<String, Object> candidatesMetadata = new LinkedHashMap<>();
        candidatesMetadata.put("candidateCount", candidateCount);
        candidatesMetadata.put("durationSeconds", intMetadataValue(generationMetadata, "durationSeconds", 10));
        candidatesMetadata.put("aspectRatio", aspectRatio.isBlank() ? "9:16" : aspectRatio);
        candidatesMetadata.put("hasHumanVoice", booleanMetadataValue(generationMetadata, "hasHumanVoice"));
        candidatesMetadata.put("candidateRefs", candidateRefs);
        Map<String, Object> reportMetadata = reviewReportMetadata(reviewReport, reviewOutput,
            chainRun.userGoal());
        reportMetadata.put("shortDramaScore", scoreValue(reviewOutput, reviewReport, "shortDramaScore"));
        return List.of(
            artifact(chainRun, ArtifactKind.FinalVideoArtifact, "最终视频", finalUrl, finalMetadata),
            artifact(chainRun, ArtifactKind.VideoCandidateAssets, "视频候选资产", finalUrl, candidatesMetadata),
            artifact(chainRun, ArtifactKind.VideoReviewReport, "视频验收报告",
                reportUrl(chainRun, reviewEvidence), reportMetadata)
        );
    }

    private int candidateCount(Map<String, Object> generationMetadata, List<String> candidateRefs) {
        Object declared = generationMetadata.get("candidateCount");
        if (declared instanceof Number number && number.intValue() > 0) {
            return number.intValue();
        }
        return candidateRefs.size();
    }

    private Object scoreValue(Map<String, Object> reviewOutput, ReviewReport reviewReport, String key) {
        Object value = reviewOutput.get(key);
        if (value != null) {
            return value;
        }
        return reviewReport == null ? 0 : reviewReport.overallScore();
    }

    private Map<String, Object> reviewReportMetadata(ReviewReport reviewReport, Map<String, Object> reviewOutput,
            String userGoal) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("passed", reviewReport == null || reviewReport.passed());
        metadata.put("overallScore", reviewReport == null ? 0 : reviewReport.overallScore());
        metadata.put("rubricVersion", reviewReport == null ? "" : reviewReport.rubricVersion());
        metadata.put("summary", reviewReport == null ? "评审证据缺失: " + userGoal : reviewReport.summary());
        metadata.put("reviewScores", reviewOutput);
        return metadata;
    }

    private String reportUrl(ChainRun chainRun, StageBuildResult reviewEvidence) {
        String reviewStageRunId = reviewEvidence == null ? "unknown" : reviewEvidence.stageRun().stageRunId();
        return "report://" + chainRun.chainRunId() + "/" + reviewStageRunId;
    }

    private int intMetadataValue(Map<String, Object> metadata, String key, int fallback) {
        Object value = metadata.get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private boolean booleanMetadataValue(Map<String, Object> metadata, String key) {
        return Boolean.TRUE.equals(metadata.get(key));
    }

    private Artifact artifact(ChainRun chainRun, ArtifactKind kind, String displayName, String url,
            Map<String, Object> metadata) {
        return new Artifact("artifact-" + UUID.randomUUID(), chainRun.chainRunId(), chainRun.chainType(), kind,
            displayName, url, urlHash(url), Map.copyOf(metadata), Instant.now());
    }

    private String urlHash(String url) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8));
            return "url-sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }
}
