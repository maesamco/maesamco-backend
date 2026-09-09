CREATE TABLE judge_schema.p_pending_judge0_executions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    submission_id UUID NOT NULL,
    test_case_id UUID NOT NULL,
    judge0_token VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (submission_id) REFERENCES judge_schema.p_submissions(id),
    UNIQUE (judge0_token),
    UNIQUE (submission_id, test_case_id)
);
COMMENT ON TABLE judge_schema.p_pending_judge0_executions
    IS 'Judge0 batch 제출 후 결과가 올 때까지 대기 중인 토큰 매핑 — 결과 반영되면 즉시 행 삭제';
COMMENT ON CONSTRAINT p_pending_judge0_executions_submission_id_test_case_id_key
    ON judge_schema.p_pending_judge0_executions
    IS '동일 제출의 동일 테스트케이스에 대한 대기 토큰이 중복 저장되는 것을 스키마 레벨에서 방지 — 현재는 애플리케이션 로직으로만 막고 있어 재시도 로직(이슈 8) 도입 전 안전장치로 추가';
CREATE INDEX idx_pending_judge0_executions_created_at
    ON judge_schema.p_pending_judge0_executions (created_at);