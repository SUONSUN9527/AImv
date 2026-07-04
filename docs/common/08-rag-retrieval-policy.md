# RAG Retrieval Policy

RAG namespace 规则：

- 图片链路和视频链路必须使用独立 namespace。
- 检索请求必须同时指定 `namespace`、`chainType`、`stageCode`。
- 私有上下文不得跨链路读取。
- 公共文档只能以只读公共证据进入 `EvidencePack`。

每次检索必须生成 `RetrievalRecord`，至少记录：

- query
- namespace
- chainType
- stageCode
- hit chunk ids
- coverage
- passed
- createdAt

检索响应和 `RetrievalRecord` 详情必须返回同一份 `coverage`；PostgreSQL profile 必须把它写入
`retrieval_record.coverage_json`，并把 `coverage.passed` 同步写入 `retrieval_record.passed`：

- `goal`：命中当前目标或链路上下文。
- `stageMap`：命中当前阶段上下文。
- `currentStage`：命中当前阶段上下文。
- `previousHandoff`：非首阶段必须命中上一阶段 `NEXT_STAGE_CONTEXT`。
- `previousReviewReport`：非首阶段必须命中上一阶段 `REVIEW_REPORT`。
- `passed`：首阶段必须覆盖 goal/currentStage；非首阶段必须额外覆盖
  previousHandoff/previousReviewReport。

进入模型 prompt 前必须压缩为阶段需要的 `EvidencePack`，不能把完整历史直接传入模型。
