package com.aimv.infrastructure.project;

import com.aimv.domain.project.CreativeProject;
import com.aimv.domain.project.ProjectRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("postgres")
public class PostgresProjectRepository implements ProjectRepository {

    private final JdbcTemplate jdbcTemplate;

    public PostgresProjectRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public CreativeProject save(CreativeProject project) {
        jdbcTemplate.update("""
            INSERT INTO creative_project (id, title, goal, created_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE
            SET title = EXCLUDED.title,
                goal = EXCLUDED.goal
            """, project.projectId(), project.title(), project.goal(), Timestamp.from(project.createdAt()));
        return project;
    }

    @Override
    public Optional<CreativeProject> findById(String projectId) {
        List<CreativeProject> projects = jdbcTemplate.query("""
            SELECT id, title, goal, created_at
            FROM creative_project
            WHERE id = ?
            """, this::mapProject, projectId);
        return projects.stream().findFirst();
    }

    @Override
    public List<CreativeProject> findRecent(int limit) {
        return jdbcTemplate.query("""
            SELECT id, title, goal, created_at
            FROM creative_project
            ORDER BY created_at DESC
            LIMIT ?
            """, this::mapProject, limit);
    }

    private CreativeProject mapProject(ResultSet resultSet, int rowNum) throws SQLException {
        return new CreativeProject(resultSet.getString("id"), resultSet.getString("title"),
            resultSet.getString("goal"), resultSet.getTimestamp("created_at").toInstant());
    }
}
