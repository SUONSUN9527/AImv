-- 置顶对话：pinned_at 非空表示已置顶，列表按置顶时间倒序排在最前。
ALTER TABLE creative_project ADD COLUMN IF NOT EXISTS pinned_at TIMESTAMPTZ;
