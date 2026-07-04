# Harness Protocol

每个阶段必须按固定顺序执行：

1. 加载 `CreativeProject`、`ChainRun`、当前 `StageRun`。
2. 按 namespace 和 stageCode 检索 RAG 证据。
3. 审核上一阶段输出和上一阶段 `ReviewReport`。
4. 发现本阶段所需能力并检查 selected key。
5. 通过 `FreeModelGate` 后调用云端 HTTP adapter。
6. 生成结构化阶段输出。
7. 执行量化 `ReviewReport`。
8. 生成 `NextStageContext`。
9. 写入 RAG 和可追踪运行日志。

任一步失败时必须返回明确阻塞原因，不能静默降级到旧链路、付费模型或本地模型。

