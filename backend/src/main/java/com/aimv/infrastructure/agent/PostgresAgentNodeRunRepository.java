package com.aimv.infrastructure.agent;

import com.aimv.domain.agent.AgentNodeRun;
import com.aimv.domain.agent.AgentNodeRunRepository;
import com.aimv.domain.agent.AgentNodeRunStatus;
import com.aimv.domain.capability.FreeModelGate;
import com.aimv.infrastructure.database.PostgresJsonSupport;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("postgres")
public class PostgresAgentNodeRunRepository implements AgentNodeRunRepository {

    private final JdbcTemplate jdbcTemplate;

    public PostgresAgentNodeRunRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public AgentNodeRun save(AgentNodeRun agentNodeRun) {
        saveFreeModelGate(agentNodeRun.freeModelGate());
        jdbcTemplate.update("""
            INSERT INTO agent_node_run (
                id, chain_run_id, stage_run_id, stage_code, node_name, status, capability_type,
                provider, model, provider_job_id, free_model_gate_id, retrieval_record_id,
                output_summary, started_at, finished_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE
            SET status = EXCLUDED.status,
                provider_job_id = EXCLUDED.provider_job_id,
                output_summary = EXCLUDED.output_summary,
                finished_at = EXCLUDED.finished_at
            """, agentNodeRun.nodeRunId(), agentNodeRun.chainRunId(), agentNodeRun.stageRunId(),
            agentNodeRun.stageCode(), agentNodeRun.nodeName(), agentNodeRun.status().name(),
            agentNodeRun.capabilityType(), agentNodeRun.provider(), agentNodeRun.model(),
            agentNodeRun.providerJobId(), agentNodeRun.freeModelGate().freeModelGateId(),
            agentNodeRun.retrievalRecordId(), agentNodeRun.outputSummary(),
            Timestamp.from(agentNodeRun.startedAt()), Timestamp.from(agentNodeRun.finishedAt()));
        return agentNodeRun;
    }

    @Override
    public Optional<AgentNodeRun> findById(String nodeRunId) {
        List<AgentNodeRun> runs = jdbcTemplate.query("""
            SELECT n.id, n.chain_run_id, n.stage_run_id, n.stage_code, n.node_name, n.status,
                   n.capability_type, n.provider, n.model, n.provider_job_id, n.retrieval_record_id,
                   n.output_summary, n.started_at, n.finished_at,
                   f.id AS free_model_gate_id, f.passed, f.plan, f.paid_fallback_allowed,
                   f.quota_snapshot_json, f.checked_at
            FROM agent_node_run n
            JOIN free_model_gate f ON f.id = n.free_model_gate_id
            WHERE n.id = ?
            """, this::mapNodeRun, nodeRunId);
        return runs.stream().findFirst();
    }

    private void saveFreeModelGate(FreeModelGate freeModelGate) {
        jdbcTemplate.update("""
            INSERT INTO free_model_gate (
                id, passed, provider, model, capability_type, plan, paid_fallback_allowed,
                quota_snapshot_json, checked_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO NOTHING
            """, freeModelGate.freeModelGateId(), freeModelGate.passed(), freeModelGate.provider(),
            freeModelGate.model(), freeModelGate.capabilityType(), freeModelGate.plan(),
            freeModelGate.paidFallbackAllowed(),
            PostgresJsonSupport.jsonb(Map.of("quotaSnapshot", freeModelGate.quotaSnapshot())),
            Timestamp.from(freeModelGate.checkedAt()));
    }

    private AgentNodeRun mapNodeRun(ResultSet resultSet, int rowNum) throws SQLException {
        FreeModelGate freeModelGate = new FreeModelGate(resultSet.getString("free_model_gate_id"),
            resultSet.getBoolean("passed"), resultSet.getString("provider"), resultSet.getString("model"),
            resultSet.getString("capability_type"), resultSet.getString("plan"),
            resultSet.getBoolean("paid_fallback_allowed"),
            PostgresJsonSupport.objectMap(resultSet.getString("quota_snapshot_json")).toString(),
            resultSet.getTimestamp("checked_at").toInstant());
        return new AgentNodeRun(resultSet.getString("id"), resultSet.getString("chain_run_id"),
            resultSet.getString("stage_run_id"), resultSet.getString("stage_code"),
            resultSet.getString("node_name"), AgentNodeRunStatus.valueOf(resultSet.getString("status")),
            resultSet.getString("capability_type"), resultSet.getString("provider"),
            resultSet.getString("model"), resultSet.getString("provider_job_id"), freeModelGate,
            resultSet.getString("retrieval_record_id"), resultSet.getString("output_summary"),
            resultSet.getTimestamp("started_at").toInstant(), resultSet.getTimestamp("finished_at").toInstant());
    }
}
