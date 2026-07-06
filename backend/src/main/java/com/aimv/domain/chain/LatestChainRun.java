package com.aimv.domain.chain;

/**
 * 项目下「最新一次链路」的轻量快照：只带 id 与状态，供历史侧边栏直达链路并展示真实状态，
 * 避免前端仅凭本地缓存（localStorage）判断状态而在链路已完成后仍显示「生成中」。
 */
public record LatestChainRun(String chainRunId, String status) {
}
