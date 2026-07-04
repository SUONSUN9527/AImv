# PostgreSQL + pgvector

## Profile

Default local development uses in-memory repositories. The `postgres` profile enables PostgreSQL-backed repositories and Flyway migrations:

```bash
export AIMV_POSTGRES_URL=jdbc:postgresql://127.0.0.1:5432/aimv
export AIMV_POSTGRES_USERNAME=aimv
export AIMV_POSTGRES_PASSWORD=aimv
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

## Migration Scope

`V1__init_pgvector_schema.sql` creates:

- `vector` and `pg_trgm` extensions.
- Core chain tables: project, chain run, stage run, review report, artifact.
- RAG tables: knowledge document, chunk, embedding, retrieval record, retrieval hit.
- Capability and key tables: capability, capability slot, encrypted credential, selection, snapshot.
- Execution evidence tables: agent node run, external job, free model gate.

The migration stores encrypted API key material only through `encrypted_key`, `secret_hash`, and `masked_key`.

## Adapter Scope

Implemented PostgreSQL repositories:

- `ProjectRepository`
- `ChainRunRepository`
- `ArtifactRepository`
- `KnowledgeRepository`
- `ApiConfigRepository`
- `AgentNodeRunRepository`
- `ExternalJobRepository`

## Runtime Verification

Verified locally with Docker image `pgvector/pgvector:pg16` and backend `postgres` profile on port `18081`.

Evidence from the smoke run:

- `vector` and `pg_trgm` extensions were installed by Flyway.
- `idx_knowledge_embedding_dense_hnsw`, `idx_knowledge_chunk_tsv`, and
  `idx_knowledge_chunk_content_trgm` existed after migration.
- IMAGE and VIDEO fixture chain runs persisted through PostgreSQL repositories.
- I30/V30 RAG retrieval returned `CHAIN_CONTEXT`, `NEXT_STAGE_CONTEXT`, and
  `REVIEW_REPORT`; retrieve responses and `RetrievalRecord` details both reported
  coverage passed.
- The latest `retrieval_record.coverage_json` row had
  `previousReviewReport=true` and `passed=true`.
- The database contained 17 `chain_run` rows, 119 `stage_run` rows, 119
  `external_job` rows, 266 `knowledge_chunk` rows, 124 `retrieval_record` rows,
  and 68 `api_selection_snapshot` rows after the smoke.

This verifies schema execution and repository persistence against PostgreSQL 16 + pgvector. It does not cover
production backup, HA, or load testing.
