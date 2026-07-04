# Observability Runbook

每次运行至少需要这些可追踪 ID：

- `traceId`
- `projectId`
- `chainRunId`
- `stageRunId`
- `retrievalRecordId`
- `artifactId`
- `freeModelGateId`
- `apiSelectionSnapshotId`

排障顺序：

1. 查 `chainRunId` 的当前状态和阻塞原因。
2. 查当前 `stageRunId` 的 `ReviewReport`。
3. 查 `retrievalRecordId` 确认 RAG namespace 和命中证据。
4. 查 `apiSelectionSnapshotId` 确认 selected key 和 FreeModelGate。
5. 查 artifact metadata 和 hash，不读取密钥或敏感原始响应。

