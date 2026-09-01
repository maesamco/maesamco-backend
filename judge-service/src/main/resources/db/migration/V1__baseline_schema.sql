-- judge_schema 초기 스키마 (Flyway V1 베이스라인)
-- 원본: 매삼코_ERD.sql (judge_schema 섹션) — 매삼코_DB_테이블_명세.md와 100% 동기화된 검증본에서 분리
-- 서비스별 물리적으로 독립된 PostgreSQL 인스턴스를 쓰므로(팀 컨벤션 16절), 이 파일은
-- judge_schema 스키마와 그 소유 테이블만 담는다 — 다른 서비스 스키마는 만들지 않는다.
-- 크로스 서비스 참조는 전부 "논리 FK"(물리 제약 없는 UUID 컬럼)로, 이 파일엔 등장하지 않는다.

CREATE SCHEMA IF NOT EXISTS judge_schema;

CREATE TABLE judge_schema.p_submissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    problem_id UUID NOT NULL,
    problem_version_id UUID NOT NULL,
    language VARCHAR(20) NOT NULL DEFAULT 'JAVA',
    code TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','QUEUED','RUNNING','RETRY_WAIT','COMPLETED','FAILED')),
    result VARCHAR(30) CHECK (result IN ('CORRECT','WRONG','COMPILE_ERROR','TIME_LIMIT_EXCEEDED','RUNTIME_ERROR','MEMORY_LIMIT_EXCEEDED')),
    failure_code VARCHAR(30) CHECK (failure_code IN ('JUDGE0_RESPONSE_FAILURE','KAFKA_PROCESSING_FAILURE','RESULT_SAVE_FAILURE','INTERNAL_SYSTEM_ERROR')),
    attempt_no INT NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    submitted_at TIMESTAMPTZ NOT NULL,
    judged_at TIMESTAMPTZ,
    execution_time_ms INT,
    memory_used_kb INT,
    -- BaseEntity 감사 컬럼
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID NOT NULL,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID,
    UNIQUE (user_id, problem_id, attempt_no),
    UNIQUE (idempotency_key)
);
COMMENT ON TABLE judge_schema.p_submissions IS '코드/답안 제출 및 채점 상태 — id가 submissionId';
COMMENT ON COLUMN judge_schema.p_submissions.status IS '처리 상태(lifecycle) — 채점 결과는 result, 시스템 실패 원인은 failure_code에 별도 저장';
COMMENT ON COLUMN judge_schema.p_submissions.result IS 'status=COMPLETED가 되기 전까지 NULL';
COMMENT ON COLUMN judge_schema.p_submissions.failure_code IS 'status=FAILED가 되기 전까지 NULL — 채점 시스템 자체 장애 원인';
COMMENT ON COLUMN judge_schema.p_submissions.code IS '로그 노출 금지 대상';

CREATE TABLE judge_schema.p_submission_test_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    submission_id UUID NOT NULL,
    test_case_id UUID NOT NULL,
    is_public BOOLEAN NOT NULL,
    passed BOOLEAN NOT NULL,
    actual_output TEXT,
    error_type VARCHAR(30),
    FOREIGN KEY (submission_id) REFERENCES judge_schema.p_submissions(id)
);
COMMENT ON TABLE judge_schema.p_submission_test_results IS '제출 건별 테스트케이스 채점 결과';
COMMENT ON COLUMN judge_schema.p_submission_test_results.test_case_id IS '논리 FK → Content Service p_test_cases.id';
COMMENT ON COLUMN judge_schema.p_submission_test_results.actual_output IS '공개 테스트만 학습자에 노출';

CREATE INDEX idx_submission_test_results_submission
    ON judge_schema.p_submission_test_results (submission_id);

CREATE TABLE judge_schema.p_problem_execution_specs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    problem_id UUID NOT NULL,
    problem_version_id UUID NOT NULL,
    language VARCHAR(20) NOT NULL,
    starter_code TEXT,
    test_cases JSONB NOT NULL,
    time_limit_ms INT NOT NULL,
    memory_limit_mb INT NOT NULL,
    published_at TIMESTAMPTZ NOT NULL,
    UNIQUE (problem_id, problem_version_id)
);
COMMENT ON TABLE judge_schema.p_problem_execution_specs IS 'ProblemPublished 이벤트 기반 실행 명세 캐시';

CREATE TABLE judge_schema.p_submission_event_outboxes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL CHECK (event_type IN ('JudgeRequested','SubmissionJudged')),
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','COMPLETED')),
    attempt_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,
    FOREIGN KEY (aggregate_id) REFERENCES judge_schema.p_submissions(id)
);
COMMENT ON TABLE judge_schema.p_submission_event_outboxes IS '채점 요청/완료 이벤트 발행용 Outbox';
COMMENT ON COLUMN judge_schema.p_submission_event_outboxes.processed_at IS '릴레이 워커가 이벤트 발행을 완료 처리한 시각';

CREATE INDEX idx_submission_event_outboxes_status
    ON judge_schema.p_submission_event_outboxes (status);