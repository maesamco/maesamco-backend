CREATE INDEX idx_submissions_status_submitted_at
    ON judge_schema.p_submissions (status, submitted_at);