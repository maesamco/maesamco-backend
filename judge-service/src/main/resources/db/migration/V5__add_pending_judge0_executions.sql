CREATE TABLE judge_schema.p_pending_judge0_executions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    submission_id UUID NOT NULL,
    test_case_id UUID NOT NULL,
    judge0_token VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (submission_id) REFERENCES judge_schema.p_submissions(id),
    UNIQUE (judge0_token)
);

COMMENT ON TABLE judge_schema.p_pending_judge0_executions
    IS 'Judge0 batch 제출 후 결과가 올 때까지 대기 중인 토큰 매핑 — 결과 반영되면 즉시 행 삭제';

CREATE INDEX idx_pending_judge0_executions_created_at
    ON judge_schema.p_pending_judge0_executions (created_at);