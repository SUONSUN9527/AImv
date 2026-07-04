# Context Schema

## NextStageContext

```json
{
  "schemaVersion": "1.0",
  "chainRunId": "chain-id",
  "chainType": "IMAGE",
  "fromStage": "I20",
  "toStage": "I30",
  "outputArtifactIds": [],
  "reviewReportId": "review-id",
  "evidenceChunkIds": ["chunk-001"],
  "claims": [
    {
      "claim": "I20 to I30 handoff is backed by retrieved RAG chunks",
      "critical": true,
      "citationChunkIds": ["chunk-001"],
      "deterministicSourceRefs": ["KnowledgeApplicationService.retrieve"],
      "supported": true
    }
  ],
  "evidenceCheck": {
    "passed": true,
    "groundednessScore": 100,
    "supportedClaims": 1,
    "totalClaims": 1,
    "unsupportedCriticalClaims": 0,
    "schemaCompliance": 100,
    "unsupportedClaims": []
  },
  "assumptions": []
}
```

`handoffContextId` 必须等于写入 RAG 的 `NEXT_STAGE_CONTEXT` chunk id。非最终阶段的
`NEXT_STAGE_CONTEXT` 按 `toStage` 绑定 `stageCode`，这样下一阶段检索时可以继承上一阶段
handoff；最终阶段使用当前阶段 `stageCode`。`sourceId` 必须是产出该 handoff 的
`stageRunId`。写入前必须构造固定字段的 `NextStageContext` 值对象，禁止把 agent 完整对话、
API key 明文或 provider 原始响应透传进 handoff。

写入 `NEXT_STAGE_CONTEXT` 前必须执行确定性的 `EvidenceCheckPolicy`。所有 critical claim
必须包含 `citationChunkIds` 或确定性来源引用，`groundednessScore` 必须大于等于 95，
`unsupportedCriticalClaims` 必须等于 0，`schemaCompliance` 必须等于 100；否则禁止写入下一阶段
handoff。

## ReviewReport

```json
{
  "passed": true,
  "score": 95,
  "rubricVersion": "image-I20.v1",
  "summary": "schema valid and safe",
  "evidenceChunkIds": []
}
```

事实字段必须可追踪到 RAG chunk、用户输入或 provider 原始元数据；无法证明的内容只能进入 `assumptions`。
