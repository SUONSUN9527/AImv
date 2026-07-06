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
            SELECT id, title, goal, created_at, pinned_at
            FROM creative_project
            WHERE id = ?
            """, this::mapProject, projectId);
        return projects.stream().findFirst();
    }

    @Override
    public List<CreativeProject> findRecent(int limit) {
        // 置顶（pinned_at 非空）优先、按置顶时间倒序，其余按创建时间倒序，保证置顶项不被 LIMIT 挤掉。
        return jdbcTemplate.query("""
            SELECT id, title, goal, created_at, pinned_at
            FROM creative_project
            ORDER BY pinned_at DESC NULLS LAST, created_at DESC
            LIMIT ?
            """, this::mapProject, limit);
    }

    @Override
    public void delete(String projectId) {
        // V4 迁移已把 project→chain_run→子表、knowledge_document 的外键改成 ON DELETE CASCADE，
        // 这里删主表即可级联清理整段对话。
        jdbcTemplate.update("DELETE FROM creative_project WHERE id = ?", projectId);
    }

    @Override
    public void updatePinned(String projectId, java.time.Instant pinnedAt) {
        jdbcTemplate.update("UPDATE creative_project SET pinned_at = ? WHERE id = ?",
            pinnedAt == null ? null : Timestamp.from(pinnedAt), projectId);
    }

    private CreativeProject mapProject(ResultSet resultSet, int rowNum) throws SQLException {
        Timestamp pinnedAt = resultSet.getTimestamp("pinned_at");
        return new CreativeProject(resultSet.getString("id"), resultSet.getString("title"),
            resultSet.getString("goal"), resultSet.getTimestamp("created_at").toInstant(),
            pinnedAt == null ? null : pinnedAt.toInstant());
    }
}
