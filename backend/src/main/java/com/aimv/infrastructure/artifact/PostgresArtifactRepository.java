package com.aimv.infrastructure.artifact;

import com.aimv.domain.artifact.Artifact;
import com.aimv.domain.artifact.ArtifactKind;
import com.aimv.domain.artifact.ArtifactRepository;
import com.aimv.domain.shared.ChainType;
import com.aimv.infrastructure.database.PostgresJsonSupport;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("postgres")
public class PostgresArtifactRepository implements ArtifactRepository {

    private final JdbcTemplate jdbcTemplate;

    public PostgresArtifactRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void saveAll(List<Artifact> artifacts) {
        artifacts.forEach(this::save);
    }

    @Override
    public void deleteByChainRunId(String chainRunId) {
        jdbcTemplate.update("DELETE FROM artifact WHERE chain_run_id = ?", chainRunId);
    }

    @Override
    public List<Artifact> findByChainRunId(String chainRunId) {
        return jdbcTemplate.query("""
            SELECT id, chain_run_id, chain_type, artifact_kind, display_name, storage_path,
                   content_hash, metadata_json, created_at
            FROM artifact
            WHERE chain_run_id = ?
            ORDER BY created_at ASC
            """, this::mapArtifact, chainRunId);
    }

    @Override
    public List<Artifact> findAll() {
        return jdbcTemplate.query("""
            SELECT id, chain_run_id, chain_type, artifact_kind, display_name, storage_path,
                   content_hash, metadata_json, created_at
            FROM artifact
            ORDER BY created_at DESC
            """, this::mapArtifact);
    }

    private void save(Artifact artifact) {
        jdbcTemplate.update("""
            INSERT INTO artifact (
                id, chain_run_id, chain_type, artifact_kind, display_name, storage_path,
                content_hash, metadata_json, verification_status, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'VERIFIED', ?)
            ON CONFLICT (id) DO UPDATE
            SET display_name = EXCLUDED.display_name,
                storage_path = EXCLUDED.storage_path,
                content_hash = EXCLUDED.content_hash,
                metadata_json = EXCLUDED.metadata_json,
                verification_status = EXCLUDED.verification_status
            """, artifact.artifactId(), artifact.chainRunId(), artifact.chainType().name(),
            artifact.artifactKind().name(), artifact.displayName(), artifact.url(), artifact.contentHash(),
            PostgresJsonSupport.jsonb(artifact.metadata()), Timestamp.from(artifact.createdAt()));
    }

    private Artifact mapArtifact(ResultSet resultSet, int rowNum) throws SQLException {
        return new Artifact(resultSet.getString("id"), resultSet.getString("chain_run_id"),
            ChainType.valueOf(resultSet.getString("chain_type")),
            ArtifactKind.valueOf(resultSet.getString("artifact_kind")), resultSet.getString("display_name"),
            resultSet.getString("storage_path"), resultSet.getString("content_hash"),
            PostgresJsonSupport.objectMap(resultSet.getString("metadata_json")),
            resultSet.getTimestamp("created_at").toInstant());
    }
}
