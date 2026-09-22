-- ============================================================
-- V15: ProblemProgress 상태, 최초 채점 시각 및 제출 시도 번호 정렬
-- ============================================================
--
-- ProblemProgress 명세에 맞게 p_problem_progress를 수정합니다.
--
-- 변경 사항
--   progress_status
--     기존: NOT_ATTEMPTED, WRONG, CORRECT
--     변경: WRONG, CORRECT
--
--   created_at
--     신규 추가
--     TIMESTAMPTZ NOT NULL
--     신규 데이터는 (user_id, problem_id) 기준 최초 채점 결과의 judgedAt 저장
--     기존 데이터는 CURRENT_TIMESTAMP로 초기화
--
--   attempt_no
--     신규 추가
--     INT NOT NULL
--     신규 데이터는 마지막으로 반영된 제출 시도 번호 저장
--     기존 데이터는 1로 초기화
--
--   lock_version
--     신규 추가
--     BIGINT NOT NULL
--     동일 ProblemProgress에 대한 동시 갱신 충돌 감지에 사용
--     기존 데이터는 1로 초기화
--
-- 기존 유지
--   id
--   user_id
--   problem_id
--   version_no
--   solved_at
--   UNIQUE (user_id, problem_id)
--
-- ============================================================


-- ============================================================
-- 1. 기존 progress_status CHECK 제약 제거
-- ============================================================

ALTER TABLE content_schema.p_problem_progress
    DROP CONSTRAINT IF EXISTS ck_p_problem_progress_status;


-- ============================================================
-- 2. 기존 NOT_ATTEMPTED 데이터 정리
-- ============================================================
--
-- 신규 ProblemProgress 상태는 WRONG, CORRECT만 허용하므로
-- 더 이상 유효하지 않은 NOT_ATTEMPTED row만 제거합니다.
--
-- 기존 WRONG/CORRECT 데이터는 유지합니다.
-- ============================================================

DELETE FROM content_schema.p_problem_progress
WHERE progress_status = 'NOT_ATTEMPTED';


-- ============================================================
-- 3. progress_status CHECK 제약 재생성
-- ============================================================

ALTER TABLE content_schema.p_problem_progress
    ADD CONSTRAINT ck_p_problem_progress_status
        CHECK (
            progress_status IN (
                                'WRONG',
                                'CORRECT'
                )
            );


-- ============================================================
-- 4. created_at 컬럼 추가
-- ============================================================
--
-- 신규 ProblemProgress에서 created_at은 단순 DB INSERT 시각이 아니라
-- (user_id, problem_id) 기준 최초 채점 결과의 judgedAt을 의미합니다.
--
-- 기존 데이터는 최초 judgedAt을 복원할 수 없으므로
-- 마이그레이션 시점의 CURRENT_TIMESTAMP로 초기화합니다.
--
-- 신규 데이터는 Application 계층에서 실제 judgedAt을 저장합니다.
-- ============================================================

ALTER TABLE content_schema.p_problem_progress
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ;


UPDATE content_schema.p_problem_progress
SET created_at = CURRENT_TIMESTAMP
WHERE created_at IS NULL;


ALTER TABLE content_schema.p_problem_progress
    ALTER COLUMN created_at DROP DEFAULT;


ALTER TABLE content_schema.p_problem_progress
    ALTER COLUMN created_at SET NOT NULL;


-- ============================================================
-- 5. attempt_no 컬럼 추가
-- ============================================================
--
-- attempt_no는 현재 ProblemProgress에 마지막으로 반영된
-- Submission의 제출 시도 번호를 저장합니다.
--
-- 기존 데이터에서는 실제 attemptNo를 복원할 수 없으므로
-- 마이그레이션 시 1로 초기화합니다.
--
-- 이후 수신되는 SubmissionJudged 이벤트부터
-- 실제 attemptNo를 기준으로 갱신합니다.
-- ============================================================

ALTER TABLE content_schema.p_problem_progress
    ADD COLUMN IF NOT EXISTS attempt_no INT;


UPDATE content_schema.p_problem_progress
SET attempt_no = 1
WHERE attempt_no IS NULL;


ALTER TABLE content_schema.p_problem_progress
    ALTER COLUMN attempt_no SET NOT NULL;


-- ============================================================
-- 6. attempt_no CHECK 제약 추가
-- ============================================================

ALTER TABLE content_schema.p_problem_progress
    DROP CONSTRAINT IF EXISTS ck_p_problem_progress_attempt_no;


ALTER TABLE content_schema.p_problem_progress
    ADD CONSTRAINT ck_p_problem_progress_attempt_no
        CHECK (attempt_no >= 1);


-- ============================================================
-- 7. lock_version 컬럼 추가
-- ============================================================
--
-- lock_version은 동일한 ProblemProgress row에 대해
-- 여러 Kafka 이벤트가 동시에 갱신을 시도하는 경우
-- 낙관적 락 충돌을 감지하기 위해 사용합니다.
--
-- 기존 데이터는 마이그레이션 시 1로 초기화합니다.
--
-- Application의 ProblemProgress 엔티티에서는
-- @Version을 통해 이후 값을 관리합니다.
-- ============================================================

ALTER TABLE content_schema.p_problem_progress
    ADD COLUMN IF NOT EXISTS lock_version BIGINT;


UPDATE content_schema.p_problem_progress
SET lock_version = 1
WHERE lock_version IS NULL;


ALTER TABLE content_schema.p_problem_progress
    ALTER COLUMN lock_version SET NOT NULL;


-- ============================================================
-- 8. 컬럼 설명 정리
-- ============================================================

COMMENT ON TABLE content_schema.p_problem_progress
    IS '사용자별 문제 풀이 진행 상태';

COMMENT ON COLUMN content_schema.p_problem_progress.id
    IS '문제 풀이 진행 고유번호';

COMMENT ON COLUMN content_schema.p_problem_progress.user_id
    IS '문제를 풀이한 사용자 식별자';

COMMENT ON COLUMN content_schema.p_problem_progress.problem_id
    IS '문제 식별자';

COMMENT ON COLUMN content_schema.p_problem_progress.version_no
    IS '마지막으로 반영된 채점 결과의 문제 버전 번호';

COMMENT ON COLUMN content_schema.p_problem_progress.attempt_no
    IS '마지막으로 ProblemProgress에 반영된 제출 시도 번호';

COMMENT ON COLUMN content_schema.p_problem_progress.solved_at
    IS '(user_id, problem_id) 기준 최초 CORRECT 판정의 judgedAt';

COMMENT ON COLUMN content_schema.p_problem_progress.progress_status
    IS '마지막으로 반영된 문제 풀이 상태: WRONG, CORRECT';

COMMENT ON COLUMN content_schema.p_problem_progress.created_at
    IS '(user_id, problem_id) 기준 최초 채점 결과의 judgedAt. 기존 데이터는 V15 적용 시각으로 초기화';

COMMENT ON COLUMN content_schema.p_problem_progress.lock_version
    IS 'ProblemProgress 동시 수정 충돌 감지를 위한 낙관적 락 버전';


-- ============================================================
-- 9. ProblemProgress 조회 인덱스 정렬
-- ============================================================
--
-- 사용자별 ProblemProgress 조회는
-- created_at DESC, id DESC 순으로 정렬합니다.
--
-- 상태 필터가 없는 조회:
--   WHERE user_id = ?
--   ORDER BY created_at DESC, id DESC
--
-- 상태 필터가 있는 조회:
--   WHERE user_id = ?
--     AND progress_status = ?
--   ORDER BY created_at DESC, id DESC
--
-- 동일한 created_at을 가진 row에 대해서도
-- 안정적인 정렬을 보장하도록 id를 tie-breaker로 포함합니다.
-- ============================================================

DROP INDEX IF EXISTS content_schema.idx_p_problem_progress_user_status;

DROP INDEX IF EXISTS content_schema.idx_p_problem_progress_user_created;

DROP INDEX IF EXISTS content_schema.idx_p_problem_progress_user_status_created;

DROP INDEX IF EXISTS content_schema.idx_p_problem_progress_user_created_id;

DROP INDEX IF EXISTS content_schema.idx_p_problem_progress_user_status_created_id;


CREATE INDEX idx_p_problem_progress_user_created_id
    ON content_schema.p_problem_progress (
                                          user_id,
                                          created_at,
                                          id
        );


CREATE INDEX idx_p_problem_progress_user_status_created_id
    ON content_schema.p_problem_progress (
                                          user_id,
                                          progress_status,
                                          created_at,
                                          id
        );


-- ============================================================
-- 10. ProblemEventOutbox Relay 조회 정렬 인덱스 보강
-- ============================================================

DROP INDEX IF EXISTS content_schema.idx_problem_event_outboxes_status_occurred_at;

DROP INDEX IF EXISTS content_schema.idx_problem_event_outboxes_status_occurred_at_id;


CREATE INDEX idx_problem_event_outboxes_status_occurred_at_id
    ON content_schema.p_problem_event_outboxes (
                                                status,
                                                occurred_at,
                                                id
        );