ALTER TABLE api_selection
    ADD COLUMN IF NOT EXISTS user_id text NOT NULL DEFAULT 'local-user';

ALTER TABLE api_selection
    DROP CONSTRAINT IF EXISTS api_selection_chain_type_capability_type_key;

ALTER TABLE api_selection
    ADD CONSTRAINT api_selection_user_chain_capability_key
    UNIQUE (user_id, chain_type, capability_type);
