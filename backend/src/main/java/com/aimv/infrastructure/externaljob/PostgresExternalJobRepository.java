package com.aimv.infrastructure.externaljob;

import com.aimv.domain.externaljob.ExternalJob;
import com.aimv.domain.externaljob.ExternalJobRepository;
import com.aimv.domain.externaljob.ExternalJobStatus;
import com.aimv.infrastructure.database.PostgresJsonSupport;
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
public class PostgresExternalJobRepository implements ExternalJobRepository {

    private final JdbcTemplate jdbcTemplate;

    public PostgresExternalJobRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ExternalJob save(ExternalJob externalJob) {
        jdbcTemplate.update("""
            INSERT INTO external_job (
                id, provider_job_id, chain_run_id, stage_run_id, capability_type, provider,
                status, retry_policy, retry_count, request_hash, response_metadata_json,
                created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (provider, provider_job_id) DO UPDATE
            SET status = EXCLUDED.status,
                retry_policy = EXCLUDED.retry_policy,
                retry_count = EXCLUDED.retry_count,
                request_hash = EXCLUDED.request_hash,
                response_metadata_json = EXCLUDED.response_metadata_json,
                updated_at = EXCLUDED.updated_at
            """, externalJob.externalJobId(), externalJob.providerJobId(), externalJob.chainRunId(),
            externalJob.stageRunId(), externalJob.capabilityType(), externalJob.provider(),
            externalJob.status().name(), externalJob.retryPolicy(), externalJob.retryCount(),
            externalJob.requestHash(), PostgresJsonSupport.jsonb(externalJob.responseMetadata()),
            Timestamp.from(externalJob.createdAt()), Timestamp.from(externalJob.updatedAt()));
        return externalJob;
    }

    @Override
    public List<ExternalJob> findByChainRunId(String chainRunId) {
        return jdbcTemplate.query("""
            SELECT id, provider_job_id, chain_run_id, stage_run_id, capability_type, provider,
                   status, retry_policy, retry_count, request_hash, response_metadata_json,
                   created_at, updated_at
            FROM external_job
            WHERE chain_run_id = ?
            ORDER BY created_at ASC
            """, this::mapExternalJob, chainRunId);
    }

    @Override
    public Optional<ExternalJob> findByProviderJobId(String provider, String providerJobId) {
        List<ExternalJob> jobs = jdbcTemplate.query("""
            SELECT id, provider_job_id, chain_run_id, stage_run_id, capability_type, provider,
                   status, retry_policy, retry_count, request_hash, response_metadata_json,
                   created_at, updated_at
            FROM external_job
            WHERE provider = ?
              AND provider_job_id = ?
            """, this::mapExternalJob, provider, providerJobId);
        return jobs.stream().findFirst();
    }

    private ExternalJob mapExternalJob(ResultSet resultSet, int rowNum) throws SQLException {
        return new ExternalJob(resultSet.getString("id"), resultSet.getString("provider_job_id"),
            resultSet.getString("chain_run_id"), resultSet.getString("stage_run_id"),
            resultSet.getString("capability_type"), resultSet.getString("provider"),
            ExternalJobStatus.valueOf(resultSet.getString("status")), resultSet.getString("retry_policy"),
            resultSet.getInt("retry_count"), resultSet.getString("request_hash"),
            PostgresJsonSupport.objectMap(resultSet.getString("response_metadata_json")),
            resultSet.getTimestamp("created_at").toInstant(),
            resultSet.getTimestamp("updated_at").toInstant());
    }
}
