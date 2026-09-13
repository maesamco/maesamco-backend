ALTER TABLE judge_schema.pending_judge0_executions
    ADD COLUMN retry_count INTEGER NOT NULL DEFAULT 0;