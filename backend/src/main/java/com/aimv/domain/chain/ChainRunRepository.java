package com.aimv.domain.chain;

import java.util.Map;
import java.util.Optional;

public interface ChainRunRepository {

    ChainRun save(ChainRun chainRun);

    Optional<ChainRun> findById(String chainRunId);

    Optional<ChainRun> findByStageRunId(String stageRunId);

    /**
     * 返回「项目ID → 该项目下最新一次链路（id + 状态）」的映射（一次查询批量取回，避免 N+1）。
     * 供前端历史侧边栏把「项目」历史项变成可点击、直达对应 workspace 链路，
     * 并展示服务端真实状态（链路完成后不再一直转圈圈）。
     */
    Map<String, LatestChainRun> latestChainRunByProject();
}
