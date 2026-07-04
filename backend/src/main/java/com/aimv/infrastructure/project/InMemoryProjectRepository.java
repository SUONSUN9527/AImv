package com.aimv.infrastructure.project;

import com.aimv.domain.project.CreativeProject;
import com.aimv.domain.project.ProjectRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!postgres")
public class InMemoryProjectRepository implements ProjectRepository {

    private final Map<String, CreativeProject> projects = new ConcurrentHashMap<>();

    @Override
    public CreativeProject save(CreativeProject project) {
        projects.put(project.projectId(), project);
        return project;
    }

    @Override
    public Optional<CreativeProject> findById(String projectId) {
        return Optional.ofNullable(projects.get(projectId));
    }

    @Override
    public List<CreativeProject> findRecent(int limit) {
        return projects.values().stream()
            .sorted(Comparator.comparing(CreativeProject::createdAt).reversed())
            .limit(limit)
            .toList();
    }
}
