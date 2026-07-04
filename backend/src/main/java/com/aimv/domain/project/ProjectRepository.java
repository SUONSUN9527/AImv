package com.aimv.domain.project;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository {

    CreativeProject save(CreativeProject project);

    Optional<CreativeProject> findById(String projectId);

    List<CreativeProject> findRecent(int limit);
}
