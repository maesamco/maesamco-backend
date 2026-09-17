CREATE INDEX idx_submissions_user_id_created_at
    ON p_submissions (user_id, created_at);