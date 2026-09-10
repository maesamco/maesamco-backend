ALTER TABLE judge_schema.p_pending_judge0_executions
    ADD COLUMN is_public BOOLEAN NOT NULL DEFAULT true;

COMMENT ON COLUMN judge_schema.p_pending_judge0_executions.is_public
    IS '해당 대기 중인 실행이 공개 테스트케이스인지 여부 — 결과 반영 시 SubmissionTestResult.isPublic으로 그대로 전달됨';