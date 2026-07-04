# Provider HTTP Contract

核心工程只能调用 HTTP adapter，不直接依赖云厂商 SDK。

默认非 `fixture-free` provider 会调用外部 HTTP adapter。当前内置的 DashScope 文本、RAG、
图片和视频 adapter 也是基础设施层 HTTP adapter。

DashScope 内置 adapter 必须优先使用链路启动时冻结的 `apiKeyId` 解析用户 selected key；
`AIMV_DASHSCOPE_API_KEY` 只作为服务级兼容 fallback。后端只能在基础设施层解密 selected key 并
放入 `Authorization: Bearer ...` 请求头，不得把明文 key 写入 `ProviderHttpRequest`、
`providerMetadata`、RAG、ReviewReport、TDD 证据、日志或接口响应。

DashScope 文本 adapter 只在以下条件同时满足时启用：

- `apiKeyId` 可解析到用户 selected key，或配置了服务级兼容 `AIMV_DASHSCOPE_API_KEY`
- `capabilityType=llm.text.free`
- `provider` 包含 `dashscope`

该内置 adapter 调用 OpenAI 兼容 `POST {baseUrl}/chat/completions`，API key 只放在
`Authorization: Bearer ...` 请求头，发给 DashScope 的请求体只包含 `model`、`messages` 和
`stream=false`，不会包含 `apiKeyId`、`maskedKey` 或 API key 明文。缺少 selected key 且未配置
fallback key 时不会启用真实调用，仍按普通非 fixture provider 路由到外部 adapter 或返回 adapter
未配置错误。

DashScope 图片 adapter 只在以下条件同时满足时启用：

- `apiKeyId` 可解析到用户 selected key，或配置了服务级兼容 `AIMV_DASHSCOPE_API_KEY`
- `capabilityType=image.generate.free`
- `provider` 包含 `dashscope`

该内置 adapter 调用 `POST {apiBaseUrl}/services/aigc/multimodal-generation/generation`，API key
只放在 `Authorization: Bearer ...` 请求头，请求体包含 `model`、`input.messages` 和
`parameters`，不会包含 `apiKeyId`、`maskedKey` 或 API key 明文。成功响应中的图片 URL 只写入
`artifactRefs`，provider 原始响应不会穿透到接口层。

DashScope embedding adapter 只在以下条件同时满足时启用：

- `apiKeyId` 可解析到用户 selected key，或配置了服务级兼容 `AIMV_DASHSCOPE_API_KEY`
- `capabilityType=rag.embedding.free`
- `provider` 包含 `dashscope`

该内置 adapter 调用 OpenAI 兼容 `POST {baseUrl}/embeddings`，API key 只放在
`Authorization: Bearer ...` 请求头，请求体包含 `model`、`input`、`dimensions` 和
`encoding_format=float`。响应只记录向量数量、维度、模型和 usage，不把 embedding 向量写回
`providerMetadata`。

DashScope rerank adapter 只在以下条件同时满足时启用：

- `apiKeyId` 可解析到用户 selected key，或配置了服务级兼容 `AIMV_DASHSCOPE_API_KEY`
- `capabilityType=rag.rerank.free`
- `provider` 包含 `dashscope`

该内置 adapter 调用兼容模式 `POST {baseUrl}/reranks`，API key 只放在
`Authorization: Bearer ...` 请求头，请求体包含 `model`、`query`、`documents`、`top_n` 和
`instruct`，不会包含 `apiKeyId`、`maskedKey` 或 API key 明文。

DashScope 视频 adapter 只在以下条件同时满足时启用：

- `apiKeyId` 可解析到用户 selected key，或配置了服务级兼容 `AIMV_DASHSCOPE_API_KEY`
- `capabilityType=video.generate.full_with_voice.free`
- `provider` 包含 `dashscope`

该内置 adapter 调用 `POST {apiBaseUrl}/services/aigc/video-generation/video-synthesis`，并设置
`X-DashScope-Async: enable`。API key 只放在 `Authorization: Bearer ...` 请求头，请求体包含
`model`、`input.prompt` 和 `parameters`。当节点输入包含 `audioUrl`、`audio_url`、
`drivingAudioUrl` 或 `driving_audio_url` 时，才会向 DashScope 传 `input.audio_url` 并在
`providerMetadata.audioDriven` 标记为 `true`；否则只提交文本驱动视频任务，不虚报驱动音频证据。
该 adapter 默认只提交任务并返回 `PENDING`，配置 `AIMV_DASHSCOPE_VIDEO_POLL_ATTEMPTS` 后会短轮询
`GET {apiBaseUrl}/tasks/{task_id}`，成功时把 `video_url` 写入 `artifactRefs`。

`video.generate.full_with_voice.free` 的门禁要求 provider metadata 同时证明：

- `completeShortVideoSupported=true`
- `nativeHumanVoiceSupported=true`
- `durationSeconds=10`
- `aspectRatio=9:16`

DashScope 当前内置 async video adapter 只能证明异步视频合成路径，不证明原生人声配音能力。因此
API key verify 请求只返回本地 `FAILED` 能力证据，不会提交真实视频生成任务；链路执行时如果 provider
返回成功但 metadata 没有上述能力证据，应用层必须停在 V40 并返回 `WAITING_CAPABILITY`，不得生成最终
视频 artifact。

所有 adapter 请求必须包含：

- `traceId`
- `chainRunId`
- `stageRunId`
- `stageCode`
- `nodeRunId`
- `nodeName`
- `capabilityType`
- `provider`
- `model`
- `freeModelGateId`
- `apiKeyId`
- `maskedKey`
- `input`

请求体不得包含 API key 明文。云端 adapter 只能使用 `apiKeyId`、`maskedKey`、后端授权上下文或外部密钥管理机制定位凭据。

所有 adapter 响应必须包含：

- `providerJobId`
- `status`
- `artifactRefs`
- `providerMetadata`
- `freeQuotaSnapshot`
- `rawErrorCode`

响应体不得包含 API key 明文、provider 原始敏感报文或计费凭据。

当 adapter 返回 `FAILED`、免费额度耗尽或其他不可继续状态时，应用层必须把 `ExternalJob` 和
`AgentNodeRun` 都记录为失败，链路停在当前 `StageRun` 并返回 `WAITING_CAPABILITY`。失败阶段只
暴露脱敏后的 provider 摘要作为 `blockingReason`，不得继续执行下游阶段，也不得生成最终
`Artifact`。

DashScope OpenAI 兼容 Chat 响应不返回免费额度余额。内置 adapter 会把
`freeQuotaSnapshot` 记录为 `dashscope-openai-compatible:quota-not-returned`，并在
`providerMetadata.quotaSource` 标记为 `not_returned_by_api`，不伪造免费 quota 证据。

DashScope 图片同步响应同样不返回免费额度余额。内置 adapter 会把 `freeQuotaSnapshot` 记录为
`dashscope-image-sync:quota-not-returned`，并在 `providerMetadata.quotaSource` 标记为
`not_returned_by_api`。

DashScope embedding/rerank 响应同样不返回免费额度余额。内置 adapter 分别把
`freeQuotaSnapshot` 记录为 `dashscope-embedding:quota-not-returned` 和
`dashscope-rerank:quota-not-returned`，并在 `providerMetadata.quotaSource` 标记为
`not_returned_by_api`。

DashScope 视频异步任务响应同样不返回免费额度余额。内置 adapter 把 `freeQuotaSnapshot` 记录为
`dashscope-video-async:quota-not-returned`，并在 `providerMetadata.quotaSource` 标记为
`not_returned_by_api`。
