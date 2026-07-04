package com.aimv.infrastructure.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.junit.jupiter.api.Test;

class PostgresSchemaMigrationTest {

    private static final String MIGRATION = "/db/migration/V1__init_pgvector_schema.sql";

    @Test
    void initializesPostgresPgvectorSchemaRequiredByDesignDocument() throws IOException {
        String sql = migrationSql();

        assertThat(sql).contains("CREATE EXTENSION IF NOT EXISTS vector");
        assertThat(sql).contains("dense_vector vector(1024) NOT NULL");
        assertThat(sql).contains("USING hnsw (dense_vector vector_cosine_ops)");
        assertThat(sql).contains("content_tsv tsvector");
        assertThat(sql).contains("gin_trgm_ops");
        assertThat(sql).contains("DEFERRABLE INITIALLY DEFERRED");

        assertThat(sql).contains(
            "CREATE TABLE IF NOT EXISTS creative_project",
            "CREATE TABLE IF NOT EXISTS chain_run",
            "CREATE TABLE IF NOT EXISTS stage_run",
            "CREATE TABLE IF NOT EXISTS agent_node_run",
            "CREATE TABLE IF NOT EXISTS review_report",
            "CREATE TABLE IF NOT EXISTS artifact",
            "CREATE TABLE IF NOT EXISTS knowledge_document",
            "CREATE TABLE IF NOT EXISTS knowledge_chunk",
            "CREATE TABLE IF NOT EXISTS knowledge_embedding",
            "CREATE TABLE IF NOT EXISTS retrieval_record",
            "CREATE TABLE IF NOT EXISTS retrieval_hit",
            "CREATE TABLE IF NOT EXISTS capability",
            "CREATE TABLE IF NOT EXISTS api_capability_slot",
            "CREATE TABLE IF NOT EXISTS api_credential",
            "CREATE TABLE IF NOT EXISTS api_selection",
            "CREATE TABLE IF NOT EXISTS api_selection_snapshot",
            "CREATE TABLE IF NOT EXISTS external_job",
            "CREATE TABLE IF NOT EXISTS free_model_gate"
        );
    }

    @Test
    void storesOnlyEncryptedAndMaskedApiCredentialMaterial() throws IOException {
        String normalizedSql = migrationSql().toLowerCase();

        assertThat(normalizedSql).contains("encrypted_key text not null");
        assertThat(normalizedSql).contains("secret_hash text not null");
        assertThat(normalizedSql).contains("masked_key text not null");
        assertThat(normalizedSql).contains("check (status = 'deleted' or encrypted_key <> '')");
        assertThat(normalizedSql).doesNotContain("plaintext_key");
        assertThat(normalizedSql).doesNotContain("api_key text");
    }

    @Test
    void scopesApiSelectionByUserChainAndCapability() throws IOException {
        String normalizedSql = allMigrationSql().toLowerCase();

        assertThat(normalizedSql).contains("create table if not exists api_selection");
        assertThat(normalizedSql).contains("user_id text not null default 'local-user'");
        assertThat(normalizedSql).contains("unique (user_id, chain_type, capability_type)");
    }

    @Test
    void externalJobsSupportImmediateProviderSubmissionEvidence() throws IOException {
        String normalizedSql = migrationSql().toLowerCase();

        assertThat(normalizedSql).contains("create table if not exists external_job");
        assertThat(normalizedSql).contains("provider_job_id text not null");
        assertThat(normalizedSql).contains(
            "chain_run_id text references chain_run(id) on delete cascade deferrable initially deferred");
        assertThat(normalizedSql).contains(
            "stage_run_id text references stage_run(id) on delete cascade deferrable initially deferred");
        assertThat(normalizedSql).contains("retry_policy text not null");
        assertThat(normalizedSql).contains("retry_count integer not null default 0");
        assertThat(normalizedSql).contains("request_hash text not null");
        assertThat(normalizedSql).contains("unique (provider, provider_job_id)");
    }

    private String migrationSql() throws IOException {
        try (var inputStream = getClass().getResourceAsStream(MIGRATION)) {
            assertThat(inputStream).as("migration resource").isNotNull();
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String allMigrationSql() throws IOException {
        Path migrationDirectory = Path.of("src/main/resources/db/migration");
        StringBuilder sql = new StringBuilder();
        try (var paths = Files.list(migrationDirectory)) {
            for (Path path : paths.sorted(Comparator.comparing(Path::toString)).toList()) {
                sql.append(Files.readString(path, StandardCharsets.UTF_8)).append('\n');
            }
        }
        return sql.toString();
    }
}
