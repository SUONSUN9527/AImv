package com.aimv.domain.artifact;

import java.util.List;

public interface ArtifactRepository {

    void saveAll(List<Artifact> artifacts);

    void deleteByChainRunId(String chainRunId);

    List<Artifact> findByChainRunId(String chainRunId);

    List<Artifact> findAll();
}
