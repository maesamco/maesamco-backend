-- ============================================================
-- V5: p_problems 테이블을 현재 Problem 도메인 모델에 맞게 변경
-- ============================================================
--
-- V1
--   title                  VARCHAR(200)
--   problem_type           VARCHAR(20)
--   status                 VARCHAR(20)
--   time_limit_ms          INT
--   memory_limit_mb        INT
--   review_note            TEXT
--   language               없음
--   current_version_no     없음
--
-- 최종
--   title                  VARCHAR(100)
--   language               VARCHAR(20) NOT NULL
--   type                   VARCHAR(20) NOT NULL
--   problem_status         VARCHAR(20) NOT NULL
--   running_time_limit     VARCHAR(20) NOT NULL
--   running_memory_limit   VARCHAR(20) NOT NULL
--   current_version_no     INTEGER NOT NULL DEFAULT 1
--   review_note            제거
-- ============================================================


-- ============================================================
-- 1. 기존 CHECK 제약 제거
-- ============================================================

ALTER TABLE content_schema.p_problems
DROP CONSTRAINT IF EXISTS p_problems_problem_type_check;

ALTER TABLE content_schema.p_problems
DROP CONSTRAINT IF EXISTS p_problems_timer_policy_check;

ALTER TABLE content_schema.p_problems
DROP CONSTRAINT IF EXISTS p_problems_source_check;

ALTER TABLE content_schema.p_problems
DROP CONSTRAINT IF EXISTS p_problems_status_check;

ALTER TABLE content_schema.p_problems
DROP CONSTRAINT IF EXISTS ck_problems_running_time_limit;

ALTER TABLE content_schema.p_problems
DROP CONSTRAINT IF EXISTS ck_problems_running_memory_limit;


-- ============================================================
-- 2. title VARCHAR(200) -> VARCHAR(100)
-- ============================================================

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM content_schema.p_problems
        WHERE LENGTH(title) > 100
    ) THEN
        RAISE EXCEPTION
            'p_problems.title contains values longer than 100 characters.';
END IF;
END
$$;

ALTER TABLE content_schema.p_problems
ALTER COLUMN title TYPE VARCHAR(100);


-- ============================================================
-- 3. language 추가
-- ============================================================
--
-- 기존 V1 p_problems는 Java 문제를 전제로 한 테이블이므로
-- 기존 데이터는 JAVA로 이관한다.
-- ============================================================

ALTER TABLE content_schema.p_problems
    ADD COLUMN IF NOT EXISTS language VARCHAR(20);

UPDATE content_schema.p_problems
SET language = 'JAVA'
WHERE language IS NULL;

ALTER TABLE content_schema.p_problems
    ALTER COLUMN language SET NOT NULL;


-- ============================================================
-- 4. problem_type -> type
-- ============================================================

ALTER TABLE content_schema.p_problems
    RENAME COLUMN problem_type TO type;


-- ============================================================
-- 5. status -> problem_status
-- ============================================================

ALTER TABLE content_schema.p_problems
    RENAME COLUMN status TO problem_status;


-- ============================================================
-- 6. time_limit_ms -> running_time_limit
-- ============================================================

ALTER TABLE content_schema.p_problems
    RENAME COLUMN time_limit_ms TO running_time_limit;


-- 기존 millisecond 값을 현재 enum 문자열로 변경
ALTER TABLE content_schema.p_problems
ALTER COLUMN running_time_limit TYPE VARCHAR(20)
    USING (
        CASE running_time_limit
            WHEN 1000 THEN 'SECOND_1'
            WHEN 2000 THEN 'SECOND_2'
            WHEN 3000 THEN 'SECOND_3'
            WHEN 5000 THEN 'SECOND_5'
            ELSE running_time_limit::TEXT
        END
    );


-- ============================================================
-- 7. memory_limit_mb -> running_memory_limit
-- ============================================================

ALTER TABLE content_schema.p_problems
    RENAME COLUMN memory_limit_mb TO running_memory_limit;

ALTER TABLE content_schema.p_problems
ALTER COLUMN running_memory_limit TYPE VARCHAR(20)
    USING (
        CASE running_memory_limit
            WHEN 128 THEN 'MB_128'
            WHEN 256 THEN 'MB_256'
            WHEN 512 THEN 'MB_512'
            WHEN 1024 THEN 'MB_1024'
            WHEN 2048 THEN 'MB_2048'
            ELSE running_memory_limit::TEXT
        END
    );


-- ============================================================
-- 8. NULL 데이터 검증
-- ============================================================

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM content_schema.p_problems
        WHERE running_time_limit IS NULL
    ) THEN
        RAISE EXCEPTION
            'p_problems.running_time_limit contains NULL values.';
END IF;

    IF EXISTS (
        SELECT 1
        FROM content_schema.p_problems
        WHERE running_memory_limit IS NULL
    ) THEN
        RAISE EXCEPTION
            'p_problems.running_memory_limit contains NULL values.';
END IF;
END
$$;

ALTER TABLE content_schema.p_problems
    ALTER COLUMN running_time_limit SET NOT NULL;

ALTER TABLE content_schema.p_problems
    ALTER COLUMN running_memory_limit SET NOT NULL;


-- ============================================================
-- 9. timer_policy 값을 현재 enum에 맞게 변환
-- ============================================================
--
-- V1
--   THINKING
--   QUICK_ANSWER
--
-- 현재
--   DEFAULT
--   APPLY60
-- ============================================================

UPDATE content_schema.p_problems
SET timer_policy =
        CASE timer_policy
            WHEN 'THINKING' THEN 'DEFAULT'
            WHEN 'QUICK_ANSWER' THEN 'APPLY60'
            ELSE timer_policy
            END;


-- ============================================================
-- 10. source VARCHAR(20) -> VARCHAR(100)
-- ============================================================

ALTER TABLE content_schema.p_problems
ALTER COLUMN source TYPE VARCHAR(100);


-- ============================================================
-- 11. current_version_no 추가
-- ============================================================

ALTER TABLE content_schema.p_problems
    ADD COLUMN IF NOT EXISTS current_version_no INTEGER;

UPDATE content_schema.p_problems
SET current_version_no = 1
WHERE current_version_no IS NULL;

ALTER TABLE content_schema.p_problems
    ALTER COLUMN current_version_no SET DEFAULT 1;

ALTER TABLE content_schema.p_problems
    ALTER COLUMN current_version_no SET NOT NULL;


-- ============================================================
-- 12. review_note 제거
-- ============================================================

ALTER TABLE content_schema.p_problems
DROP COLUMN IF EXISTS review_note;


-- ============================================================
-- 13. created_at / updated_at 기본값 정렬
-- ============================================================

ALTER TABLE content_schema.p_problems
    ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE content_schema.p_problems
    ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP;


-- ============================================================
-- 14. id 기본값 제거
-- ============================================================
--
-- 최종 정의:
-- id UUID PRIMARY KEY
--
-- V1에서는 DEFAULT gen_random_uuid()가 존재하므로 제거한다.
-- ============================================================

ALTER TABLE content_schema.p_problems
    ALTER COLUMN id DROP DEFAULT;


-- ============================================================
-- 15. 컬럼 설명
-- ============================================================

COMMENT ON TABLE content_schema.p_problems
    IS '코딩 및 객관식/주관식 문제';

COMMENT ON COLUMN content_schema.p_problems.title
    IS '문제 제목. 최대 100자';

COMMENT ON COLUMN content_schema.p_problems.language
    IS '문제 프로그래밍 언어';

COMMENT ON COLUMN content_schema.p_problems.running_time_limit
    IS '실행 시간 제한. RunningTimeLimit enum 값';

COMMENT ON COLUMN content_schema.p_problems.running_memory_limit
    IS '실행 메모리 제한. RunningMemoryLimit enum 값';

COMMENT ON COLUMN content_schema.p_problems.problem_status
    IS '문제 상태';

COMMENT ON COLUMN content_schema.p_problems.current_version_no
    IS '현재 문제 버전 번호';