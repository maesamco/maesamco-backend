-- content_schema 초기 스키마 (Flyway V1 베이스라인)
-- 원본: 매삼코_ERD.sql (content_schema 섹션) — 매삼코_DB_테이블_명세.md와 100% 동기화된 검증본에서 분리
-- 서비스별 물리적으로 독립된 PostgreSQL 인스턴스를 쓰므로(팀 컨벤션 16절), 이 파일은
-- content_schema 스키마와 그 소유 테이블만 담는다 — 다른 서비스 스키마는 만들지 않는다.
-- 크로스 서비스 참조는 전부 "논리 FK"(물리 제약 없는 UUID 컬럼)로, 이 파일엔 등장하지 않는다.

CREATE SCHEMA IF NOT EXISTS content_schema;

CREATE TABLE content_schema.p_chapters (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(100) NOT NULL,
    display_order INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID NOT NULL,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID
);
COMMENT ON TABLE content_schema.p_chapters IS '학습 챕터';

CREATE TABLE content_schema.p_units (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chapter_id UUID NOT NULL,
    title VARCHAR(100) NOT NULL,
    display_order INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID NOT NULL,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID,
    FOREIGN KEY (chapter_id) REFERENCES content_schema.p_chapters(id)
);
COMMENT ON TABLE content_schema.p_units IS '학습 유닛';

CREATE TABLE content_schema.p_lessons (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    unit_id UUID NOT NULL,
    title VARCHAR(100) NOT NULL,
    content TEXT NOT NULL,
    display_order INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID NOT NULL,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID,
    FOREIGN KEY (unit_id) REFERENCES content_schema.p_units(id)
);
COMMENT ON TABLE content_schema.p_lessons IS '레슨';
COMMENT ON COLUMN content_schema.p_lessons.content IS '개념 설명·예시 코드';

CREATE TABLE content_schema.p_concepts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(50) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID NOT NULL,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID
);
COMMENT ON TABLE content_schema.p_concepts IS 'Java 개념 마스터 (조건문/반복문/배열/문자열/메서드/컬렉션/예외처리 등)';

CREATE TABLE content_schema.p_lesson_concepts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lesson_id UUID NOT NULL,
    concept_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID NOT NULL,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID,
    UNIQUE (lesson_id, concept_id),
    FOREIGN KEY (lesson_id) REFERENCES content_schema.p_lessons(id),
    FOREIGN KEY (concept_id) REFERENCES content_schema.p_concepts(id)
);
COMMENT ON TABLE content_schema.p_lesson_concepts IS '레슨-개념 매핑(다대다)';

CREATE TABLE content_schema.p_problems (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    problem_type VARCHAR(20) NOT NULL CHECK (problem_type IN ('CODE','SHORT_ANSWER','FILL_IN_BLANK','MULTIPLE_CHOICE')),
    timer_policy VARCHAR(20) NOT NULL CHECK (timer_policy IN ('THINKING','QUICK_ANSWER')),
    difficulty VARCHAR(20) NOT NULL,
    source VARCHAR(20) NOT NULL CHECK (source IN ('HUMAN_AUTHORED','AI_ASSISTED')),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','VALIDATING','VALIDATION_FAILED','REVIEW_PENDING','PUBLISHED','ARCHIVED')),
    review_note TEXT,
    starter_code TEXT,
    time_limit_ms INT,
    memory_limit_mb INT,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID NOT NULL,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID
);
COMMENT ON TABLE content_schema.p_problems IS 'Java 문제(정식 CODE 문제 + SHORT_ANSWER 등)';
COMMENT ON COLUMN content_schema.p_problems.timer_policy IS 'THINKING=타이머 없음, QUICK_ANSWER=타이머 있음 (기획안 7-3절)';
COMMENT ON COLUMN content_schema.p_problems.status IS '발행된 문제는 직접 수정하지 않고 p_problem_versions에 새 버전으로 등록';

CREATE TABLE content_schema.p_problem_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    problem_id UUID NOT NULL,
    version_no INT NOT NULL,
    content_snapshot JSONB NOT NULL,
    published_at TIMESTAMPTZ NOT NULL,
    created_by UUID NOT NULL,
    UNIQUE (problem_id, version_no),
    FOREIGN KEY (problem_id) REFERENCES content_schema.p_problems(id)
);
COMMENT ON TABLE content_schema.p_problem_versions IS '문제 버전 스냅샷 — id가 problemVersionId, Judge Service가 채점 기준으로 참조';

CREATE TABLE content_schema.p_problem_concepts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    problem_id UUID NOT NULL,
    concept_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID NOT NULL,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID,
    UNIQUE (problem_id, concept_id),
    FOREIGN KEY (problem_id) REFERENCES content_schema.p_problems(id),
    FOREIGN KEY (concept_id) REFERENCES content_schema.p_concepts(id)
);
COMMENT ON TABLE content_schema.p_problem_concepts IS '문제-개념 태그 매핑(다대다)';

CREATE TABLE content_schema.p_test_cases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    problem_id UUID NOT NULL,
    is_public BOOLEAN NOT NULL DEFAULT false,
    input TEXT NOT NULL,
    expected_output TEXT NOT NULL,
    display_order INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID NOT NULL,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID,
    FOREIGN KEY (problem_id) REFERENCES content_schema.p_problems(id)
);
COMMENT ON TABLE content_schema.p_test_cases IS '공개·비공개 테스트케이스';
COMMENT ON COLUMN content_schema.p_test_cases.is_public IS 'false(비공개)는 학습자용 API 응답 DTO에서 원천 제외';

CREATE TABLE content_schema.p_daily_quiz_questions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quiz_question_group_id UUID NOT NULL,
    version_no INT NOT NULL DEFAULT 1,
    problem_type VARCHAR(20) NOT NULL CHECK (problem_type IN ('SHORT_ANSWER','FILL_IN_BLANK','MULTIPLE_CHOICE')),
    question_text TEXT NOT NULL,
    choices JSONB,
    answer VARCHAR(200) NOT NULL,
    allowed_answer_variants JSONB,
    concept_tags JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','FLAGGED','DISABLED')),
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID NOT NULL,
    UNIQUE (quiz_question_group_id, version_no)
);
COMMENT ON TABLE content_schema.p_daily_quiz_questions IS '일일 추천 퀴즈 문제은행 — 특정 버전 1건 = 1행, 재사용됨';
COMMENT ON COLUMN content_schema.p_daily_quiz_questions.quiz_question_group_id IS '버전이 달라져도 동일 논리 문제를 묶는 식별자';
COMMENT ON COLUMN content_schema.p_daily_quiz_questions.concept_tags IS '문제은행 재사용 조회 구현 시 후속 Flyway 마이그레이션으로 GIN 인덱스 추가';

CREATE UNIQUE INDEX uq_daily_quiz_questions_active_group
    ON content_schema.p_daily_quiz_questions (quiz_question_group_id)
    WHERE status = 'ACTIVE';

CREATE TABLE content_schema.p_daily_quiz_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    daily_quiz_question_id UUID NOT NULL,
    reporter_user_id UUID NOT NULL,
    reason TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    resolved_by UUID,
    UNIQUE (daily_quiz_question_id, reporter_user_id),
    FOREIGN KEY (daily_quiz_question_id) REFERENCES content_schema.p_daily_quiz_questions(id)
);
COMMENT ON TABLE content_schema.p_daily_quiz_reports IS '일일 퀴즈 문제 신고 이력';
COMMENT ON COLUMN content_schema.p_daily_quiz_reports.reporter_user_id IS '논리 FK → User Service p_users.id';

CREATE TABLE content_schema.p_daily_quiz_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    attempt_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('READY','IN_PROGRESS','COMPLETED')),
    correct_count INT,
    total_count INT NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    UNIQUE (user_id, attempt_date)
);
COMMENT ON TABLE content_schema.p_daily_quiz_attempts IS '사용자별 일일 퀴즈 응시(하루 1회) — id가 quizAttemptId';
COMMENT ON COLUMN content_schema.p_daily_quiz_attempts.user_id IS '논리 FK → User Service p_users.id';
COMMENT ON COLUMN content_schema.p_daily_quiz_attempts.started_at IS '실제 첫 시작 시각 — READY 상태에선 NULL';

CREATE TABLE content_schema.p_daily_quiz_attempt_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quiz_attempt_id UUID NOT NULL,
    daily_quiz_question_id UUID NOT NULL,
    user_answer VARCHAR(200),
    is_correct BOOLEAN,
    question_order INT NOT NULL,
    answered_at TIMESTAMPTZ,
    UNIQUE (quiz_attempt_id, daily_quiz_question_id),
    UNIQUE (quiz_attempt_id, question_order),
    FOREIGN KEY (quiz_attempt_id) REFERENCES content_schema.p_daily_quiz_attempts(id),
    FOREIGN KEY (daily_quiz_question_id) REFERENCES content_schema.p_daily_quiz_questions(id)
);
COMMENT ON TABLE content_schema.p_daily_quiz_attempt_items IS '일일 퀴즈 세트 내 문항별 응답';
COMMENT ON COLUMN content_schema.p_daily_quiz_attempt_items.user_answer IS '새벽 배치가 미리 행을 만들어둠 — 안 푼 문항은 답/정오답/시각이 전부 NULL';

CREATE TABLE content_schema.p_ai_generation_histories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purpose VARCHAR(30) NOT NULL CHECK (purpose IN ('PROBLEM_GENERATION','DAILY_QUIZ_GENERATION')),
    related_id UUID,
    model_name VARCHAR(50) NOT NULL,
    prompt_version VARCHAR(20) NOT NULL,
    request_status VARCHAR(20) NOT NULL,
    called_at TIMESTAMPTZ NOT NULL,
    response_time_ms INT,
    token_usage INT,
    failure_reason TEXT,
    retry_count INT NOT NULL DEFAULT 0
);
COMMENT ON TABLE content_schema.p_ai_generation_histories IS 'AI 문제 생성·일일 퀴즈 생성 호출 이력';
COMMENT ON COLUMN content_schema.p_ai_generation_histories.related_id IS 'purpose로 대상 테이블 판별하는 다형성 논리 참조 — 생성 실패로 대상 행이 없으면 NULL';
