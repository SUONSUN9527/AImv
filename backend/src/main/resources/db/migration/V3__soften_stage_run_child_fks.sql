-- external_job / agent_node_run / retrieval_record 在 agent 执行期间就被写入，
-- 引用的 stage_run 此时尚未落库；且 chain_run 保存采用「删除后重插 stage_run」策略，
-- ON DELETE CASCADE 会在每次增量保存时连带清空这些子表。
-- 该增量写入模型是按内存仓储设计的，与硬外键不兼容 → 改为软引用（应用层保证一致性）。
ALTER TABLE external_job DROP CONSTRAINT IF EXISTS external_job_stage_run_id_fkey;
ALTER TABLE agent_node_run DROP CONSTRAINT IF EXISTS agent_node_run_stage_run_id_fkey;
ALTER TABLE retrieval_record DROP CONSTRAINT IF EXISTS retrieval_record_stage_run_id_fkey;
