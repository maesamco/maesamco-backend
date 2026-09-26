ALTER TABLE content_schema.p_daily_quiz_questions
    ADD COLUMN fallback_eligible BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN content_schema.p_daily_quiz_questions.fallback_eligible
    IS '운영 검수를 마쳐 공통 기본 문제 폴백에 사용할 수 있는 문항';

CREATE INDEX idx_daily_quiz_questions_fallback_active_type
    ON content_schema.p_daily_quiz_questions (problem_type)
    WHERE status = 'ACTIVE' AND fallback_eligible = TRUE;
