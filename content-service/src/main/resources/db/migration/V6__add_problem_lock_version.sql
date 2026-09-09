ALTER TABLE content_schema.p_problems
    ADD COLUMN IF NOT EXISTS lock_version BIGINT NOT NULL DEFAULT 0;
