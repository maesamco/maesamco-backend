-- ============================================================
-- V18: 현재 Problem 도메인이 지원하지 않는
--      legacy non-CODE 문제를 안전하게 격리
-- ============================================================
--
-- V1에서는 SHORT_ANSWER / FILL_IN_BLANK / MULTIPLE_CHOICE를
-- p_problems에 저장할 수 있었지만 현재 정식 Problem 도메인은
-- CODE 유형만 지원한다.
--
-- 기존 non-CODE 데이터를 CODE로 변환하면 문제의 의미가 달라질 수 있으므로
-- 삭제하거나 강제 변환하지 않고 ARCHIVED 상태로 보존한다.
--
-- 이후 신규/활성 문제는 CODE만 허용한다.
-- ============================================================


-- 1. 삭제되지 않은 legacy non-CODE 문제는 ARCHIVED 상태로 격리한다.
UPDATE content_schema.p_problems
SET problem_status = 'ARCHIVED',
    updated_at = CURRENT_TIMESTAMP
WHERE type <> 'CODE'
  AND deleted_at IS NULL
  AND problem_status <> 'ARCHIVED';


-- 2. 활성 상태의 non-CODE 문제가 다시 생성되지 않도록 방어한다.
--
-- 과거 데이터 보존을 위해 아래 두 경우는 허용한다.
-- - ARCHIVED 상태
-- - 이미 soft delete 된 데이터
ALTER TABLE content_schema.p_problems
DROP CONSTRAINT IF EXISTS ck_problems_active_type_supported;

ALTER TABLE content_schema.p_problems
    ADD CONSTRAINT ck_problems_active_type_supported
        CHECK (
            type = 'CODE'
                OR problem_status = 'ARCHIVED'
                OR deleted_at IS NOT NULL
            );


COMMENT ON CONSTRAINT ck_problems_active_type_supported
    ON content_schema.p_problems
    IS '현재 활성 Problem 도메인은 CODE만 지원한다. legacy non-CODE 문제는 ARCHIVED 또는 삭제 상태로만 보존한다.';
