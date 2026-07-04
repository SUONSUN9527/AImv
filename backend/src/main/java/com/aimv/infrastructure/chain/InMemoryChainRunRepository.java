package com.aimv.infrastructure.chain;

import com.aimv.domain.chain.ChainRun;
import com.aimv.domain.chain.ChainRunRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!postgres")
public class InMemoryChainRunRepository implements ChainRunRepository {

    private final Map<String, ChainRun> chainRuns = new ConcurrentHashMap<>();

    @Override
    public ChainRun save(ChainRun chainRun) {
        chainRuns.put(chainRun.chainRunId(), chainRun);
        return chainRun;
    }

    @Override
    public Optional<ChainRun> findById(String chainRunId) {
        return Optional.ofNullable(chainRuns.get(chainRunId));
    }

    @Override
    public Optional<ChainRun> findByStageRunId(String stageRunId) {
        return chainRuns.values().stream()
            .filter(chainRun -> chainRun.stageRuns().stream()
                .anyMatch(stageRun -> stageRun.stageRunId().equals(stageRunId)))
            .findFirst();
    }

    @Override
    public Map<String, String> latestChainRunIdByProject() {
        // 每个项目保留 createdAt 最新的一条链路，再映射成 projectId → chainRunId。
        Map<String, ChainRun> latestPerProject = new HashMap<>();
        for (ChainRun chainRun : chainRuns.values()) {
            latestPerProject.merge(chainRun.projectId(), chainRun,
                (existing, candidate) -> candidate.createdAt().isAfter(existing.createdAt()) ? candidate : existing);
        }
        Map<String, String> result = new HashMap<>();
        latestPerProject.forEach((projectId, chainRun) -> result.put(projectId, chainRun.chainRunId()));
        return result;
    }
}
