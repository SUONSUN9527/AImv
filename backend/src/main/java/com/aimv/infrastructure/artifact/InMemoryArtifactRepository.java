package com.aimv.infrastructure.artifact;

import com.aimv.domain.artifact.Artifact;
import com.aimv.domain.artifact.ArtifactRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!postgres")
public class InMemoryArtifactRepository implements ArtifactRepository {

    private final Map<String, Artifact> artifacts = new ConcurrentHashMap<>();

    @Override
    public void saveAll(List<Artifact> newArtifacts) {
        newArtifacts.forEach(artifact -> artifacts.put(artifact.artifactId(), artifact));
    }

    @Override
    public void deleteByChainRunId(String chainRunId) {
        artifacts.values().removeIf(artifact -> artifact.chainRunId().equals(chainRunId));
    }

    @Override
    public List<Artifact> findByChainRunId(String chainRunId) {
        return artifacts.values().stream()
            .filter(artifact -> artifact.chainRunId().equals(chainRunId))
            .toList();
    }

    @Override
    public List<Artifact> findAll() {
        return new ArrayList<>(artifacts.values());
    }
}
