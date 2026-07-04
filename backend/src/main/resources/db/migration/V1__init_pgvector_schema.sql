CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE IF NOT EXISTS creative_project (
    id text PRIMARY KEY,
    title text NOT NULL,
    goal text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS chain_run (
    id text PRIMARY KEY,
    project_id text NOT NULL REFERENCES creative_project(id),
    chain_type text NOT NULL CHECK (chain_type IN ('IMAGE', 'VIDEO')),
    user_goal text NOT NULL,
    status text NOT NULL,
    current_stage_code text,
    blocking_reason text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS stage_run (
    id text PRIMARY KEY,
    chain_run_id text NOT NULL REFERENCES chain_run(id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED,
    stage_code text NOT NULL,
    stage_name text NOT NULL,
    status text NOT NULL,
    retrieval_record_id text,
    handoff_context_id text,
    agent_node_run_ids jsonb NOT NULL DEFAULT '[]'::jsonb,
    free_model_gate_ids jsonb NOT NULL DEFAULT '[]'::jsonb,
    provider_job_ids jsonb NOT NULL DEFAULT '[]'::jsonb,
    started_at timestamptz NOT NULL,
    finished_at timestamptz
);

CREATE TABLE IF NOT EXISTS review_report (
    id text PRIMARY KEY,
    stage_run_id text NOT NULL UNIQUE REFERENCES stage_run(id) ON DELETE CASCADE,
    passed boolean NOT NULL,
    overall_score integer NOT NULL CHECK (overall_score BETWEEN 0 AND 100),
    rubric_version text NOT NULL,
    summary text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS artifact (
    id text PRIMARY KEY,
    chain_run_id text NOT NULL REFERENCES chain_run(id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED,
    chain_type text NOT NULL CHECK (chain_type IN ('IMAGE', 'VIDEO')),
    artifact_kind text NOT NULL,
    display_name text NOT NULL,
    storage_path text NOT NULL,
    content_hash text NOT NULL,
    metadata_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    verification_status text NOT NULL DEFAULT 'PENDING',
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS free_model_gate (
    id text PRIMARY KEY,
    passed boolean NOT NULL,
    provider text NOT NULL,
    model text,
    capability_type text NOT NULL,
    plan text NOT NULL,
    paid_fallback_allowed boolean NOT NULL DEFAULT false,
    quota_snapshot_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    checked_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS agent_node_run (
    id text PRIMARY KEY,
    chain_run_id text NOT NULL REFERENCES chain_run(id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED,
    stage_run_id text NOT NULL REFERENCES stage_run(id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED,
    stage_code text NOT NULL,
    node_name text NOT NULL,
    status text NOT NULL,
    capability_type text NOT NULL,
    provider text NOT NULL,
    model text,
    provider_job_id text,
    free_model_gate_id text REFERENCES free_model_gate(id),
    retrieval_record_id text,
    output_summary text NOT NULL,
    started_at timestamptz NOT NULL,
    finished_at timestamptz
);

CREATE TABLE IF NOT EXISTS knowledge_document (
    id text PRIMARY KEY,
    project_id text REFERENCES creative_project(id),
    chain_run_id text REFERENCES chain_run(id),
    chain_type text NOT NULL CHECK (chain_type IN ('IMAGE', 'VIDEO')),
    namespace text NOT NULL,
    source_type text NOT NULL,
    source_id text NOT NULL,
    stage_code text NOT NULL,
    language text NOT NULL DEFAULT 'zh-CN',
    visibility text NOT NULL DEFAULT 'CHAIN_PRIVATE',
    content_hash text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS knowledge_chunk (
    id text PRIMARY KEY,
    document_id text NOT NULL REFERENCES knowledge_document(id) ON DELETE CASCADE,
    namespace text NOT NULL,
    chain_type text NOT NULL CHECK (chain_type IN ('IMAGE', 'VIDEO')),
    stage_code text NOT NULL,
    source_type text NOT NULL,
    source_id text NOT NULL,
    chunk_index integer NOT NULL,
    content text NOT NULL,
    content_summary text,
    token_count integer NOT NULL DEFAULT 0,
    language text NOT NULL DEFAULT 'zh-CN',
    content_hash text NOT NULL,
    content_tsv tsvector GENERATED ALWAYS AS (to_tsvector('simple', content)) STORED,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (document_id, chunk_index)
);

CREATE TABLE IF NOT EXISTS knowledge_embedding (
    id text PRIMARY KEY,
    chunk_id text NOT NULL REFERENCES knowledge_chunk(id) ON DELETE CASCADE,
    embedding_model text NOT NULL,
    embedding_dimension integer NOT NULL,
    dense_vector vector(1024) NOT NULL,
    sparse_vector_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    metadata_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (chunk_id, embedding_model)
);

CREATE TABLE IF NOT EXISTS retrieval_record (
    id text PRIMARY KEY,
    stage_run_id text REFERENCES stage_run(id) ON DELETE SET NULL,
    namespace text NOT NULL,
    chain_type text NOT NULL CHECK (chain_type IN ('IMAGE', 'VIDEO')),
    stage_code text NOT NULL,
    query text NOT NULL,
    namespace_filter_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    metadata_filter_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    top_k integer NOT NULL CHECK (top_k > 0),
    coverage_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    passed boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS retrieval_hit (
    id text PRIMARY KEY,
    retrieval_record_id text NOT NULL REFERENCES retrieval_record(id) ON DELETE CASCADE,
    chunk_id text NOT NULL REFERENCES knowledge_chunk(id) ON DELETE CASCADE,
    rank_order integer NOT NULL CHECK (rank_order > 0),
    keyword_score numeric(8, 5),
    vector_score numeric(8, 5),
    rerank_score numeric(8, 5),
    evidence_excerpt text,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (retrieval_record_id, chunk_id)
);

CREATE TABLE IF NOT EXISTS capability (
    id text PRIMARY KEY,
    capability_type text NOT NULL UNIQUE,
    label text NOT NULL,
    adapter_kind text NOT NULL,
    free_only boolean NOT NULL DEFAULT true,
    selected_key_required boolean NOT NULL DEFAULT true,
    enabled boolean NOT NULL DEFAULT true,
    metadata_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS api_capability_slot (
    id text PRIMARY KEY,
    chain_type text NOT NULL CHECK (chain_type IN ('IMAGE', 'VIDEO')),
    capability_type text NOT NULL REFERENCES capability(capability_type),
    display_name text NOT NULL,
    required boolean NOT NULL DEFAULT true,
    sort_order integer NOT NULL,
    enabled boolean NOT NULL DEFAULT true,
    UNIQUE (chain_type, capability_type)
);

CREATE TABLE IF NOT EXISTS api_credential (
    id text PRIMARY KEY,
    chain_type text NOT NULL CHECK (chain_type IN ('IMAGE', 'VIDEO')),
    capability_type text NOT NULL,
    provider text NOT NULL,
    label text NOT NULL,
    secret_hash text NOT NULL,
    encrypted_key text NOT NULL,
    masked_key text NOT NULL,
    model text,
    status text NOT NULL,
    selected boolean NOT NULL DEFAULT false,
    free_model_gate_status text NOT NULL,
    last_verified_at timestamptz,
    deleted_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (status = 'DELETED' OR encrypted_key <> '')
);

CREATE TABLE IF NOT EXISTS api_selection (
    id text PRIMARY KEY,
    chain_type text NOT NULL CHECK (chain_type IN ('IMAGE', 'VIDEO')),
    capability_type text NOT NULL,
    api_credential_id text NOT NULL REFERENCES api_credential(id),
    selected_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (chain_type, capability_type)
);

CREATE TABLE IF NOT EXISTS api_selection_snapshot (
    id text PRIMARY KEY,
    chain_run_id text NOT NULL REFERENCES chain_run(id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED,
    chain_type text NOT NULL CHECK (chain_type IN ('IMAGE', 'VIDEO')),
    capability_type text NOT NULL,
    provider text NOT NULL,
    api_credential_id text NOT NULL,
    masked_key text NOT NULL,
    model text,
    free_model_gate_id text REFERENCES free_model_gate(id),
    free_quota_snapshot_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS external_job (
    id text PRIMARY KEY,
    provider_job_id text NOT NULL,
    chain_run_id text REFERENCES chain_run(id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED,
    stage_run_id text REFERENCES stage_run(id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED,
    capability_type text NOT NULL,
    provider text NOT NULL,
    status text NOT NULL,
    retry_policy text NOT NULL DEFAULT 'FREE_PROVIDER_RETRY_ONLY',
    retry_count integer NOT NULL DEFAULT 0,
    request_hash text NOT NULL,
    response_metadata_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_job_id)
);

CREATE INDEX IF NOT EXISTS idx_chain_run_project_created ON chain_run (project_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_stage_run_chain_stage ON stage_run (chain_run_id, stage_code);
CREATE INDEX IF NOT EXISTS idx_artifact_chain_kind ON artifact (chain_run_id, artifact_kind);
CREATE INDEX IF NOT EXISTS idx_agent_node_stage ON agent_node_run (stage_run_id, node_name);
CREATE INDEX IF NOT EXISTS idx_knowledge_document_scope
    ON knowledge_document (namespace, chain_type, stage_code, source_type);
CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_scope
    ON knowledge_chunk (namespace, chain_type, stage_code, source_type);
CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_tsv ON knowledge_chunk USING gin (content_tsv);
CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_content_trgm ON knowledge_chunk USING gin (content gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_knowledge_embedding_dense_hnsw
    ON knowledge_embedding USING hnsw (dense_vector vector_cosine_ops);
CREATE INDEX IF NOT EXISTS idx_retrieval_record_scope_created
    ON retrieval_record (namespace, chain_type, stage_code, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_retrieval_hit_record_rank ON retrieval_hit (retrieval_record_id, rank_order);
CREATE INDEX IF NOT EXISTS idx_api_credential_slot ON api_credential (chain_type, capability_type, status);
CREATE INDEX IF NOT EXISTS idx_external_job_status ON external_job (status, updated_at);

INSERT INTO capability (id, capability_type, label, adapter_kind)
VALUES
    ('cap-llm-text-free', 'llm.text.free', '文本规划 LLM', 'HTTP_ADAPTER'),
    ('cap-rag-embedding-free', 'rag.embedding.free', 'RAG Embedding', 'HTTP_ADAPTER'),
    ('cap-rag-rerank-free', 'rag.rerank.free', 'RAG Rerank', 'HTTP_ADAPTER'),
    ('cap-image-generate-free', 'image.generate.free', '图片生成', 'HTTP_ADAPTER'),
    (
        'cap-video-full-voice-free',
        'video.generate.full_with_voice.free',
        '完整视频带配音生成',
        'HTTP_ADAPTER'
    )
ON CONFLICT (capability_type) DO NOTHING;

INSERT INTO api_capability_slot (id, chain_type, capability_type, display_name, sort_order)
VALUES
    ('slot-image-llm-text', 'IMAGE', 'llm.text.free', '文本规划 LLM', 10),
    ('slot-image-rag-embedding', 'IMAGE', 'rag.embedding.free', 'RAG Embedding', 20),
    ('slot-image-rag-rerank', 'IMAGE', 'rag.rerank.free', 'RAG Rerank', 30),
    ('slot-image-generate', 'IMAGE', 'image.generate.free', '图片生成', 40),
    ('slot-video-llm-text', 'VIDEO', 'llm.text.free', '文本规划 LLM', 10),
    ('slot-video-rag-embedding', 'VIDEO', 'rag.embedding.free', 'RAG Embedding', 20),
    ('slot-video-rag-rerank', 'VIDEO', 'rag.rerank.free', 'RAG Rerank', 30),
    ('slot-video-generate', 'VIDEO', 'video.generate.full_with_voice.free', '完整视频带配音生成', 40)
ON CONFLICT (chain_type, capability_type) DO NOTHING;
