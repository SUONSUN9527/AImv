# Stage Rubrics

所有阶段评分采用 0-100 分。未达到阶段最低标准时必须重做或阻塞。

| Stage Group | Required Score |
| --- | --- |
| 目标锁定 | clarity >= 90, safety = 100 |
| 方案生成 | fieldCompleteness >= 90, constraintCoverage = 100 |
| Prompt 包 | schemaValidity = 100, safetyVeto = false |
| 能力预检 | capabilityAvailable = 100, FreeModelGate.passed = true |
| 生成 | artifactIntegrity = 100, providerJobId exists |
| 质量评审 | finalScore >= 85, safety = 100 |
| 验收交付 | userGoalMatch >= 95, evidenceComplete = 100 |

视频最终验收还必须满足：9.5-10.5 秒、9:16、存在可解码音频轨、可听清人声配音。

当前后端质量门禁从评审 agent 的 provider metadata 生成 `ReviewReport`：

- I50 图片质量评审要求 `finalScore >= 85`、`safetyScore = 100`、
  `artifactIntegrityScore = 100`。
- V50 视频质量评审要求 `finalScore >= 85`、`decodeIntegrityScore = 100`、
  `safetyScore = 100`、`shortDramaScore >= 90`、`humanVoiceAudible = true`。

首次质量门禁失败时，当前 I50/V50 `StageRun` 记录为 `FAILED`，不写下一阶段
`NextStageContext`，并自动重做对应生成阶段 I40/V40 及其质量评审 I50/V50。
如果第二次质量评审通过，链路继续进入 I60/V60 并生成最终 artifact。

连续两次质量门禁失败时，后端追加 I30/V30 恢复阶段，要求重新选择 provider；
此时不生成最终 artifact，也不伪造下一阶段 handoff。图片链路进入 `WAITING_CAPABILITY`；
视频链路在当前快照没有其他完整视频 provider 时进入 `WAITING_USER`。

用户重新选择 provider 后重做 I30/V30 恢复阶段时，后端刷新当前 selected key 并追加新的
`ApiSelectionSnapshot`；普通阶段重做仍使用链路启动快照，保证常规重做可复现。
