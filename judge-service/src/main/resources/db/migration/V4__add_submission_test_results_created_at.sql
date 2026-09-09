ALTER TABLE judge_schema.p_submission_test_results
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();

COMMENT ON COLUMN judge_schema.p_submission_test_results.created_at
    IS '테스트케이스 채점 결과 생성 시각 — 이 레코드는 생성 후 수정되지 않으므로 updated_at 없음';