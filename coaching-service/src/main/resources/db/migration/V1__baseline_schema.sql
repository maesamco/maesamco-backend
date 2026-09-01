-- coaching_schema 초기 스키마 (Flyway V1 베이스라인)
-- 원본: 매삼코_ERD.sql (coaching_schema 섹션) — 매삼코_DB_테이블_명세.md와 100% 동기화된 검증본에서 분리
-- 서비스별 물리적으로 독립된 PostgreSQL 인스턴스를 쓰므로(팀 컨벤션 16절), 이 파일은
-- coaching_schema 스키마와 그 소유 테이블만 담는다 — 다른 서비스 스키마는 만들지 않는다.
-- 크로스 서비스 참조는 전부 "논리 FK"(물리 제약 없는 UUID 컬럼)로, 이 파일엔 등장하지 않는다.

CREATE SCHEMA IF NOT EXISTS coaching_schema;

CREATE TABLE coaching_schema.p_coaching_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    submission_id UUID NOT NULL,
    user_id UUID NOT NULL,
    problem_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS' CHECK (status IN ('IN_PROGRESS','COMPLETED')),
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    UNIQUE (submission_id)
);
COMMENT ON TABLE coaching_schema.p_coaching_sessions IS '제출 1건당 코칭 세션(상위 엔티티) — id가 coachingId';
COMMENT ON COLUMN coaching_schema.p_coaching_sessions.submission_id IS '논리 FK → Judge Service p_submissions.id (1:1)';
COMMENT ON COLUMN coaching_schema.p_coaching_sessions.completed_at IS '역질문 답변까지 완료된 시각 — 스트릭 반영 기준';

CREATE TABLE coaching_schema.p_hints (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    coaching_session_id UUID NOT NULL,
    stage INT NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (coaching_session_id, stage),
    FOREIGN KEY (coaching_session_id) REFERENCES coaching_schema.p_coaching_sessions(id)
);
COMMENT ON TABLE coaching_schema.p_hints IS '오답 단계별 힌트(1~4단계)';

CREATE TABLE coaching_schema.p_explanations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    coaching_session_id UUID NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (coaching_session_id),
    FOREIGN KEY (coaching_session_id) REFERENCES coaching_schema.p_coaching_sessions(id)
);
COMMENT ON TABLE coaching_schema.p_explanations IS '정답 제출에 대한 60초 설명 — MVP는 텍스트만 지원';

CREATE TABLE coaching_schema.p_follow_up_questions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    explanation_id UUID NOT NULL,
    question_text TEXT NOT NULL,
    category VARCHAR(30),
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (explanation_id),
    FOREIGN KEY (explanation_id) REFERENCES coaching_schema.p_explanations(id)
);
COMMENT ON TABLE coaching_schema.p_follow_up_questions IS 'AI 역질문 — MVP는 설명 1건당 역질문 1건';

CREATE TABLE coaching_schema.p_follow_up_answers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    follow_up_question_id UUID NOT NULL,
    answer_text TEXT NOT NULL,
    answered_at TIMESTAMPTZ NOT NULL,
    UNIQUE (follow_up_question_id),
    FOREIGN KEY (follow_up_question_id) REFERENCES coaching_schema.p_follow_up_questions(id)
);
COMMENT ON TABLE coaching_schema.p_follow_up_answers IS '역질문에 대한 사용자 답변 — 미답변은 행 자체가 없음(나중에 이어서 답변 가능)';

CREATE TABLE coaching_schema.p_ai_feedbacks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    coaching_session_id UUID NOT NULL,
    understood_concepts JSONB NOT NULL,
    explanation_gaps JSONB NOT NULL,
    weak_concepts JSONB NOT NULL,
    syntax_to_improve JSONB,
    recommended_problems JSONB,
    next_direction TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (coaching_session_id),
    FOREIGN KEY (coaching_session_id) REFERENCES coaching_schema.p_coaching_sessions(id)
);
COMMENT ON TABLE coaching_schema.p_ai_feedbacks IS 'AI 종합 이해도 피드백';
COMMENT ON COLUMN coaching_schema.p_ai_feedbacks.weak_concepts IS '요약 값 — 상세 집계는 p_weak_concepts';

CREATE TABLE coaching_schema.p_weak_concepts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    concept_tag VARCHAR(50) NOT NULL,
    occurrence_count INT NOT NULL DEFAULT 1,
    last_detected_at TIMESTAMPTZ NOT NULL,
    improved BOOLEAN NOT NULL DEFAULT false,
    UNIQUE (user_id, concept_tag)
);
COMMENT ON TABLE coaching_schema.p_weak_concepts IS '사용자별 취약 개념 집계 — 사용자-개념당 1행, 발견 시 count만 증가';
COMMENT ON COLUMN coaching_schema.p_weak_concepts.user_id IS '논리 FK → User Service p_users.id';

CREATE TABLE coaching_schema.p_ai_call_histories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    coaching_session_id UUID NOT NULL,
    purpose VARCHAR(30) NOT NULL CHECK (purpose IN ('HINT','FOLLOWUP_QUESTION','FEEDBACK')),
    model_name VARCHAR(50) NOT NULL,
    prompt_version VARCHAR(20) NOT NULL,
    request_status VARCHAR(20) NOT NULL,
    called_at TIMESTAMPTZ NOT NULL,
    response_time_ms INT,
    token_usage INT,
    failure_reason TEXT,
    retry_count INT NOT NULL DEFAULT 0,
    FOREIGN KEY (coaching_session_id) REFERENCES coaching_schema.p_coaching_sessions(id)
);
COMMENT ON TABLE coaching_schema.p_ai_call_histories IS '힌트·역질문·피드백 AI 호출 이력';
