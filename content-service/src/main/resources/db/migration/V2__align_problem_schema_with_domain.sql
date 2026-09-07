-- 현재 Problem 도메인 모델과 기존 V1 p_problems 스키마의 정합성을 맞춥니다.
-- 기존 컬럼의 데이터를 보존하기 위해 삭제/재생성 대신 rename을 사용합니다.
--
-- 주의:
-- V1의 timer_policy(THINKING / QUICK_ANSWER)는 현재 TimerPolicy와
-- 의미상 일대일 대응 관계가 정의되어 있지 않습니다.
-- 따라서 기존 데이터가 존재할 경우 임의 변환하지 않고 migration을 중단합니다.

-- ============================================================
-- 1. 기존 데이터 사전 검증
-- ============================================================

DO $$
BEGIN
    -- 기존 TimerPolicy는 새 정책으로 안전하게 자동 변환할 근거가 없습니다.
    IF EXISTS (
        SELECT 1
        FROM content_schema.p_problems
        WHERE timer_policy IN (
            'THINKING',
            'QUICK_ANSWER'
        )
    ) THEN
        RAISE EXCEPTION
            'Legacy timer_policy data exists. '
            'THINKING/QUICK_ANSWER cannot be safely mapped to the current TimerPolicy.';
END IF;

    -- 기존 time_limit_ms가 실제 밀리초로 저장됐을 가능성과
    -- 현재 도메인의 초 단위 저장을 모두 허용합니다.
    IF EXISTS (
        SELECT 1
        FROM content_schema.p_problems
        WHERE time_limit_ms IS NOT NULL
          AND time_limit_ms NOT IN (
              1,
              2,
              3,
              5,
              1000,
              2000,
              3000,
              5000
          )
    ) THEN
        RAISE EXCEPTION
            'Unsupported legacy time_limit_ms value exists.';
END IF;

    IF EXISTS (
        SELECT 1
        FROM content_schema.p_problems
        WHERE memory_limit_mb IS NOT NULL
          AND memory_limit_mb NOT IN (
              128,
              256,
              512,
              1024,
              2048
          )
    ) THEN
        RAISE EXCEPTION
            'Unsupported legacy memory_limit_mb value exists.';
END IF;
END
$$;


-- ============================================================
-- 2. 기존 CHECK 제약 제거
-- ============================================================

ALTER TABLE content_schema.p_problems
DROP CONSTRAINT IF EXISTS p_problems_problem_type_check;

ALTER TABLE content_schema.p_problems
DROP CONSTRAINT IF EXISTS p_problems_timer_policy_check;

ALTER TABLE content_schema.p_problems
DROP CONSTRAINT IF EXISTS p_problems_source_check;

ALTER TABLE content_schema.p_problems
DROP CONSTRAINT IF EXISTS p_problems_status_check;


-- ============================================================
-- 3. 현재 Problem 엔티티의 컬럼명에 맞춰 변경
-- ============================================================

ALTER TABLE content_schema.p_problems
    RENAME COLUMN problem_type TO type;

ALTER TABLE content_schema.p_problems
    RENAME COLUMN status TO problem_status;

ALTER TABLE content_schema.p_problems
    RENAME COLUMN time_limit_ms TO running_time_limit;

ALTER TABLE content_schema.p_problems
    RENAME COLUMN memory_limit_mb TO running_memory_limit;


-- ============================================================
-- 4. 현재 Problem 도메인에 존재하지만 V1에는 없던 컬럼 추가
-- ============================================================

ALTER TABLE content_schema.p_problems
    ADD COLUMN language VARCHAR(20);

ALTER TABLE content_schema.p_problems
    ADD COLUMN current_version_no INT NOT NULL DEFAULT 1;


-- ============================================================
-- 5. 기존 데이터 보정
-- ============================================================

-- V1은 Java 문제 중심의 기존 스키마이므로
-- 기존 행의 언어를 JAVA로 보정합니다.
UPDATE content_schema.p_problems
SET language = 'JAVA'
WHERE language IS NULL;

ALTER TABLE content_schema.p_problems
    ALTER COLUMN language SET NOT NULL;


-- V1 컬럼명은 time_limit_ms였지만
-- 현재 Problem 도메인은 실행 시간을 초 단위(1/2/3/5)로 저장합니다.
--
-- 기존 값이 실제 밀리초 단위였다면 초 단위로 정규화하고,
-- 이미 초 단위로 저장돼 있었다면 그대로 유지합니다.
UPDATE content_schema.p_problems
SET running_time_limit =
        CASE running_time_limit
            WHEN 1000 THEN 1
            WHEN 2000 THEN 2
            WHEN 3000 THEN 3
            WHEN 5000 THEN 5
            ELSE running_time_limit
            END
WHERE running_time_limit IS NOT NULL;


-- V1에서는 실행 시간/메모리 제한이 NULL 가능했으므로
-- 현재 도메인의 최소 허용값으로 기존 NULL 데이터를 보정합니다.
UPDATE content_schema.p_problems
SET running_time_limit = 1
WHERE running_time_limit IS NULL;

UPDATE content_schema.p_problems
SET running_memory_limit = 128
WHERE running_memory_limit IS NULL;

ALTER TABLE content_schema.p_problems
    ALTER COLUMN running_time_limit SET NOT NULL;

ALTER TABLE content_schema.p_problems
    ALTER COLUMN running_memory_limit SET NOT NULL;


-- ============================================================
-- 6. 현재 enum / 값 객체 기준 CHECK 제약 추가
-- ============================================================

-- ProgrammingLanguage
ALTER TABLE content_schema.p_problems
    ADD CONSTRAINT ck_problems_language
        CHECK (
    language IN (
    'C',
    'CPP',
    'JAVA',
    'PYTHON',
    'ETC'
    )
    );


-- ProblemType
ALTER TABLE content_schema.p_problems
    ADD CONSTRAINT ck_problems_type
        CHECK (
            type IN (
                     'CODE',
                     'SHORT_ANSWER',
                     'LONG_ANSWER',
                     'ONE_CHOICE',
                     'MULTIPLE_CHOICE',
                     'FILL_IN_BLANK'
                )
            );


-- TimerPolicy
ALTER TABLE content_schema.p_problems
    ADD CONSTRAINT ck_problems_timer_policy
        CHECK (
            timer_policy IN (
                             'APPLY60',
                             'APPLY120',
                             'APPLY180',
                             'APPLY300',
                             'NOT_APPLY_TIMEPOLICY'
                )
            );


-- ProblemSource
ALTER TABLE content_schema.p_problems
    ADD CONSTRAINT ck_problems_source
        CHECK (
            source IN (
                       'HUMAN_AUTHORED',
                       'AI_ASSISTED'
                )
            );


-- ProblemStatus
ALTER TABLE content_schema.p_problems
    ADD CONSTRAINT ck_problems_status
        CHECK (
            problem_status IN (
                               'DRAFT',
                               'VALIDATING',
                               'VALIDATION_FAILED',
                               'REVIEW_PENDING',
                               'REJECTED',
                               'ARCHIVED',
                               'PUBLISHED'
                )
            );


-- RunningTimeLimit
ALTER TABLE content_schema.p_problems
    ADD CONSTRAINT ck_problems_running_time_limit
        CHECK (
            running_time_limit IN (
                                   1,
                                   2,
                                   3,
                                   5
                )
            );


-- RunningMemoryLimit
ALTER TABLE content_schema.p_problems
    ADD CONSTRAINT ck_problems_running_memory_limit
        CHECK (
            running_memory_limit IN (
                                     128,
                                     256,
                                     512,
                                     1024,
                                     2048
                )
            );


-- 문제 버전 번호는 항상 1 이상이어야 합니다.
ALTER TABLE content_schema.p_problems
    ADD CONSTRAINT ck_problems_current_version_no
        CHECK (current_version_no >= 1);


-- ============================================================
-- 7. 컬럼 설명
-- ============================================================

COMMENT ON COLUMN content_schema.p_problems.language
    IS '문제 실행 언어';

COMMENT ON COLUMN content_schema.p_problems.title
    IS '문제 제목. 신규 API 입력은 최대 100자로 제한하며 기존 V1 호환을 위해 DB 길이는 VARCHAR(200)을 유지';

COMMENT ON COLUMN content_schema.p_problems.running_time_limit
    IS '코드 실행 제한 시간(초). 허용값: 1, 2, 3, 5';

COMMENT ON COLUMN content_schema.p_problems.running_memory_limit
    IS '코드 실행 메모리 제한(MB)';

COMMENT ON COLUMN content_schema.p_problems.timer_policy
    IS 'APPLY60/120/180/300은 학습 제한 시간(초), NOT_APPLY_TIMEPOLICY는 타이머 미적용';

COMMENT ON COLUMN content_schema.p_problems.problem_status
    IS '문제 발행 생명주기 상태';

COMMENT ON COLUMN content_schema.p_problems.current_version_no
    IS '현재 문제 버전 번호. 최초 버전은 1이며 수정 시 증가';
