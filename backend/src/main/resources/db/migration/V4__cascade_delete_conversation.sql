-- 支持「删除对话」：删除 project 时级联清理其下所有链路与知识文档。
-- chain_run 的子表（stage_run/artifact/agent_node_run/external_job/api_selection_snapshot）本就 CASCADE，
-- 只需把 project/knowledge 这几个 NO ACTION 外键也改成 ON DELETE CASCADE。
ALTER TABLE chain_run DROP CONSTRAINT IF EXISTS chain_run_project_id_fkey;
ALTER TABLE chain_run ADD CONSTRAINT chain_run_project_id_fkey
    FOREIGN KEY (project_id) REFERENCES creative_project(id) ON DELETE CASCADE;

ALTER TABLE knowledge_document DROP CONSTRAINT IF EXISTS knowledge_document_project_id_fkey;
ALTER TABLE knowledge_document ADD CONSTRAINT knowledge_document_project_id_fkey
    FOREIGN KEY (project_id) REFERENCES creative_project(id) ON DELETE CASCADE;

ALTER TABLE knowledge_document DROP CONSTRAINT IF EXISTS knowledge_document_chain_run_id_fkey;
ALTER TABLE knowledge_document ADD CONSTRAINT knowledge_document_chain_run_id_fkey
    FOREIGN KEY (chain_run_id) REFERENCES chain_run(id) ON DELETE CASCADE;
