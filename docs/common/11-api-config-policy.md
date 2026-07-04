# API Config Policy

API 配置必须按 `(userId, chainType, capabilityType)` 隔离。

当前本地单用户实现按 `(chainType, capabilityType)` 隔离，后续接入用户系统时必须补充 `userId`。

规则：

- 每个能力条目可保存多个 key。
- selected key 只能在同一链路同一能力内生效。
- 设为 selected 前必须通过 provider 连通性和 `FreeModelGate`。
- 链路启动时必须冻结 `ApiSelectionSnapshot`。
- 链路启动时必须重新检查 selected key 仍为 `ACTIVE`、存在 `lastVerifiedAt`，且
  `FreeModelGateStatus=PASSED`；状态漂移或免费门禁失败时返回 `FREE_MODEL_GATE_FAILED`，
  不进入任何 stage，也不调用 provider。
- 缺少 selected key 时返回 `API_CAPABILITY_NOT_CONFIGURED`。
- 删除 selected key 必须拒绝，除非先选择另一个可用 key。

当前本地实现会保存 key 的 hash、masked key 和进程内 AES-GCM 加密密文。密钥不会进入前端状态、REST 响应或 provider adapter 请求。

进程内加密只适合本地闭环验证；生产环境必须替换为稳定的密钥管理服务或数据库字段级加密，并补充密钥轮换流程。
