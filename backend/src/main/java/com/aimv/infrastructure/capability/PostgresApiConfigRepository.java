package com.aimv.infrastructure.capability;

import com.aimv.domain.capability.ApiConfigRepository;
import com.aimv.domain.capability.ApiCredential;
import com.aimv.domain.capability.ApiKeyStatus;
import com.aimv.domain.capability.ApiSelectionSnapshot;
import com.aimv.domain.capability.FreeModelGate;
import com.aimv.domain.capability.FreeModelGateStatus;
import com.aimv.domain.shared.ChainType;
import com.aimv.infrastructure.database.PostgresJsonSupport;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
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
public class PostgresApiConfigRepository implements ApiConfigRepository {

    private static final String DEFAULT_USER_ID = "local-user";

    private final JdbcTemplate jdbcTemplate;

    public PostgresApiConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public ApiCredential save(ApiCredential credential) {
        jdbcTemplate.update("""
            INSERT INTO api_credential (
                id, chain_type, capability_type, provider, label, secret_hash, encrypted_key,
                masked_key, model, status, selected, free_model_gate_status, last_verified_at,
                deleted_at, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
            ON CONFLICT (id) DO UPDATE
            SET provider = EXCLUDED.provider,
                label = EXCLUDED.label,
                secret_hash = EXCLUDED.secret_hash,
                encrypted_key = EXCLUDED.encrypted_key,
                masked_key = EXCLUDED.masked_key,
                model = EXCLUDED.model,
                status = EXCLUDED.status,
                selected = EXCLUDED.selected,
                free_model_gate_status = EXCLUDED.free_model_gate_status,
                last_verified_at = EXCLUDED.last_verified_at,
                deleted_at = EXCLUDED.deleted_at,
                updated_at = now()
            """, credential.apiKeyId(), credential.chainType().name(), credential.capabilityType(),
            credential.provider(), credential.label(), credential.secretHash(), credential.encryptedSecret(),
            credential.maskedKey(), credential.model(), credential.status().name(), credential.selected(),
            credential.freeModelGateStatus().name(), timestamp(credential.lastVerifiedAt()),
            credential.status() == ApiKeyStatus.DELETED ? Timestamp.from(Instant.now()) : null);
        syncSelection(credential);
        return credential;
    }

    @Override
    public Optional<ApiCredential> findCredential(String apiKeyId) {
        List<ApiCredential> credentials = jdbcTemplate.query("""
            SELECT id, chain_type, capability_type, provider, label, secret_hash, encrypted_key,
                   masked_key, model, status, selected, free_model_gate_status, last_verified_at
            FROM api_credential
            WHERE id = ?
            """, this::mapCredential, apiKeyId);
        return credentials.stream().findFirst();
    }

    @Override
    public List<ApiCredential> findCredentials(ChainType chainType, String capabilityType) {
        return jdbcTemplate.query("""
            SELECT id, chain_type, capability_type, provider, label, secret_hash, encrypted_key,
                   masked_key, model, status, selected, free_model_gate_status, last_verified_at
            FROM api_credential
            WHERE chain_type = ?
              AND capability_type = ?
              AND status <> 'DELETED'
            ORDER BY updated_at DESC
            """, this::mapCredential, chainType.name(), capabilityType);
    }

    @Override
    public List<ApiCredential> findSelectedCredentials(ChainType chainType) {
        return jdbcTemplate.query("""
            SELECT c.id, c.chain_type, c.capability_type, c.provider, c.label, c.secret_hash,
                   c.encrypted_key, c.masked_key, c.model, c.status, c.selected,
                   c.free_model_gate_status, c.last_verified_at
            FROM api_selection s
            JOIN api_credential c ON c.id = s.api_credential_id
            WHERE s.user_id = ?
              AND s.chain_type = ?
              AND c.status = 'ACTIVE'
            ORDER BY c.capability_type ASC
            """, this::mapCredential, DEFAULT_USER_ID, chainType.name());
    }

    @Override
    @Transactional
    public void saveSnapshot(ApiSelectionSnapshot snapshot) {
        saveFreeModelGate(snapshot.freeModelGate());
        jdbcTemplate.update("""
            INSERT INTO api_selection_snapshot (
                id, chain_run_id, chain_type, capability_type, provider, api_credential_id,
                masked_key, model, free_model_gate_id, free_quota_snapshot_json, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO NOTHING
            """, snapshot.snapshotId(), snapshot.chainRunId(), snapshot.chainType().name(),
            snapshot.capabilityType(), snapshot.provider(), snapshot.apiKeyId(), snapshot.maskedKey(),
            snapshot.model(), snapshot.freeModelGate().freeModelGateId(),
            PostgresJsonSupport.jsonb(Map.of("quotaSnapshot", snapshot.freeModelGate().quotaSnapshot())),
            Timestamp.from(snapshot.createdAt()));
    }

    @Override
    public List<ApiSelectionSnapshot> findSnapshots(String chainRunId) {
        return jdbcTemplate.query("""
            SELECT s.id, s.chain_run_id, s.chain_type, s.capability_type, s.provider,
                   s.api_credential_id, s.masked_key, s.model, s.created_at,
                   f.id AS free_model_gate_id, f.passed, f.plan, f.paid_fallback_allowed,
                   f.quota_snapshot_json, f.checked_at
            FROM api_selection_snapshot s
            LEFT JOIN free_model_gate f ON f.id = s.free_model_gate_id
            WHERE s.chain_run_id = ?
            ORDER BY s.created_at ASC
            """, this::mapSnapshot, chainRunId);
    }

    private void syncSelection(ApiCredential credential) {
        jdbcTemplate.update("DELETE FROM api_selection WHERE api_credential_id = ?", credential.apiKeyId());
        if (!credential.selected()) {
            return;
        }
        jdbcTemplate.update("""
            INSERT INTO api_selection (
                id, user_id, chain_type, capability_type, api_credential_id, selected_at
            ) VALUES (?, ?, ?, ?, ?, now())
            ON CONFLICT (user_id, chain_type, capability_type) DO UPDATE
            SET api_credential_id = EXCLUDED.api_credential_id,
                selected_at = EXCLUDED.selected_at
            """, "selection-" + UUID.randomUUID(), DEFAULT_USER_ID, credential.chainType().name(),
            credential.capabilityType(), credential.apiKeyId());
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

    private ApiCredential mapCredential(ResultSet resultSet, int rowNum) throws SQLException {
        return new ApiCredential(resultSet.getString("id"), ChainType.valueOf(resultSet.getString("chain_type")),
            resultSet.getString("capability_type"), resultSet.getString("provider"),
            resultSet.getString("label"), resultSet.getString("secret_hash"),
            resultSet.getString("encrypted_key"), resultSet.getString("masked_key"),
            resultSet.getString("model"), ApiKeyStatus.valueOf(resultSet.getString("status")),
            resultSet.getBoolean("selected"), instant(resultSet, "last_verified_at"),
            FreeModelGateStatus.valueOf(resultSet.getString("free_model_gate_status")));
    }

    private ApiSelectionSnapshot mapSnapshot(ResultSet resultSet, int rowNum) throws SQLException {
        FreeModelGate freeModelGate = new FreeModelGate(resultSet.getString("free_model_gate_id"),
            resultSet.getBoolean("passed"), resultSet.getString("provider"), resultSet.getString("model"),
            resultSet.getString("capability_type"), resultSet.getString("plan"),
            resultSet.getBoolean("paid_fallback_allowed"),
            PostgresJsonSupport.objectMap(resultSet.getString("quota_snapshot_json")).toString(),
            resultSet.getTimestamp("checked_at").toInstant());
        return new ApiSelectionSnapshot(resultSet.getString("id"), resultSet.getString("chain_run_id"),
            ChainType.valueOf(resultSet.getString("chain_type")), resultSet.getString("capability_type"),
            resultSet.getString("provider"), resultSet.getString("api_credential_id"),
            resultSet.getString("masked_key"), resultSet.getString("model"), freeModelGate,
            resultSet.getTimestamp("created_at").toInstant());
    }

    private Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant instant(ResultSet resultSet, String columnName) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(columnName);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
