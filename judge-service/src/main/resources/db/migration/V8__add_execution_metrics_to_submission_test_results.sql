ALTER TABLE judge_schema.p_submission_test_results
    ADD COLUMN execution_time_ms INTEGER,
    ADD COLUMN memory_used_kb INTEGER;