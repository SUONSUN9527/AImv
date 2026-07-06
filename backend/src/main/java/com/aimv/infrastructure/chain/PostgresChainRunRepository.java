package com.aimv.infrastructure.chain;

import com.aimv.domain.artifact.Artifact;
import com.aimv.domain.artifact.ArtifactRepository;
import com.aimv.domain.chain.ChainRun;
import com.aimv.domain.chain.ChainRunRepository;
import com.aimv.domain.chain.ChainRunStatus;
import com.aimv.domain.chain.LatestChainRun;
import com.aimv.domain.chain.ReviewReport;
import com.aimv.domain.chain.StageRun;
import com.aimv.domain.chain.StageRunStatus;
import com.aimv.domain.shared.ChainType;
import com.aimv.infrastructure.database.PostgresJsonSupport;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("postgres")
public class PostgresChainRunRepository implements ChainRunRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ArtifactRepository artifactRepository;

    public PostgresChainRunRepository(JdbcTemplate jdbcTemplate, ArtifactRepository artifactRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.artifactRepository = artifactRepository;
    }

    @Override
    @Transactional
    public ChainRun save(ChainRun chainRun) {
        jdbcTemplate.update("""
            INSERT INTO chain_run (
                id, project_id, chain_type, user_goal, status, current_stage_code,
                blocking_reason, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE
            SET user_goal = EXCLUDED.user_goal,
                status = EXCLUDED.status,
                current_stage_code = EXCLUDED.current_stage_code,
                blocking_reason = EXCLUDED.blocking_reason,
                updated_at = EXCLUDED.updated_at
            """, chainRun.chainRunId(), chainRun.projectId(), chainRun.chainType().name(), chainRun.userGoal(),
            chainRun.status().name(), chainRun.currentStageCode(), chainRun.blockingReason(),
            Timestamp.from(chainRun.createdAt()), Timestamp.from(chainRun.updatedAt()));

        jdbcTemplate.update("DELETE FROM stage_run WHERE chain_run_id = ?", chainRun.chainRunId());
        chainRun.stageRuns().forEach(this::saveStageRun);
        return chainRun;
    }

    @Override
    public Optional<ChainRun> findById(String chainRunId) {
        List<ChainRun> chainRuns = jdbcTemplate.query("""
            SELECT id, project_id, chain_type, user_goal, status, current_stage_code,
                   blocking_reason, created_at, updated_at
            FROM chain_run
            WHERE id = ?
            """, this::mapChainRun, chainRunId);
        return chainRuns.stream().findFirst();
    }

    @Override
    public Optional<ChainRun> findByStageRunId(String stageRunId) {
        List<String> chainRunIds = jdbcTemplate.queryForList("""
            SELECT chain_run_id
            FROM stage_run
            WHERE id = ?
            """, String.class, stageRunId);
        return chainRunIds.stream().findFirst().flatMap(this::findById);
    }

    @Override
    public Map<String, LatestChainRun> latestChainRunByProject() {
        // DISTINCT ON (project_id)：按 project_id 分组后取 created_at 最新的一条链路（含状态）。
        Map<String, LatestChainRun> result = new HashMap<>();
        jdbcTemplate.query("""
            SELECT DISTINCT ON (project_id) project_id, id, status
            FROM chain_run
            ORDER BY project_id, created_at DESC
            """, resultSet -> {
            result.put(resultSet.getString("project_id"),
                new LatestChainRun(resultSet.getString("id"), resultSet.getString("status")));
        });
        return result;
    }

    private void saveStageRun(StageRun stageRun) {
        jdbcTemplate.update("""
            INSERT INTO stage_run (
                id, chain_run_id, stage_code, stage_name, status, retrieval_record_id,
                handoff_context_id, agent_node_run_ids, free_model_gate_ids, provider_job_ids,
                started_at, finished_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, stageRun.stageRunId(), stageRun.chainRunId(), stageRun.stageCode(), stageRun.stageName(),
            stageRun.status().name(), stageRun.retrievalRecordId(), stageRun.handoffContextId(),
            PostgresJsonSupport.jsonb(stageRun.agentNodeRunIds()),
            PostgresJsonSupport.jsonb(stageRun.freeModelGateIds()),
            PostgresJsonSupport.jsonb(stageRun.providerJobIds()), Timestamp.from(stageRun.startedAt()),
            stageRun.finishedAt() == null ? null : Timestamp.from(stageRun.finishedAt()));
        saveReviewReport(stageRun);
    }

    private void saveReviewReport(StageRun stageRun) {
        ReviewReport report = stageRun.reviewReport();
        if (report == null) {
            return;
        }
        jdbcTemplate.update("""
            INSERT INTO review_report (
                id, stage_run_id, passed, overall_score, rubric_version, summary, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, now())
            """, "review-" + UUID.randomUUID(), stageRun.stageRunId(), report.passed(), report.overallScore(),
            report.rubricVersion(), report.summary());
    }

    private ChainRun mapChainRun(ResultSet resultSet, int rowNum) throws SQLException {
        String chainRunId = resultSet.getString("id");
        List<StageRun> stageRuns = findStageRuns(chainRunId);
        List<Artifact> artifacts = artifactRepository.findByChainRunId(chainRunId);
        return new ChainRun(chainRunId, resultSet.getString("project_id"),
            ChainType.valueOf(resultSet.getString("chain_type")), resultSet.getString("user_goal"),
            ChainRunStatus.valueOf(resultSet.getString("status")), resultSet.getString("current_stage_code"),
            stageRuns, artifacts, resultSet.getString("blocking_reason"),
            resultSet.getTimestamp("created_at").toInstant(), resultSet.getTimestamp("updated_at").toInstant());
    }

    private List<StageRun> findStageRuns(String chainRunId) {
        return jdbcTemplate.query("""
            SELECT sr.id, sr.chain_run_id, sr.stage_code, sr.stage_name, sr.status,
                   sr.retrieval_record_id, sr.handoff_context_id, sr.agent_node_run_ids,
                   sr.free_model_gate_ids, sr.provider_job_ids, sr.started_at, sr.finished_at,
                   rr.passed, rr.overall_score, rr.rubric_version, rr.summary
            FROM stage_run sr
            LEFT JOIN review_report rr ON rr.stage_run_id = sr.id
            WHERE sr.chain_run_id = ?
            ORDER BY sr.started_at ASC, sr.stage_code ASC
            """, this::mapStageRun, chainRunId);
    }

    private StageRun mapStageRun(ResultSet resultSet, int rowNum) throws SQLException {
        Timestamp finishedAt = resultSet.getTimestamp("finished_at");
        return new StageRun(resultSet.getString("id"), resultSet.getString("chain_run_id"),
            resultSet.getString("stage_code"), resultSet.getString("stage_name"),
            StageRunStatus.valueOf(resultSet.getString("status")), mapReviewReport(resultSet),
            resultSet.getString("retrieval_record_id"), resultSet.getString("handoff_context_id"),
            PostgresJsonSupport.stringList(resultSet.getString("agent_node_run_ids")),
            PostgresJsonSupport.stringList(resultSet.getString("free_model_gate_ids")),
            PostgresJsonSupport.stringList(resultSet.getString("provider_job_ids")),
            resultSet.getTimestamp("started_at").toInstant(), finishedAt == null ? null : finishedAt.toInstant());
    }

    private ReviewReport mapReviewReport(ResultSet resultSet) throws SQLException {
        String rubricVersion = resultSet.getString("rubric_version");
        if (rubricVersion == null) {
            return null;
        }
        return new ReviewReport(resultSet.getBoolean("passed"), resultSet.getInt("overall_score"), rubricVersion,
            resultSet.getString("summary"));
    }
}
