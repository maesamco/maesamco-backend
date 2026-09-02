-- Daily Quiz 문제은행에서 ACTIVE 문항을 개념 태그로 재사용 조회할 때 사용한다.
-- 기본 jsonb_ops GIN 인덱스는 JSONB 배열의 ?| 연산자를 지원한다.
CREATE INDEX idx_daily_quiz_questions_active_concept_tags
    ON content_schema.p_daily_quiz_questions
    USING GIN (concept_tags)
    WHERE status = 'ACTIVE';
