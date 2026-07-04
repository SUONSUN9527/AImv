# AImv Vue3 + Java Rewrite TDD Evidence

## Source

- `../技术文档.md`
- `../前端VUE3重构方案.md`
- 《阿里巴巴 Java 开发手册》（编码规范参考）

## User Journeys

| # | Journey | Guarantee |
|---|---|---|
| 1 | 用户创建图片链路 | 缺少 selected free key 时后端拒绝启动，配置齐全后只执行 I00-I60 |
| 2 | 用户创建视频链路 | 只执行 V00-V60，并输出完整视频合同产物，不出现音频、口型、剪辑阶段 |
| 3 | 用户配置 API key | 前端和后端只返回 masked key，明文 key 不进响应或 store |
| 4 | 用户使用 Vue3 工作台 | 页面只暴露图片/视频生成，剪辑页为 unsupported |
| 5 | 用户查看工作区和资产库 | chain run 状态、stage timeline、最终 artifact 和报告可见 |
| 6 | 系统发现外部能力 | 能力 API 只暴露图片/视频链路所需免费 HTTP adapter 能力 |
| 7 | 系统写入和检索 RAG 证据 | RAG API 按 namespace、chainType、stageCode 隔离并记录 retrieval record |
| 8 | 用户查看阶段证据链 | 每个 `StageRun` 暴露 `retrievalRecordId` 和 `handoffContextId` |
| 9 | 系统执行 agent 节点 | 每个阶段生成 `AgentNodeRun` 和 `ExternalJob`，记录 provider job、状态、重试策略、FreeModelGate 和输出摘要 |
| 10 | 用户配置 DashScope 文本 LLM | selected user key 后 `llm.text.free` 可走 OpenAI 兼容 HTTP，缺少 key 时不误报可用 |
| 11 | 用户配置 DashScope 图片生成 | selected user key 后 `image.generate.free` 可走 multimodal generation HTTP，缺少 key 时不误报可用 |
| 12 | 用户配置 DashScope RAG | selected user key 后 `rag.embedding.free` 和 `rag.rerank.free` 可走兼容 HTTP，缺少 key 时不误报可用 |
| 13 | 用户配置 DashScope 完整视频 | selected user key 后 `video.generate.full_with_voice.free` 只在 provider 证明 10 秒、9:16、原生人声能力时通过门禁 |
| 14 | 用户校验 DashScope 视频 key | verify 阶段不提交真实视频生成任务，未证明原生人声能力时拒绝设为可用 |
| 15 | 系统执行质量评审阶段 | I50/V50 必须读取评审 agent 的量化评分；首次低于 rubric 阈值时自动重做 I40/V40 和 I50/V50，连续两次失败后回到 I30/V30 等待重新选择 provider，且不生成最终 artifact |
| 16 | 用户调整会话侧栏 | 侧栏 resizer 同时支持键盘和 pointer 拖拽，并限制在 198px-360px |
| 17 | 用户预览资产 | 资产预览 modal 支持按钮、遮罩和 Esc 关闭 |
| 18 | 用户在工作区重新编辑 | 用户气泡切换为 textarea + 取消/确定，确认后复用新 chain-run API 创建新链路 |
| 19 | 用户使用中文输入法输入 prompt | IME composing 状态下 Enter 不提交，普通 Enter 才提交 |
| 20 | 用户查看工作区状态 | 前端按后端状态机分别展示生成中、验收中、交付整理、失败和取消文案 |
| 21 | 用户遇到缺能力阻塞 | `WAITING_CAPABILITY` 状态卡片提供 `去配置` 入口并打开当前链路配置面板 |
| 22 | 用户预览最终视频 | 资产库 modal 提供原生视频预览和 0.5x/1x/1.5x/2x 倍速控制 |
| 23 | 评审执行浏览器验收 | Playwright 覆盖桌面/移动端生成页、缺 key 配置拦截和资产视频预览 |
| 24 | 用户添加 API Key | 添加 Key 弹窗可提交 provider、label、model 和一次性明文 key，提交后不保留明文字段 |
| 25 | 用户启动视频链路 | 前端选择视频模式后只调用 `video-chain-runs`，请求体保留用户目标 |
| 26 | 用户查看 API Key 配置 | key 列表展示 provider、label、maskedKey、status、isSelected、lastVerifiedAt 和 freeModelGateStatus |
| 27 | 用户启动链路遇到免费门禁失败 | 前端停留生成页，显示安全错误并打开链路能力配置面板 |
| 28 | 用户从 composer 打开能力配置 | 初始生成页提供可见的 `能力配置` chip 入口 |
| 29 | 用户取消正在执行的链路 | 工作区提供 `取消链路` 入口并调用后端 cancel API，不走旧 provider 或前端直连 |
| 30 | 用户查看启动 API 选择快照 | 工作区加载链路时同步拉取 `api-selection-snapshot`，展示能力、provider、model、maskedKey 和免费额度快照 |
| 31 | 用户删除正在使用中的唯一 Key | 后端拒绝删除时，能力配置面板显示安全错误文案且不出现未处理异常 |
| 32 | 用户查看会话侧栏历史 | 侧栏从项目列表加载最近项目，并按项目标题或目标去重显示 |
| 33 | 用户等待工作区链路加载 | 工作区显示 `history-source.png` 和 `loadingSweep` 骨架，不使用普通 spinner |
| 34 | 用户查看工作区结果 strip | 结果区最多展示 4 个 artifact 卡片，避免长列表挤压对话流 |
| 35 | 用户查看成功后的建议操作 | 工作区链路成功后展示建议操作和底部二次操作，后续动作仍复用前后端分离链路 API |
| 36 | 用户查看成功后的交付摘要 | 工作区链路成功后展示最终产物、候选摘要、验收报告、人声验收和脱敏 providerJobId 摘要 |
| 37 | 用户查看成功链路候选资产 | 后端成功 IMAGE/VIDEO 链路持久返回候选资产、最终产物和评审报告，候选数量符合 I40/V40 设计 |
| 38 | 系统执行固定阶段合同 | 每个 I/V 阶段在目录中声明 schema、rubric、retrieval policy、协作模式和能力类型，链路执行不靠阶段码后缀推断 |
| 39 | 用户在资产库预览图片和报告 | 图片 artifact 打开真实图片预览，报告 artifact 展示验收摘要和元数据，不把报告误当视频 |
| 40 | 系统检索阶段必需上下文 | RAG coverage 必须由显式 `USER_GOAL`、`STAGE_MAP`、`CHAIN_CONTEXT`、上一阶段 handoff 和 review report 共同证明，不能用目标文本冒充阶段图或当前阶段上下文 |
| 41 | 系统隔离链路私有知识库 | RAG 私有 namespace 必须使用 `project:{projectId}:chain:{chainRunId}` 规范格式，旧式 `chain-id-image/video` 命名和跨链路私有 namespace 访问必须阻塞 |
| 42 | 系统向 agent 提供压缩证据包 | RAG 检索结果进入 provider/agent 请求前必须压缩成 `EvidencePack`，包含 coverage、citation chunk ids 和阶段约束，不透传完整历史或明文 key |
| 43 | 系统读取允许的共享知识 | 链路私有 namespace 检索必须同时允许 `global:public` 和 `project:{projectId}` 的只读公共/项目知识进入结果，同时禁止其他链路私有上下文污染 |
| 44 | 系统处理 RAG 证据冲突 | 同一字段在检索证据中出现不一致取值时必须返回 `RAG_EVIDENCE_CONFLICT`，等待人工确认后才允许继续进入 provider/agent |
| 45 | 链路执行遇到 RAG 证据冲突 | 阶段必须进入 `WAITING_REVIEW`，保留链路状态和阻塞原因，不调用 provider/agent，也不生成最终资产 |
| 46 | 系统执行文档化多 Agent 阶段 | I00-I60/V00-V60 必须复用技术文档中的固定 agent 角色清单执行，不再用 `stageCode + Agent` 伪造单节点 |
| 47 | 系统向 agent 传递 StageInputContext | 每个 agent/provider 请求必须携带 `contextVersion/projectId/chainRunId/stageRunId/currentStage` 以及 goal、上一阶段 handoff、上一阶段 review、retrieval policy、stage definition 引用 |
| 48 | 系统确定性合并分工 agent partial | `DIVIDE_AND_MERGE` 阶段必须由非 LLM 的 `StageCoordinator` 按阶段优先级合并 typed partial，记录字段冲突和解决理由，并写入 handoff |
| 49 | 系统校验 agent typed partial schema | `DIVIDE_AND_MERGE` 阶段必须拒绝缺少必填字段或输出 schema 外字段的 partial，不能让自然语言/markdown 字段混入阶段合并 |
| 50 | 系统执行 PromptSafety veto | I20/V20 的 `PromptSafetyAgent` 返回 `safetyPassed=false` 时必须让提示词包阶段失败，链路不得继续到生成阶段或产出最终 artifact |
| 51 | 系统校验 V20 连续性引用 | V20 的 `PromptAgent` 必须通过结构化字段引用 `ContinuityAgent` 输出的连续性约束，未引用时阶段失败且不得进入 V30/V40 |
| 52 | 系统锁定 I00/V00 用户目标 | I00/V00 的 `GoalAgent` 必须输出结构化目标并通过固定 rubric；缺字段、count/时长/画幅/人声要求、目标清晰度或安全分不达标时阶段失败 |
| 53 | 系统校验 V20 原生人声配音目标 | V20 的 `PromptAgent` 必须输出 `voiceoverRequirement=HUMAN_VOICE_REQUIRED`，缺失或不匹配时不得进入 V30/V40 |
| 54 | 系统校验 V20 动作提示词引用 | V20 的 `PromptAgent` 必须通过结构化字段引用 `MotionPromptAgent` 输出的动作提示词，未引用时阶段失败且不得进入 V30/V40 |
| 55 | 系统校验 V20 人设和风格约束引用 | V20 的 `PromptAgent` 必须通过结构化字段引用 `ContinuityAgent` 输出的人设连续性和风格约束，未引用时阶段失败且不得进入 V30/V40 |
| 56 | 系统校验 I20 图片提示词变量解析 | I20 的 `PromptAgent` 必须输出非空 `positivePrompt` 和 `promptVariables`，且 `positivePrompt` 不得保留 `{{...}}` 未解析占位符，未达标时阶段失败且不得进入 I30/I40 |

## RED Evidence

| Target | Command | RED result |
|---|---|---|
| Backend | `mvn test` | failed because `domain layer must exist` and no `@SpringBootConfiguration` existed after old code deletion |
| Frontend | `npm test` | failed because `../App.vue` and `../router/routes` were missing after old React code deletion |
| ExternalJob persistence | `mvn -q -Dtest=DefaultAgentNodeFactoryTest,PostgresSchemaMigrationTest,ChainRunControllerTest test` | failed because `com.aimv.domain.externaljob` did not exist before implementing ExternalJob domain and repositories |
| ExternalJob query API | `mvn -q -Dtest=ChainRunControllerTest#runsImageChainThroughFixedStagesWhenFreeFixtureKeysAreSelected test` | failed with missing `/api/chain-runs/{chainRunId}/external-jobs` route before adding the controller |
| Frontend external job evidence | `npm test -- FrontendFlows.test.ts` | failed because workspace loaded the chain run but did not fetch or render masked provider job evidence |
| DashScope text provider adapter | `mvn -q -Dtest=RoutingProviderHttpGatewayTest test` | failed at test compile because `DashScopeTextProviderOptions` and the built-in DashScope route did not exist |
| DashScope image provider adapter | `mvn -q -Dtest=RoutingProviderHttpGatewayTest test` | failed at test compile because `DashScopeImageProviderOptions` and the built-in DashScope image route did not exist |
| DashScope RAG provider adapter | `mvn -q -Dtest=RoutingProviderHttpGatewayTest test` | failed at test compile because `DashScopeRagProviderOptions` and the built-in embedding/rerank routes did not exist |
| DashScope video provider adapter | `mvn -q -Dtest=RoutingProviderHttpGatewayTest test` | failed at test compile because `DashScopeVideoProviderOptions` and the built-in async video route did not exist |
| Selected user provider key routing | `mvn -q -Dtest=RoutingProviderHttpGatewayTest test` | failed at test compile because `ProviderSecretResolver` and selected-key DashScope routing did not exist |
| NextStageContext RAG handoff | `mvn -q -Dtest=ChainRunControllerTest#writesSchemaCompliantNextStageContextToRagForEachStage test` | failed at test compile because `KnowledgeRepository.findChunk` and schema-backed handoff chunk persistence did not exist |
| Chain start FreeModelGate recheck | `mvn -q -Dtest=ChainRunControllerTest#rejectsChainStartWhenSelectedKeyNoLongerPassesFreeModelGate test` | failed because chain start returned 200 and executed stages even when a selected key had `freeModelGateStatus=FAILED` |
| API key response scope | `mvn -q -Dtest=ApiConfigControllerTest#neverReturnsPlainApiKeyAndRejectsDeletingSelectedOnlyKey test` | failed because key summary responses omitted `chainType` and `capabilityType` from the documented response shape |
| Provider failed status mapping | `mvn -q -Dtest=DefaultAgentNodeFactoryTest#marksAgentNodeFailedWhenProviderReportsFreeQuotaExhausted test` | failed because provider `FAILED/FREE_QUOTA_EXHAUSTED` was still persisted as a successful agent node |
| Provider failed chain blocking | `mvn -q -Dtest=ChainRunProviderFailureControllerTest#returnsWaitingCapabilityWhenProviderReportsFreeQuotaExhausted test` | failed because the chain returned `SUCCEEDED`, generated downstream stages, and produced final artifacts after a failed provider response |
| Video full-with-voice capability evidence gate | `mvn -q -Dtest=ChainRunVideoCapabilityGateControllerTest#blocksVideoChainWhenProviderDoesNotProveNativeHumanVoiceCapability test` | failed because the video chain returned `SUCCEEDED`, continued to V60, and produced final artifacts even though V40 metadata did not prove native human voice support |
| DashScope video key verification no-submit gate | `mvn -q -Dtest=RoutingProviderHttpGatewayTest#dashScopeVideoVerifyDoesNotSubmitGenerationWhenNativeHumanVoiceIsUnsupported test` | failed because API key verification submitted `POST /services/aigc/video-generation/video-synthesis` instead of making a local capability decision |
| Video API key capability evidence gate | `mvn -q -Dtest=ApiConfigAdapterVerificationTest#rejectsVideoKeyVerificationWhenProviderDoesNotProveNativeHumanVoiceCapability test` | failed because API key verification returned 200 and `PASSED` even though provider metadata did not prove native human voice support |
| Quality review rubric gate | `mvn -q -Dtest=ChainRunQualityGateControllerTest test` | failed because IMAGE and VIDEO chains returned `SUCCEEDED`, continued to I60/V60, and generated final artifacts even when I50/V50 provider metadata had low score or missing audible human voice evidence |
| Quality recovery routing | `mvn -q -Dtest=ChainRunQualityGateControllerTest test` | failed because a first I50 quality failure stopped at the review stage instead of rerunning I40/I50, and repeated V50 human-voice failures did not return to V30 `WAITING_USER` when no alternative full-video provider existed |
| Recovery automatic provider switch | `mvn -q -Dtest=ChainRunQualityGateControllerTest#autoSelectsAlternativeVideoProviderAfterRepeatedQualityFailure test` | failed because the chain returned `WAITING_USER` even though an alternative verified full-video provider was available |
| NextStageContext evidence check | `mvn -q -Dtest=ChainRunControllerTest#writesSchemaCompliantNextStageContextToRagForEachStage test` | failed because the handoff JSON had no critical `claims`, `citationChunkIds`, or `evidenceCheck` gate |
| RAG required coverage | `mvn -q -Dtest=KnowledgeControllerTest#reportsRequiredCoverageForNonInitialStageEvidence,ChainRunControllerTest#retrievesPreviousHandoffAndReviewReportForNextStageRagContext test` | failed because retrieve responses had no `coverage` and next-stage retrieval returned only `CHAIN_CONTEXT`, not previous handoff/review report |
| RetrievalRecord coverage persistence | `mvn -q -Dtest=KnowledgeControllerTest#reportsRequiredCoverageForNonInitialStageEvidence test` | failed because `GET /api/retrieval-records/{id}` returned hit chunk ids but no persisted coverage |
| API selection user scope | `mvn -q -Dtest=PostgresSchemaMigrationTest#scopesApiSelectionByUserChainAndCapability test` | failed because `api_selection` had no `user_id` and uniqueness was only `(chain_type, capability_type)` |
| Vue3 sidebar pointer resize | `npm test -- ComponentInteractions.test.ts -t "resizes the creation sidebar"` | failed because pointer drag left the shell width unchanged at 238px instead of applying the drag delta |
| Vue3 asset preview Escape close | `npm test -- ComponentInteractions.test.ts -t "closes the asset preview"` | failed because pressing Esc left the preview dialog mounted |
| Vue3 workspace inline edit | `npm test -- FrontendFlows.test.ts -t "edits the workspace user bubble"` | failed because `重新编辑` was a `/generate` link and no inline textarea/cancel/confirm flow existed |
| Vue3 workspace cancel chain | `npm test -- FrontendFlows.test.ts -t "cancels an executing chain"` | failed because the workspace had no accessible `取消链路` action even though the API layer exposed cancel |
| Vue3 composer IME enter guard | `npm test -- ComponentInteractions.test.ts -t "IME composition"` | failed because Enter during IME composition emitted `submit` immediately |
| Vue3 workspace documented status copy | `npm test -- BranchCoverage.test.ts -t "documented review"` | failed because `QUALITY_REVIEWING`, handoff, failed, and cancelled states still rendered the generic fixed-harness copy |
| Vue3 workspace capability config entry | `npm test -- BranchCoverage.test.ts -t "WAITING_CAPABILITY"` | failed because the workspace had no accessible `去配置` button in `WAITING_CAPABILITY` state |
| Vue3 asset video playback speed | `npm test -- BranchCoverage.test.ts -t "playback speed"` | failed because the preview modal rendered only the video URL and no accessible video preview |
| Vue3 Playwright acceptance | `npx playwright test` | failed because Playwright had no dedicated config/testDir and tried to parse Vitest `.vue` component tests directly |
| Vue3 add key dialog | `npm test -- FrontendFlows.test.ts -t "adds a key"` | failed because the API key plaintext input was rendered inline before the user opened an add-key dialog |
| Vue3 add key model field | `npm test -- FrontendFlows.test.ts -t "adds a key"` | failed because the add-key dialog could not submit the documented `model` request field |
| Vue3 key field display | `npm test -- ComponentInteractions.test.ts -t "configured key field"` | failed because configured key label/status were not rendered; only provider, maskedKey, and freeModelGateStatus were visible |
| Vue3 key verified-at display | `npm test -- ComponentInteractions.test.ts -t "configured key field"` | failed because configured key `lastVerifiedAt` was present in the API shape but not rendered in the key row |
| Vue3 selected key indicator | `npm test -- ComponentInteractions.test.ts -t "configured key field"` | failed because `isSelected=true` was present in the API shape but the key row did not render a current selected marker |
| Vue3 free gate start failure UI | `npm test -- FrontendFlows.test.ts -t "free model gate"` | failed because `FREE_MODEL_GATE_FAILED` was an unhandled rejection and the generate page did not show the safe error or open config |
| Vue3 capability chip copy | `npm test -- WorkbenchContract.test.ts -t "Jimeng shell"` | failed because the composer capability chip used visible text `配置` instead of the documented `能力配置` |
| Vue3 workspace API selection snapshot | `npm test -- FrontendFlows.test.ts -t "loads a workspace chain run"` | failed because the workspace loaded stages, external jobs, and final artifacts but did not fetch or render `API 选择快照` |
| Vue3 selected key delete rejection UI | `npm test -- ComponentInteractions.test.ts -t "deleting the selected key"` | failed because `DELETE_SELECTED_KEY_REJECTED` produced an unhandled Vue event error and no user-facing message |
| Project list API | `mvn -q -Dtest=ProjectControllerTest test` | failed because `GET /api/projects` was not supported and returned the generic 500 error envelope |
| Vue3 sidebar project history | `npm test -- ComponentInteractions.test.ts -t "sidebar history"` | failed because the sidebar rendered only in-memory chain runs and never loaded project history from the backend |
| Vue3 workspace loading skeleton | `npm test -- BranchCoverage.test.ts -t "workspace loading skeleton"` | failed because workspace loading with no active chain rendered the empty state instead of `history-source.png` plus `loadingSweep` |
| Vue3 result strip card limit | `npm test -- BranchCoverage.test.ts -t "result strip"` | failed because `ResultStrip` rendered the fifth artifact even though the frontend document caps the strip at 4 cards |
| Vue3 workspace success actions | `npm test -- FrontendFlows.test.ts -t "loads a workspace chain run"` | failed because succeeded workspace had no accessible `建议操作` or `底部二次操作` region |
| Vue3 workspace delivery summary | `npm test -- FrontendFlows.test.ts -t "delivery"` | failed because succeeded video workspace had no accessible `交付摘要` region for voice acceptance and provider job summary |
| Backend candidate artifacts | `mvn -q -Dtest=ChainRunControllerTest#runsImageChainThroughFixedStagesWhenFreeFixtureKeysAreSelected,ChainRunControllerTest#runsVideoChainAsCompleteVideoWithVoiceWithoutOldAudioOrEditStages test` | failed because successful IMAGE/VIDEO responses returned only final artifact and review report, without `ImageCandidateAssets` or `VideoCandidateAssets` |
| Backend stage catalog contracts | `mvn -q -Dtest=StageCatalogContractTest test` | failed at testCompile because `StageDefinition` exposed only stage code/name and had no schema, rubric, retrieval policy, collaboration mode, or capability contract fields |
| Vue3 asset image/report preview | `npm test -- FrontendFlows.test.ts -t "renders artifact categories"` | failed because the asset modal rendered the final image URL as text and had no accessible image preview or report summary region |
| RAG explicit coverage sources | `mvn -q -Dtest=KnowledgeControllerTest#goalChunkDoesNotSatisfyStageMapOrCurrentStageCoverage,ChainRunControllerTest#runsImageChainThroughFixedStagesWhenFreeFixtureKeysAreSelected,ChainRunControllerTest#retrievesPreviousHandoffAndReviewReportForNextStageRagContext test` | failed because `USER_GOAL` was counted as stageMap/currentStage coverage, I00 retrieval returned only one chunk, and I30 retrieval did not include explicit `USER_GOAL` or `STAGE_MAP` chunks |
| RAG namespace contract | `mvn -q -Dtest=KnowledgeControllerTest#rejectsLegacyOrMalformedPrivateNamespace,KnowledgeControllerTest#rejectsCrossChainNamespaceAccess,ChainRunControllerTest#writesSchemaCompliantNextStageContextToRagForEachStage,ChainRunControllerTest#retrievesPreviousHandoffAndReviewReportForNextStageRagContext test` | failed because chain runs still wrote `chain-{id}-image` namespaces, retrieval by documented project-chain namespace returned no chunks, and malformed legacy private namespaces were accepted |
| Agent EvidencePack contract | `mvn -q -Dtest=ChainRunEvidencePackControllerTest test` | failed because the chain completed successfully but every provider request input lacked `evidencePack` |
| RAG allowed namespace retrieval | `mvn -q -Dtest=KnowledgeControllerTest#chainPrivateRetrievalIncludesAllowedPublicAndProjectKnowledge test` | failed because retrieval by chain-private namespace returned only private chain chunks and omitted `global:public` plus `project:{projectId}` knowledge |
| RAG evidence conflict gate | `mvn -q -Dtest=KnowledgeControllerTest#rejectsRetrievalWhenEvidenceChunksHaveUnresolvedFieldConflicts test` | failed because retrieval returned 200 with conflicting `aspectRatio` chunks instead of `RAG_EVIDENCE_CONFLICT` |
| Chain RAG conflict WAITING_REVIEW | `mvn -q -Dtest=ChainRunControllerTest#waitsForReviewWhenChainRagEvidenceHasUnresolvedConflicts test` | failed because chain start returned 409 `RAG_EVIDENCE_CONFLICT` instead of a persisted `WAITING_REVIEW` chain with no provider jobs |
| Documented multi-agent roles | `mvn -q -Dtest=StageCatalogContractTest#stageContractsDeclareDocumentedAgentRoles,ChainRunEvidencePackControllerTest#sendsCompressedEvidencePackToEveryAgentProviderRequest test` | failed at testCompile because `StageDefinition` had no `agentNames()` contract and chain execution still derived one node as `stageCode + Agent` |
| Vue3 WAITING_REVIEW copy | `npm test -- BranchCoverage.test.ts -t "waiting states"` | failed because `WAITING_REVIEW` rendered the generic `正在生成中。` copy instead of the backend blocking reason |
| StageInputContext provider contract | `mvn -q -Dtest=ChainRunEvidencePackControllerTest#sendsCompressedEvidencePackToEveryAgentProviderRequest test` | failed because provider request input contained `evidencePack` but no `stageInputContext` key, so agents could not see the documented stage context refs |
| StageCoordinator partial merge | `mvn -q -Dtest=StageCoordinatorTest,ChainRunEvidencePackControllerTest#sendsCompressedEvidencePackToEveryAgentProviderRequest test` | failed at testCompile because `StageCoordinator`, `StageCoordinationResult`, and `StagePartialOutput` did not exist, so divide-and-merge stages had no deterministic partial merger |
| Stage partial schema gate | `mvn -q -Dtest=StageCoordinatorTest test` | failed because `StageCoordinator` accepted partial outputs that missed required schema fields or contained unknown `markdownNarration` outside the typed partial schema |
| PromptSafety veto gate | `mvn -q -Dtest=StageReviewPolicyTest,ChainRunQualityGateControllerTest#failsImagePromptPackWhenPromptSafetyAgentVetoes test` | failed because `StageReviewPolicy` accepted `safetyPassed=false`, the chain continued from I20 to I60, and final artifacts were created after a safety veto |
| V20 continuity reference gate | `mvn -q -Dtest=StageReviewPolicyTest,ChainRunQualityGateControllerTest#failsVideoPromptPackWhenPromptMissesContinuityReference test` | failed because `StageReviewPolicy` accepted missing or wrong `continuityConstraintRefs`, and the interface path returned 500 before the V20 PromptAgent schema allowed the structured reference field |
| I00/V00 target lock gate | `mvn -q -Dtest=StageReviewPolicyTest,ChainRunQualityGateControllerTest#failsImageGoalLockWhenGoalAgentMissesStructuredScene test` | failed because `StageReviewPolicy` accepted missing I00/V00 goal-lock fields and the chain continued from I00 to I60 with final artifacts |
| V20 native voice prompt gate | `mvn -q -Dtest=StageReviewPolicyTest,ChainRunQualityGateControllerTest#failsVideoPromptPackWhenPromptMissesNativeVoiceRequirement test` | failed because `StageReviewPolicy` accepted a V20 prompt pack without `voiceoverRequirement=HUMAN_VOICE_REQUIRED`, and the chain continued past V20 into generation/recovery |
| V20 motion prompt reference gate | `mvn -q -Dtest=StageReviewPolicyTest,ChainRunQualityGateControllerTest#failsVideoPromptPackWhenPromptMissesMotionReference test` | failed because `StageReviewPolicy` accepted wrong `motionPromptRefs`, and the interface path rejected `motionPromptRefs` as schema-outside before the intended MotionPromptAgent review |
| V20 character and visual style reference gate | `mvn -q -Dtest=StageReviewPolicyTest,ChainRunQualityGateControllerTest#failsVideoPromptPackWhenPromptMissesCharacterContinuityReference+failsVideoPromptPackWhenPromptMissesVisualStyleReference test` | failed because `StageReviewPolicy` accepted wrong `characterContinuityRefs` and `visualStyleRefs`, and the interface path rejected those fields as schema-outside before the intended V20 review |
| I20 prompt variable resolution gate | `mvn -q -Dtest=StageReviewPolicyTest,ChainRunQualityGateControllerTest#failsImagePromptPackWhenPromptVariablesAreMissing test` | failed because `StageReviewPolicy` accepted missing or unresolved `promptVariables`, and the IMAGE chain continued through I60 with artifacts when `PromptAgent` returned `portrait of {{subject}}` without `promptVariables` |
| I20 positive prompt schema gate | `mvn -q -Dtest=StageReviewPolicyTest#rejectsImagePromptPackWhenPositivePromptIsBlank test` | failed because `StageReviewPolicy` accepted blank `positivePrompt` when `promptVariables` was present |

## GREEN Evidence

| Target | Command | Result |
|---|---|---|
| Backend unit/integration | `mvn verify` | PASS, 65 tests |
| Backend verify/coverage | `mvn verify` | PASS, JaCoCo coverage check met |
| Frontend unit/component | `npm test` | PASS, 34 tests |
| Frontend coverage | `npm run test:coverage` | PASS, statements 90.94%, branches 81.33%, functions 94.04%, lines 91.08% |
| Frontend production build | `npm run build` | PASS, Vite built `dist/` |
| Frontend dependency audit | `npm audit --audit-level=high` | PASS, 0 vulnerabilities |
| Frontend Playwright E2E | `npx playwright test` | PASS, 6 tests across 1440x980 desktop and 390x900 mobile Chromium |
| Runtime smoke | HTTP against backend `:8081` and frontend `:5174` | PASS, health `UP`, IMAGE and VIDEO fixture runs each produced 7 stages; I30/V30 RAG retrieval returned `CHAIN_CONTEXT`, `NEXT_STAGE_CONTEXT`, `REVIEW_REPORT`, retrieve coverage and retrieval-record detail coverage passed, and EvidenceCheck passed |
| PostgreSQL + pgvector runtime smoke | Docker `pgvector/pgvector:pg16` + backend `postgres` profile on `:18081` | PASS, Flyway validated pgvector schema, IMAGE and VIDEO fixture runs each produced 7 stages with RAG coverage/EvidenceCheck passed; latest `coverage_json.previousReviewReport=true` and `passed=true`; database contained 17 chain runs, 119 stage runs, 266 knowledge chunks, 124 retrieval records, and 68 API selection snapshots |
| DashScope text provider route | `mvn -q -Dtest=RoutingProviderHttpGatewayTest test` | PASS, selected user key or compatible fallback uses bearer header only and no-key config refuses real provider activation |
| DashScope image provider route | `mvn -q -Dtest=RoutingProviderHttpGatewayTest test` | PASS, multimodal generation request uses bearer header only, returns image URL artifact refs, and no-key config refuses real provider activation |
| DashScope RAG provider route | `mvn -q -Dtest=RoutingProviderHttpGatewayTest test` | PASS, embeddings/reranks requests use bearer header only, redact key material from JSON bodies, and no-key config refuses real provider activation |
| DashScope video provider route | `mvn -q -Dtest=RoutingProviderHttpGatewayTest test` | PASS, async video request and task polling use bearer header only, redact key material from JSON bodies, and no-key config refuses real provider activation |
| NextStageContext RAG handoff | `mvn -q -Dtest=ChainRunControllerTest#writesSchemaCompliantNextStageContextToRagForEachStage,EvidenceCheckPolicyTest test` | PASS, each tested handoff id resolves to a `NEXT_STAGE_CONTEXT` RAG chunk for the next stage with critical claims, citationChunkIds, evidenceCheck, and no plaintext key material |
| Chain start FreeModelGate recheck | `mvn -q -Dtest=ChainRunControllerTest#rejectsChainStartWhenSelectedKeyNoLongerPassesFreeModelGate test` | PASS, selected key state drift to failed gate returns `FREE_MODEL_GATE_FAILED` before stage execution and does not leak plaintext key material |
| API key response scope | `mvn -q -Dtest=ApiConfigControllerTest#neverReturnsPlainApiKeyAndRejectsDeletingSelectedOnlyKey test` | PASS, key summary responses include `chainType` and `capabilityType` while still hiding plaintext key material |
| Provider failed status mapping | `mvn -q -Dtest=DefaultAgentNodeFactoryTest#marksAgentNodeFailedWhenProviderReportsFreeQuotaExhausted test` | PASS, failed provider responses persist `ExternalJobStatus.FAILED` and `AgentNodeRunStatus.FAILED` without plaintext key material |
| Provider failed chain blocking | `mvn -q -Dtest=ChainRunProviderFailureControllerTest#returnsWaitingCapabilityWhenProviderReportsFreeQuotaExhausted test` | PASS, failed provider responses stop the chain at the current stage with `WAITING_CAPABILITY`, no downstream stages, and no final artifacts |
| Video full-with-voice capability evidence gate | `mvn -q -Dtest=ChainRunVideoCapabilityGateControllerTest#blocksVideoChainWhenProviderDoesNotProveNativeHumanVoiceCapability test` | PASS, V40 stops with `WAITING_CAPABILITY` when provider metadata does not prove native human voice support |
| DashScope video verify no-submit gate | `mvn -q -Dtest=RoutingProviderHttpGatewayTest#dashScopeVideoVerifyDoesNotSubmitGenerationWhenNativeHumanVoiceIsUnsupported test` | PASS, verify returns local `FAILED` evidence and MockRestServiceServer observes no generation request |
| Video API key capability evidence gate | `mvn -q -Dtest=ApiConfigAdapterVerificationTest#rejectsVideoKeyVerificationWhenProviderDoesNotProveNativeHumanVoiceCapability test` | PASS, verify returns 409 `FREE_MODEL_GATE_FAILED` without plaintext key material when native human voice support is not proven |
| Quality review rubric gate and recovery routing | `mvn -q -Dtest=ChainRunQualityGateControllerTest test` | PASS, first I50 low image score records a failed review attempt, reruns I40/I50, and delivers when the second review passes; repeated V50 missing human voice evidence returns to V30 `WAITING_USER` with no final artifacts when no alternative full-video provider exists |
| Recovery automatic provider switch | `mvn -q -Dtest=ChainRunQualityGateControllerTest test` | PASS, repeated V50 failure auto-selects an alternative verified full-video provider, appends its API selection snapshot, restarts at V30, and delivers V60 |
| Recovery provider reselection | `mvn -q -Dtest=ChainRunQualityGateControllerTest test` | PASS, redoing recovery V30 after user selects a new full-video provider refreshes selected credentials, appends a new API selection snapshot, and uses the new provider for V40 |
| Frontend recovery redo routing | `npm test -- FrontendFlows.test.ts` | PASS, `WAITING_USER` workspace retry calls the recovery V30 redo endpoint instead of the old V40 generation stage |
| RAG required coverage | `mvn -q -Dtest=KnowledgeControllerTest#reportsRequiredCoverageForNonInitialStageEvidence,ChainRunControllerTest#retrievesPreviousHandoffAndReviewReportForNextStageRagContext test` | PASS, non-initial stage retrieval exposes coverage and includes `USER_GOAL`, `STAGE_MAP`, `CHAIN_CONTEXT`, `NEXT_STAGE_CONTEXT`, and `REVIEW_REPORT` chunks |
| RetrievalRecord coverage persistence | `mvn -q -Dtest=KnowledgeControllerTest#reportsRequiredCoverageForNonInitialStageEvidence test` | PASS, `RetrievalRecord` domain/DTO carries the same coverage as retrieve response, and Postgres writes it to `coverage_json`/`passed` |
| API selection user scope | `mvn -q -Dtest=PostgresSchemaMigrationTest#scopesApiSelectionByUserChainAndCapability test` | PASS, Postgres selection storage scopes uniqueness by `user_id`, chain, and capability |
| Vue3 sidebar pointer resize | `npm test -- ComponentInteractions.test.ts -t "resizes the creation sidebar"` | PASS, pointer drag updates sidebar width and clamps it to the documented 198px-360px range |
| Vue3 asset preview Escape close | `npm test -- ComponentInteractions.test.ts -t "closes the asset preview"` | PASS, non-Escape keys keep the modal open and Esc closes it |
| Vue3 workspace inline edit | `npm test -- FrontendFlows.test.ts -t "edits the workspace user bubble"` | PASS, `重新编辑` switches the user bubble to textarea/cancel/confirm, and confirm starts a new IMAGE chain through the documented chain-run API |
| Vue3 workspace cancel chain | `npm test -- FrontendFlows.test.ts -t "cancels an executing chain"` | PASS, `取消链路` calls `/api/chain-runs/{chainRunId}:cancel` and renders the documented cancelled status copy |
| Vue3 composer IME enter guard | `npm test -- ComponentInteractions.test.ts -t "IME composition"` | PASS, IME composing Enter does not submit, while normal Enter submits the prompt |
| Vue3 workspace documented status copy | `npm test -- BranchCoverage.test.ts -t "documented review"` | PASS, workspace renders documented copy for review, handoff, knowledge ingestion, failed, cancelled, and executing states |
| Vue3 workspace capability config entry | `npm test -- BranchCoverage.test.ts -t "WAITING_CAPABILITY"` | PASS, `WAITING_CAPABILITY` exposes `去配置`, loads VIDEO capability slots, and renders the existing `ApiConfigPanel` |
| Vue3 asset video playback speed | `npm test -- BranchCoverage.test.ts -t "playback speed"` | PASS, final video artifacts render native video controls and update `playbackRate` from the 1.5x button |
| Vue3 Playwright acceptance | `npx playwright test` | PASS, generate shell, capability blocking, add-key dialog, mobile rail/sidebar, and asset video speed controls pass in Chromium |
| Vue3 add key dialog | `npm test -- FrontendFlows.test.ts -t "adds a key"` | PASS, plaintext API key input appears only inside the dialog, `model` is submitted, and plaintext is removed after submit |
| Vue3 video chain request contract | `npm test -- FrontendFlows.test.ts -t "video chain"` | PASS, VIDEO mode loads VIDEO configs, calls `/video-chain-runs`, avoids image/pipeline endpoints, and keeps the user goal in the request body |
| Vue3 key field display | `npm test -- ComponentInteractions.test.ts -t "configured key field"` | PASS, key rows render provider, label, maskedKey, status, selected marker, lastVerifiedAt, and freeModelGateStatus |
| Vue3 free gate start failure UI | `npm test -- FrontendFlows.test.ts -t "free model gate"` | PASS, selected-key drift to `FREE_MODEL_GATE_FAILED` shows the safe error, keeps the user on `/generate`, and opens the chain-scoped config panel |
| Vue3 capability chip copy | `npm test -- WorkbenchContract.test.ts -t "Jimeng shell"` | PASS, initial generate page exposes a visible `能力配置` chip alongside image/video chain modes |
| Vue3 workspace API selection snapshot | `npm test -- FrontendFlows.test.ts -t "loads a workspace chain run"` | PASS, workspace loads `/api/chain-runs/{chainRunId}/api-selection-snapshot` and renders capability, provider, model, masked key, and quota snapshot |
| Vue3 selected key delete rejection UI | `npm test -- ComponentInteractions.test.ts -t "deleting the selected key"` | PASS, rejected deletion renders the backend safe message and avoids unhandled native event errors |
| Project list API | `mvn -q -Dtest=ProjectControllerTest test` | PASS, `GET /api/projects` returns recent projects latest-first for sidebar history |
| Vue3 sidebar project history | `npm test -- ComponentInteractions.test.ts -t "sidebar history"` | PASS, sidebar loads project history and deduplicates repeated titles before rendering |
| Vue3 workspace loading skeleton | `npm test -- BranchCoverage.test.ts -t "workspace loading skeleton"` | PASS, workspace loading renders `history-source.png` with the `loadingSweep` skeleton animation |
| Vue3 result strip card limit | `npm test -- BranchCoverage.test.ts -t "result strip"` | PASS, `ResultStrip` renders only the first 4 artifacts |
| Vue3 workspace success actions | `npm test -- FrontendFlows.test.ts -t "loads a workspace chain run"` | PASS, succeeded workspace renders `建议操作`, asset-library link, `底部二次操作`, and the redo action |
| Vue3 workspace delivery summary | `npm test -- FrontendFlows.test.ts` | PASS, succeeded IMAGE and VIDEO workspaces render final artifact, candidate summary, review summary, voice acceptance, and masked provider job summary |
| Backend candidate artifacts | `mvn -q -Dtest=ChainRunControllerTest#runsImageChainThroughFixedStagesWhenFreeFixtureKeysAreSelected,ChainRunControllerTest#runsVideoChainAsCompleteVideoWithVoiceWithoutOldAudioOrEditStages test` | PASS, successful IMAGE returns `ImageCandidateAssets` with candidateCount 4 and VIDEO returns `VideoCandidateAssets` with candidateCount 1 |
| Backend stage catalog contracts | `mvn -q -Dtest=StageCatalogContractTest test` | PASS, all IMAGE/VIDEO stages declare input/output schema ids, rubric versions, retrieval policy ids, collaboration modes, capability types, and documented agent names |
| Vue3 asset image/report preview | `npm test -- FrontendFlows.test.ts -t "renders artifact categories"` | PASS, final image artifacts render an accessible image preview and report artifacts render the review summary instead of a bare URL |
| RAG explicit coverage sources | `mvn -q -Dtest=KnowledgeControllerTest#goalChunkDoesNotSatisfyStageMapOrCurrentStageCoverage,KnowledgeControllerTest#reportsRequiredCoverageForNonInitialStageEvidence,ChainRunControllerTest#runsImageChainThroughFixedStagesWhenFreeFixtureKeysAreSelected,ChainRunControllerTest#retrievesPreviousHandoffAndReviewReportForNextStageRagContext test` | PASS, coverage only passes when explicit goal, stage map, current stage, previous handoff, and previous review evidence are retrieved |
| RAG namespace contract | `mvn -q -Dtest=KnowledgeControllerTest#rejectsLegacyOrMalformedPrivateNamespace,KnowledgeControllerTest#rejectsCrossChainNamespaceAccess,KnowledgeControllerTest#ingestsAndRetrievesOnlyMatchingNamespaceAndStageEvidence,ChainRunControllerTest#writesSchemaCompliantNextStageContextToRagForEachStage,ChainRunControllerTest#retrievesPreviousHandoffAndReviewReportForNextStageRagContext test` | PASS, chain-run RAG writes use `project:{projectId}:chain:{chainRunId}`, malformed legacy namespaces are rejected, and occupied private namespaces cannot be read as another chain type |
| Agent EvidencePack contract | `mvn -q -Dtest=ChainRunEvidencePackControllerTest test` | PASS, every IMAGE stage role provider request carries compressed `EvidencePack`; I30 includes previous handoff/review coverage, citation chunk ids, required constraints, and no plaintext key |
| RAG allowed namespace retrieval | `mvn -q -Dtest=KnowledgeControllerTest#chainPrivateRetrievalIncludesAllowedPublicAndProjectKnowledge test` | PASS, chain-private retrieval returns allowed private, project, and global-public chunks while excluding another chain type's private context |
| RAG evidence conflict gate | `mvn -q -Dtest=KnowledgeControllerTest#rejectsRetrievalWhenEvidenceChunksHaveUnresolvedFieldConflicts test` | PASS, same-field evidence conflicts return 409 `RAG_EVIDENCE_CONFLICT` before saving a retrieval record or entering provider/agent execution |
| Chain RAG conflict WAITING_REVIEW | `mvn -q -Dtest=ChainRunControllerTest#waitsForReviewWhenChainRagEvidenceHasUnresolvedConflicts test` | PASS, chain execution records `WAITING_REVIEW`, keeps provider job and agent node ids empty, and produces no artifacts on unresolved RAG conflicts |
| Vue3 WAITING_REVIEW copy | `npm test -- BranchCoverage.test.ts -t "waiting states"` | PASS, workspace renders the backend blocking reason for `WAITING_REVIEW` instead of generic executing copy |
| Documented multi-agent roles | `mvn -q -Dtest=StageCatalogContractTest,ChainRunEvidencePackControllerTest,ChainRunControllerTest,ChainRunProviderFailureControllerTest,ChainRunVideoCapabilityGateControllerTest,ChainRunQualityGateControllerTest test` | PASS, IMAGE sends 14 provider node requests with documented names, `StageRun` aggregates all node/free-gate/provider-job ids, and quality/recovery gates still pass |
| Multi-agent runtime smoke | HTTP against backend `:18082` and frontend `:5176` | PASS, backend health `UP`, frontend HTTP 200, IMAGE fixture run returned 7 stages, 14 node runs, 14 external jobs, and node names `GoalAgent` through `ImageAcceptanceAgent` in documented order |
| StageInputContext provider contract | `mvn -q -Dtest=ChainRunEvidencePackControllerTest#sendsCompressedEvidencePackToEveryAgentProviderRequest test` | PASS, every provider request includes `stageInputContext`; I30 carries project/chain/stage ids plus goal, previous handoff, previous review, retrieval policy, and stage definition refs |
| Post-StageInputContext runtime smoke | HTTP against restarted backend `:18082` and frontend `:5176` | PASS, restarted jar health `UP`, frontend HTTP 200, IMAGE fixture run returned `SUCCEEDED` with 7 stage runs, 14 node runs, 14 external jobs, 4 API selection snapshots, and no plaintext key in runtime responses |
| StageCoordinator partial merge | `mvn -q -Dtest=StageCatalogContractTest,StageCoordinatorTest,ChainRunEvidencePackControllerTest,ChainRunControllerTest,ChainRunQualityGateControllerTest,ChainRunProviderFailureControllerTest,ChainRunVideoCapabilityGateControllerTest test` | PASS, divide-and-merge stages declare merge priority, `StageCoordinator` merges partial outputs deterministically, records conflicts, and I10 handoff persists merged `stageOutput` without breaking chain quality/recovery gates |
| Post-StageCoordinator runtime smoke | HTTP against restarted backend `:18082` | PASS, restarted jar health `UP`, IMAGE fixture run returned `SUCCEEDED` with 7 stage runs, 14 node runs, 14 external jobs, 4 API selection snapshots, and no plaintext key in runtime responses |
| Stage partial schema gate | `mvn verify` | PASS, 71 backend tests passed; every divide-and-merge stage declares per-agent partial schemas, `StageCoordinator` rejects missing required fields and schema-outside fields, and fixture/mock providers return schema-compliant typed partials |
| Post-partial-schema runtime smoke | HTTP against restarted backend `:18082` | PASS, IMAGE fixture run returned `SUCCEEDED` with 7 stage runs, I10 nodes `SubjectAgent,StyleAgent,ConstraintAgent`, 14 external jobs, 4 API selection snapshots, and no plaintext key in runtime responses |
| PromptSafety veto gate | `mvn verify` | PASS, 74 backend tests passed; I20/V20 prompt-pack reviews fail on `PromptSafetyAgent` veto, chain status becomes `FAILED` for a final failed stage, and no downstream artifact is created after an I20 safety veto |
| Post-PromptSafety runtime smoke | HTTP against restarted backend `:18082` | PASS, latest jar health `UP`; IMAGE fixture run returned `SUCCEEDED` at I60 with 7 stage runs, 3 artifacts, and no plaintext smoke key in responses |
| V20 continuity reference gate | `mvn verify` | PASS, 77 backend tests passed; V20 schema allows `continuityConstraintRefs`, normal fixture/mock providers reference `same subject and scene`, and missing/wrong refs fail V20 without creating artifacts |
| Post-V20-continuity runtime smoke | HTTP against restarted backend `:18082` | PASS, latest jar health `UP`; VIDEO fixture run returned `SUCCEEDED` at V60 with V20 `SUCCEEDED`, 7 stage runs, 3 artifacts, and no plaintext smoke key in responses |
| I00/V00 target lock gate | `mvn verify` | PASS, 82 backend tests passed; I00/V00 declare `GoalAgent` typed partial schemas, structured single-agent output is merged, and goal-lock rubric failures stop the chain before artifacts |
| V20 native voice prompt gate | `mvn verify` | PASS, 84 backend tests passed; V20 `PromptAgent` schema requires `voiceoverRequirement`, ReviewPolicy enforces `HUMAN_VOICE_REQUIRED`, and missing native voice prompt intent stops V20 before artifacts |
| V20 motion prompt reference gate | `mvn verify` | PASS, 86 backend tests passed and JaCoCo coverage check met; V20 `PromptAgent` schema requires `motionPromptRefs`, ReviewPolicy enforces exact `MotionPromptAgent.motionPrompt` reference, and wrong motion refs stop V20 before artifacts |
| V20 character and visual style reference gate | `mvn verify` | PASS, 90 backend tests passed and JaCoCo coverage check met; V20 schema requires `characterContinuityRefs` and `visualStyleRefs`, ReviewPolicy enforces exact character/style constraint references, and wrong refs stop V20 before artifacts |
| I20 prompt variable resolution gate | `mvn verify` | PASS, 94 backend tests passed and JaCoCo coverage check met; I20 schema requires `promptVariables`, image prompt review rejects blank prompt text, missing variables, or unresolved `{{...}}`, and fixture/mock providers return schema-compliant resolved variables |

## Test Specification

| # | What is guaranteed | Test file or command | Type | Result |
|---|---|---|---|---|
| 1 | `IMAGE` chain cannot start without selected free keys | `ChainRunControllerTest.rejectsImageChainWhenSelectedKeysAreMissing` | integration | PASS |
| 2 | `IMAGE` chain runs I00-I60, returns final image/report, and exposes stage retrieval/handoff/node/free-gate/provider ids | `ChainRunControllerTest.runsImageChainThroughFixedStagesWhenFreeFixtureKeysAreSelected` | integration | PASS |
| 3 | `VIDEO` chain runs V00-V60 and forbids old audio/lip-sync stages | `ChainRunControllerTest.runsVideoChainAsCompleteVideoWithVoiceWithoutOldAudioOrEditStages` | integration | PASS |
| 4 | API config lists only chain-specific capability slots | `ApiConfigControllerTest.listsOnlyImageCapabilitySlotsForImageChain` | integration | PASS |
| 5 | Wrong-chain capability configuration is rejected | `ApiConfigControllerTest.rejectsCapabilityConfiguredForWrongChain` | integration | PASS |
| 6 | API key summary includes chain/capability scope, plaintext never appears in response, and selected key deletion is rejected | `ApiConfigControllerTest.neverReturnsPlainApiKeyAndRejectsDeletingSelectedOnlyKey` | integration | PASS |
| 7 | Domain layer has no Spring/HTTP dependency | `DddLayerArchitectureTest` | architecture | PASS |
| 8 | Capability registry omits old audio/edit abilities and rejects local model weight acquisition | `CapabilityControllerTest` | integration/security | PASS |
| 9 | Capability discovery is scoped to the requested chain and stage | `CapabilityControllerTest` | integration | PASS |
| 10 | RAG ingest/retrieve is namespace and stage isolated | `KnowledgeControllerTest` | integration | PASS |
| 11 | Retrieval records are persisted and queryable | `KnowledgeControllerTest` | integration | PASS |
| 12 | Vue3 workbench exposes only image/video modes | `WorkbenchContract.test.ts` | component | PASS |
| 13 | Frontend blocks missing capability configuration and avoids old pipeline APIs | `WorkbenchContract.test.ts` | component | PASS |
| 14 | `/edit` is unsupported and has no export action | `WorkbenchContract.test.ts` | component | PASS |
| 15 | Configured frontend starts image chain via new endpoints | `FrontendFlows.test.ts` | component/API | PASS |
| 16 | Capability panel clears plaintext key after add | `FrontendFlows.test.ts` | component/security | PASS |
| 17 | Workspace and assets render final stage/artifact state | `FrontendFlows.test.ts`, `BranchCoverage.test.ts` | component | PASS |
| 18 | API key is encrypted locally and decryptable only inside backend boundary | `ApiKeyProtectorTest` | unit/security | PASS |
| 19 | Non-fixture provider jobs are posted to configured HTTP adapter without plaintext key | `RoutingProviderHttpGatewayTest.postsNonFixtureProviderToConfiguredHttpAdapterWithoutPlainSecret` | unit/security | PASS |
| 20 | Malformed provider adapter responses are rejected | `RoutingProviderHttpGatewayTest.rejectsMalformedAdapterResponse` | unit/integration | PASS |
| 21 | Non-fixture API key verification goes through `ProviderHttpGateway` and never leaks plaintext | `ApiConfigAdapterVerificationTest` | integration/security | PASS |
| 22 | Sensitive key hash/ciphertext are redacted from `ApiCredential.toString()` | `ApiKeyProtectorTest.credentialStringDoesNotExposeSecretMaterials` | unit/security | PASS |
| 23 | Overlong API key payloads are rejected at the REST boundary | `ApiConfigControllerTest.rejectsOverlongApiKeyPayload` | integration/security | PASS |
| 24 | `POST /api/stage-runs/{stageRunId}:redo` redoes the requested stage and downstream evidence using the startup snapshot | `ChainRunControllerTest.redoesRequestedStageAndDownstreamStagesUsingSnapshotEvidence` | integration | PASS |
| 25 | Workspace `再次生成` calls backend stage redo instead of old provider or pipeline APIs | `FrontendFlows.test.ts` | component/API | PASS |
| 26 | Provider submission persists `ExternalJob` before `AgentNodeRun` and stores status, retry policy, request hash, and metadata without plaintext key | `DefaultAgentNodeFactoryTest.persistsExternalJobImmediatelyAfterProviderSubmissionBeforeAgentNodeRun` | unit/security | PASS |
| 27 | External jobs are queryable through `GET /api/chain-runs/{chainRunId}/external-jobs` without returning plaintext key material | `ChainRunControllerTest.runsImageChainThroughFixedStagesWhenFreeFixtureKeysAreSelected` | integration/security | PASS |
| 28 | Vue3 workspace fetches external jobs and displays masked provider job, status, and retry policy without rendering the full provider job id | `FrontendFlows.test.ts` | component/API/security | PASS |
| 29 | PostgreSQL migration initializes pgvector, RAG, chain, artifact, API config, external job, free gate tables, user-scoped API selection, and deferred FK order for pre-stage evidence writes | `PostgresSchemaMigrationTest` | migration contract | PASS |
| 30 | PostgreSQL profile support serializes JSONB safely and builds a PostgreSQL DataSource from env-backed properties | `PostgresInfrastructureSupportTest` | unit/config | PASS |
| 31 | Configured DashScope text provider calls OpenAI-compatible `/chat/completions` with bearer auth and without API key or masked key in the JSON body | `RoutingProviderHttpGatewayTest.routesDashScopeTextCapabilityToOpenAiCompatibleEndpointWithoutSecretInBody` | unit/security | PASS |
| 32 | Missing selected user key and compatible fallback key does not activate real DashScope text calls or report the provider as available | `RoutingProviderHttpGatewayTest.refusesDashScopeTextProviderWhenNoDashScopeKeyAndNoAdapterConfigured` | unit/security | PASS |
| 33 | Configured DashScope image provider calls multimodal generation with bearer auth and without API key or masked key in the JSON body | `RoutingProviderHttpGatewayTest.routesDashScopeImageCapabilityToMultimodalEndpointWithoutSecretInBody` | unit/security | PASS |
| 34 | Missing selected user key and compatible fallback key does not activate real DashScope image calls or report the provider as available | `RoutingProviderHttpGatewayTest.refusesDashScopeImageProviderWhenNoDashScopeKeyAndNoAdapterConfigured` | unit/security | PASS |
| 35 | Configured DashScope embedding provider calls OpenAI-compatible `/embeddings` with bearer auth and without API key or masked key in the JSON body | `RoutingProviderHttpGatewayTest.routesDashScopeEmbeddingCapabilityToOpenAiCompatibleEndpointWithoutSecretInBody` | unit/security | PASS |
| 36 | Configured DashScope rerank provider calls compatible `/reranks` with bearer auth and without API key or masked key in the JSON body | `RoutingProviderHttpGatewayTest.routesDashScopeRerankCapabilityToCompatibleEndpointWithoutSecretInBody` | unit/security | PASS |
| 37 | Missing selected user key and compatible fallback key does not activate real DashScope embedding calls or report the provider as available | `RoutingProviderHttpGatewayTest.refusesDashScopeEmbeddingProviderWhenNoDashScopeKeyAndNoAdapterConfigured` | unit/security | PASS |
| 38 | Missing selected user key and compatible fallback key does not activate real DashScope rerank calls or report the provider as available | `RoutingProviderHttpGatewayTest.refusesDashScopeRerankProviderWhenNoDashScopeKeyAndNoAdapterConfigured` | unit/security | PASS |
| 39 | Configured DashScope video provider submits async `/video-synthesis`, polls `/tasks/{task_id}`, and never puts API key or masked key in the JSON body | `RoutingProviderHttpGatewayTest.routesDashScopeVideoCapabilityToAsyncVideoEndpointWithoutSecretInBody` | unit/security | PASS |
| 40 | Missing selected user key and compatible fallback key does not activate real DashScope video calls or report the provider as available | `RoutingProviderHttpGatewayTest.refusesDashScopeVideoProviderWhenNoDashScopeKeyAndNoAdapterConfigured` | unit/security | PASS |
| 41 | DashScope adapter routes with selected user key when service fallback env key is blank and never puts that key in the JSON body | `RoutingProviderHttpGatewayTest.routesDashScopeTextCapabilityWithSelectedUserSecretWhenEnvironmentKeyIsBlank` | unit/security | PASS |
| 42 | Stage `handoffContextId` is a retrievable RAG `NEXT_STAGE_CONTEXT` chunk whose JSON has schemaVersion, chainRunId, chainType, fromStage, toStage, reviewReportId, evidenceChunkIds, critical claims, citationChunkIds, and evidenceCheck | `ChainRunControllerTest.writesSchemaCompliantNextStageContextToRagForEachStage` | integration/RAG | PASS |
| 43 | Chain start rechecks every selected credential is `ACTIVE`, recently verified and `FreeModelGateStatus.PASSED`; drifted failed gate returns `FREE_MODEL_GATE_FAILED` before provider calls | `ChainRunControllerTest.rejectsChainStartWhenSelectedKeyNoLongerPassesFreeModelGate` | integration/security | PASS |
| 44 | Provider `FAILED` or free quota exhausted responses are persisted as failed external jobs and failed agent nodes instead of successful node output | `DefaultAgentNodeFactoryTest.marksAgentNodeFailedWhenProviderReportsFreeQuotaExhausted` | unit/security | PASS |
| 45 | Chain execution stops at the first failed provider stage, returns `WAITING_CAPABILITY`, exposes the provider summary as `blockingReason`, and does not create downstream stages or final artifacts | `ChainRunProviderFailureControllerTest.returnsWaitingCapabilityWhenProviderReportsFreeQuotaExhausted` | integration/security | PASS |
| 46 | PostgreSQL profile runs Flyway against a real PostgreSQL 16 + pgvector database and persists chain, stage, external job, and API selection snapshot evidence through repository adapters | Docker `pgvector/pgvector:pg16` + HTTP smoke on `:18081` | runtime/integration | PASS |
| 47 | VIDEO V40 requires provider metadata proving 10 seconds, 9:16, and native human voice support; otherwise the chain stops at `WAITING_CAPABILITY` and creates no final artifact | `ChainRunVideoCapabilityGateControllerTest.blocksVideoChainWhenProviderDoesNotProveNativeHumanVoiceCapability` | integration/security | PASS |
| 48 | DashScope video API key verification does not submit real generation and rejects `video.generate.full_with_voice.free` when native human voice support is not proven | `RoutingProviderHttpGatewayTest.dashScopeVideoVerifyDoesNotSubmitGenerationWhenNativeHumanVoiceIsUnsupported`, `ApiConfigAdapterVerificationTest.rejectsVideoKeyVerificationWhenProviderDoesNotProveNativeHumanVoiceCapability` | unit/integration/security | PASS |
| 49 | IMAGE I50 consumes review metadata, records the first failed review when `finalScore < 85`, `safetyScore != 100`, or artifact integrity is not perfect, reruns I40/I50, and can still deliver when the second review passes | `ChainRunQualityGateControllerTest.retriesImageGenerationWhenFirstImageReviewFailsAndThenDelivers` | integration/quality | PASS |
| 50 | VIDEO V50 consumes review metadata, records repeated failed reviews when decode integrity, safety, short-drama score, or audible human voice evidence fails, then returns to V30 `WAITING_USER` with no final artifact when no alternative full-video provider exists | `ChainRunQualityGateControllerTest.returnsVideoToWaitingUserWhenVoiceReviewFailsTwiceAndNoAlternativeProviderExists` | integration/quality | PASS |
| 51 | Recovery V30 redo refreshes current selected credentials after the user reselects a full-video provider, appends a new API selection snapshot, and continues with the new provider instead of the stale startup snapshot | `ChainRunQualityGateControllerTest.redoesVideoRecoveryPreflightWithReselectedProviderSnapshot` | integration/recovery | PASS |
| 52 | Vue3 workspace retry chooses the latest waiting recovery stage for `WAITING_USER` chains and does not call the stale V40 redo endpoint | `FrontendFlows.test.ts` | component/API | PASS |
| 53 | EvidenceCheck rejects unsupported critical claims and passes fully cited critical claims with schema compliance 100 | `EvidenceCheckPolicyTest` | unit/domain | PASS |
| 54 | RAG retrieve response and retrieval-record detail report required context coverage for non-initial stages | `KnowledgeControllerTest.reportsRequiredCoverageForNonInitialStageEvidence` | integration/RAG | PASS |
| 55 | Next-stage RAG retrieval includes previous handoff and previous ReviewReport chunks even when the query matches current chain context | `ChainRunControllerTest.retrievesPreviousHandoffAndReviewReportForNextStageRagContext` | integration/RAG | PASS |
| 56 | Vue3 creation sidebar resizer supports pointer drag and clamps to the documented min/max width | `ComponentInteractions.test.ts` | component/accessibility | PASS |
| 57 | Asset preview modal stays open for non-Escape keys and closes on Escape | `ComponentInteractions.test.ts` | component/accessibility | PASS |
| 58 | Workspace `重新编辑` opens an inline user-goal editor with cancel/confirm and confirm restarts via new chain-run endpoints, not old pipeline APIs | `FrontendFlows.test.ts` | component/API | PASS |
| 59 | Prompt composer ignores Enter during IME composition and submits on normal Enter | `ComponentInteractions.test.ts` | component/accessibility | PASS |
| 60 | Workspace maps documented chain statuses to distinct user-facing copy instead of one generic executing message | `BranchCoverage.test.ts` | component/status | PASS |
| 61 | `WAITING_CAPABILITY` workspace state opens the current chain-scoped capability config panel from `去配置` | `BranchCoverage.test.ts` | component/status | PASS |
| 62 | Final video artifact preview keeps modal playback speed controls and updates the video playback rate | `BranchCoverage.test.ts` | component/assets | PASS |
| 63 | Playwright verifies key frontend acceptance flows on desktop and mobile Chromium without a real backend | `e2e/workbench.spec.ts` | e2e | PASS |
| 64 | API key plaintext input is scoped to an add-key dialog, optional model is submitted, and plaintext is cleared after submission | `FrontendFlows.test.ts` | component/security | PASS |
| 65 | VIDEO mode starts generation through `video-chain-runs` with the user goal and never calls image or legacy pipeline endpoints | `FrontendFlows.test.ts` | component/API | PASS |
| 66 | API config key rows render every configured key field and selected marker required by frontend docs and backend API policy | `ComponentInteractions.test.ts` | component/security | PASS |
| 67 | Repeated video quality failures automatically switch to an alternative verified free full-video provider before asking the user | `ChainRunQualityGateControllerTest.autoSelectsAlternativeVideoProviderAfterRepeatedQualityFailure` | integration/recovery | PASS |
| 68 | Generate page handles `FREE_MODEL_GATE_FAILED` as blocking UI instead of an unhandled rejection or workspace navigation | `FrontendFlows.test.ts` | component/security | PASS |
| 69 | The generate composer exposes the documented visible `能力配置` chip before any blocking state occurs | `WorkbenchContract.test.ts` | component/contract | PASS |
| 70 | Workspace cancel action calls the documented chain cancel API and updates the UI to `CANCELLED` | `FrontendFlows.test.ts` | component/API | PASS |
| 71 | Workspace chain detail fetches and renders API selection snapshots without exposing plaintext key material | `FrontendFlows.test.ts` | component/API/security | PASS |
| 72 | Capability panel surfaces selected-key delete rejection as safe user-facing UI instead of an unhandled promise | `ComponentInteractions.test.ts` | component/security | PASS |
| 73 | Project API returns recent projects for the Vue3 session sidebar | `ProjectControllerTest` | integration | PASS |
| 74 | Creation sidebar loads and deduplicates recent project history by title or goal | `ComponentInteractions.test.ts` | component/API | PASS |
| 75 | Workspace empty loading state uses the documented image skeleton rather than a spinner or empty message | `BranchCoverage.test.ts` | component/visual-state | PASS |
| 76 | Workspace result strip renders no more than 4 artifact cards | `BranchCoverage.test.ts` | component/visual-state | PASS |
| 77 | Succeeded workspace renders suggestion actions and bottom secondary actions without falling back to old frontend-only flows | `FrontendFlows.test.ts` | component/workflow | PASS |
| 78 | Succeeded IMAGE and VIDEO workspaces render delivery summaries from chain artifacts, review reports, and masked provider jobs | `FrontendFlows.test.ts` | component/workflow/security | PASS |
| 79 | Successful IMAGE and VIDEO chains expose candidate artifact sets with documented candidate counts before review reports | `ChainRunControllerTest`, `ChainRunQualityGateControllerTest` | integration/workflow | PASS |
| 80 | Fixed IMAGE and VIDEO stage catalogs declare schema, rubric, retrieval policy, collaboration mode, and capability metadata for every stage | `StageCatalogContractTest` | domain/architecture | PASS |
| 81 | Asset library preview renders final images as images and review reports as structured summaries, while video controls stay scoped to final video artifacts | `FrontendFlows.test.ts`, `BranchCoverage.test.ts` | component/assets | PASS |
| 82 | RAG coverage does not allow a goal chunk to masquerade as stage map/current stage, and chain stages retrieve explicit goal, stage map, current stage, handoff, and review evidence | `KnowledgeControllerTest`, `ChainRunControllerTest` | integration/RAG | PASS |
| 83 | RAG private namespaces follow the documented project-chain shape and reject malformed or cross-chain private namespace access | `KnowledgeControllerTest`, `ChainRunControllerTest` | integration/RAG/security | PASS |
| 84 | Agent/provider requests receive a compressed EvidencePack rather than only raw user goal and stage metadata | `ChainRunEvidencePackControllerTest` | integration/RAG/harness | PASS |
| 85 | Chain-private RAG retrieval includes allowed `global:public` and `project:{projectId}` knowledge but excludes other private chain context | `KnowledgeControllerTest` | integration/RAG/security | PASS |
| 86 | RAG retrieval rejects unresolved same-field evidence conflicts before provider/agent execution | `KnowledgeControllerTest.rejectsRetrievalWhenEvidenceChunksHaveUnresolvedFieldConflicts` | integration/RAG/security | PASS |
| 87 | Chain execution preserves unresolved RAG evidence conflicts as `WAITING_REVIEW` without calling provider/agent, and Vue3 shows the blocking reason | `ChainRunControllerTest.waitsForReviewWhenChainRagEvidenceHasUnresolvedConflicts`, `BranchCoverage.test.ts` | integration/component/RAG | PASS |
| 88 | Fixed IMAGE/VIDEO stages declare and execute the exact documented agent roles; provider requests expose `nodeName` and `StageRun` aggregates all role node evidence | `StageCatalogContractTest.stageContractsDeclareDocumentedAgentRoles`, `ChainRunEvidencePackControllerTest.sendsCompressedEvidencePackToEveryAgentProviderRequest`, `ChainRunControllerTest.runsImageChainThroughFixedStagesWhenFreeFixtureKeysAreSelected` | domain/integration/harness | PASS |
| 89 | Agent/provider requests carry the documented `StageInputContext`, including stage identity and RAG-derived goal/handoff/review refs for non-initial stages | `ChainRunEvidencePackControllerTest.sendsCompressedEvidencePackToEveryAgentProviderRequest` | integration/RAG/harness | PASS |
| 90 | `DIVIDE_AND_MERGE` stages use a deterministic `StageCoordinator` with documented priority instead of LLM guessing, and handoff context includes the merged stage output plus conflict resolutions | `StageCoordinatorTest`, `StageCatalogContractTest`, `ChainRunEvidencePackControllerTest` | domain/integration/harness | PASS |
| 91 | Typed partial schema validation rejects missing required fields and unknown fields before merge, and all divide-and-merge stages declare per-agent schemas | `StageCoordinatorTest`, `StageCatalogContractTest` | domain/schema/harness | PASS |
| 92 | Prompt-pack stages honor `PromptSafetyAgent` veto: I20/V20 review fails on `safetyPassed=false`, I20 stops the chain as `FAILED`, and no final artifact is produced | `StageReviewPolicyTest`, `ChainRunQualityGateControllerTest.failsImagePromptPackWhenPromptSafetyAgentVetoes` | domain/integration/security | PASS |
| 93 | V20 prompt-pack review requires `PromptAgent` to reference `ContinuityAgent` output through `continuityConstraintRefs`; missing or wrong refs stop the chain at V20 with no artifacts | `StageReviewPolicyTest`, `ChainRunQualityGateControllerTest.failsVideoPromptPackWhenPromptMissesContinuityReference` | domain/integration/schema | PASS |
| 94 | I00/V00 goal-lock stages require `GoalAgent` structured fields plus fixed count, duration, aspect ratio, human-voice, clarity, and safety rubric checks; failures stop at the goal stage before artifacts | `StageReviewPolicyTest`, `StageCoordinatorTest`, `ChainRunQualityGateControllerTest.failsImageGoalLockWhenGoalAgentMissesStructuredScene` | domain/integration/schema | PASS |
| 95 | V20 full-video prompt packs must carry native human voice intent through `voiceoverRequirement=HUMAN_VOICE_REQUIRED`; missing native voice intent fails V20 and prevents generation artifacts | `StageReviewPolicyTest`, `ChainRunQualityGateControllerTest.failsVideoPromptPackWhenPromptMissesNativeVoiceRequirement` | domain/integration/schema | PASS |
| 96 | V20 full-video prompt packs must reference the generated motion prompt through `motionPromptRefs`; wrong refs fail V20 and prevent generation artifacts | `StageReviewPolicyTest`, `ChainRunQualityGateControllerTest.failsVideoPromptPackWhenPromptMissesMotionReference` | domain/integration/schema | PASS |
| 97 | V20 full-video prompt packs must reference character continuity and visual style constraints through structured refs; wrong refs fail V20 and prevent generation artifacts | `StageReviewPolicyTest`, `ChainRunQualityGateControllerTest.failsVideoPromptPackWhenPromptMissesCharacterContinuityReference`, `ChainRunQualityGateControllerTest.failsVideoPromptPackWhenPromptMissesVisualStyleReference` | domain/integration/schema | PASS |
| 98 | I20 image prompt packs must provide nonblank `positivePrompt`, resolved `promptVariables`, and no unresolved `{{...}}` placeholder; blank, missing, or unresolved fields fail I20 and prevent generation artifacts | `StageReviewPolicyTest`, `ChainRunQualityGateControllerTest.failsImagePromptPackWhenPromptVariablesAreMissing` | domain/integration/schema | PASS |

## Known Gaps

- Real DashScope OpenAI-compatible text LLM, embedding, rerank, synchronous image, and async text-to-video HTTP adapters exist for `llm.text.free`, `rag.embedding.free`, `rag.rerank.free`, `image.generate.free`, and `video.generate.full_with_voice.free`; DashScope async text-to-video is rejected for the full-with-voice gate unless provider metadata proves native human voice support, and video audio-driving is only marked when request input carries an audio URL.
- PostgreSQL + pgvector migration and project/chain/artifact/RAG/API config/agent node/external job adapters exist under the `postgres` profile and passed a Docker `pgvector/pgvector:pg16` runtime smoke; this does not replace production database backup, HA, or load testing.
- Fixture HTTP adapter proves API, state-machine, node-run, provider-job, and FreeModelGate contracts only; configurable HTTP routing proves the backend adapter boundary, not real media generation.
