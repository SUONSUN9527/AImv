# ADR 0001: Vue3 And Java Separated Rewrite

## Status

Accepted

## Decision

AImv 前后端分离重写：

- 后端继续复用包名 `com.aimv`。
- 前端使用 Vue3、TypeScript、Vite、Vue Router、Pinia。
- 后端使用 Spring Boot、DDD 分层和统一 API envelope。
- 只保留 `IMAGE` 和 `VIDEO` 两条 chain run。
- 剪辑、导出、独立音频、音乐生成、口型同步暂不开放。

## Consequences

旧 React 入口、旧 pipeline API 和旧剪辑/音频链路不进入新主链路。真实云端生成能力后续必须通过 HTTP adapter 和 `FreeModelGate` 接入。

