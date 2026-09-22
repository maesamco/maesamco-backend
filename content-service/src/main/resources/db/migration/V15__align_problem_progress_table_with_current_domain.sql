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
--     (user_id, problem_id) 기준 최초 채점 결과의 judgedAt을 저장
--
--   attempt_no
--     신규 추가
--     INT NOT NULL
--     마지막으로 ProblemProgress에 반영된 제출 시도 번호를 저장
--
--   lock_version
--     신규 추가
--     BIGINT NOT NULL
--     동일 ProblemProgress에 대한 동시 갱신 충돌 감지에 사용
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
-- 기존 데이터 마이그레이션 정책
-- ============================================================
--
-- 기존 ProblemProgress에는 attempt_no가 존재하지 않으며,
-- Content Service의 기존 데이터만으로 실제 Submission의
-- 제출 시도 번호를 정확하게 복원할 수 없습니다.
--
-- 임의의 attempt_no를 부여할 경우 Kafka에서 늦게 도착한
-- 이전 SubmissionJudged 이벤트를 최신 이벤트로 오인하여
-- version_no, progress_status 등의 최신 상태를 덮어쓸 수 있습니다.
--
-- 따라서 기존 데이터를 임의로 backfill하지 않고
-- V15 적용 시 기존 ProblemProgress 데이터를 초기화합니다.
--
-- 이후 ProblemProgress는 SubmissionJudged 이벤트를 기준으로
-- 새로운 도메인 모델에 맞게 다시 생성됩니다.
-- ============================================================


-- ============================================================
-- 1. 기존 progress_status CHECK 제약 제거
-- ============================================================

ALTER TABLE content_schema.p_problem_progress
    DROP CONSTRAINT IF EXISTS ck_p_problem_progress_status;


-- ============================================================
-- 2. 기존 ProblemProgress 데이터 초기화
-- ============================================================
--
-- 기존 row에는 정확한 attempt_no를 부여할 수 없으므로
-- 새로운 SubmissionJudged 기반 ProblemProgress 모델과
-- 일관성을 보장하기 위해 기존 데이터를 초기화합니다.
--
-- NOT_ATTEMPTED뿐 아니라 WRONG/CORRECT 상태의 기존 row 역시
-- attempt_no를 복원할 수 없으므로 모두 초기화 대상입니다.
-- ============================================================

DELETE FROM content_schema.p_problem_progress;


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
-- created_at은 단순 DB INSERT 시각이 아니라
-- (user_id, problem_id) 기준 최초 채점 결과의 judgedAt을 의미합니다.
--
-- Kafka 이벤트가 순서와 다르게 도착할 수 있으므로,
-- 더 이른 judgedAt을 가진 이벤트가 늦게 도착한 경우
-- Application 계층에서 created_at을 보정할 수 있습니다.
--
-- 기존 ProblemProgress는 위에서 초기화했으므로
-- 임의의 CURRENT_TIMESTAMP backfill을 수행하지 않습니다.
-- ============================================================

ALTER TABLE content_schema.p_problem_progress
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ;


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
-- Kafka 이벤트가 중복되거나 이전 시도의 이벤트가 늦게 도착한 경우,
-- 저장된 attempt_no와 수신한 attemptNo를 비교하여
-- 오래된 이벤트가 version_no, progress_status 등의
-- 최신 상태를 덮어쓰지 않도록 사용합니다.
--
-- 기존 ProblemProgress는 정확한 attempt_no를 복원할 수 없어
-- 위에서 초기화했으므로 임의의 기본값을 사용하지 않습니다.
-- ============================================================

ALTER TABLE content_schema.p_problem_progress
    ADD COLUMN IF NOT EXISTS attempt_no INT;


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
-- Application의 ProblemProgress 엔티티에서 @Version으로 관리하며,
-- UPDATE 시 조회 당시의 lock_version과 DB의 현재 값을 비교합니다.
--
-- 다른 트랜잭션이 먼저 row를 수정한 경우
-- lock_version 불일치로 UPDATE가 실패하고,
-- 해당 Kafka 메시지는 listener 계층의 재시도를 통해
-- 최신 ProblemProgress를 다시 조회한 뒤 재처리합니다.
--
-- 최초 row 생성 경쟁은 lock_version이 아니라
-- 기존 UNIQUE (user_id, problem_id) 제약으로 방어합니다.
-- ============================================================

ALTER TABLE content_schema.p_problem_progress
    ADD COLUMN IF NOT EXISTS lock_version BIGINT NOT NULL DEFAULT 0;


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
    IS '(user_id, problem_id) 기준 최초 채점 결과의 judgedAt';

COMMENT ON COLUMN content_schema.p_problem_progress.lock_version
    IS 'ProblemProgress 동시 수정 충돌 감지를 위한 낙관적 락 버전';


-- ============================================================
-- 9. ProblemEventOutbox Relay 조회 정렬 인덱스 보강
-- ============================================================
--
-- PENDING Outbox 조회 시 occurred_at이 같은 이벤트에 대해
-- id ASC를 tie-breaker로 사용하므로 복합 인덱스에도 id를 포함합니다.
-- ============================================================

DROP INDEX IF EXISTS content_schema.idx_problem_event_outboxes_status_occurred_at;

DROP INDEX IF EXISTS content_schema.idx_problem_event_outboxes_status_occurred_at_id;


CREATE INDEX idx_problem_event_outboxes_status_occurred_at_id
    ON content_schema.p_problem_event_outboxes (
                                                status,
                                                occurred_at,
                                                id
        );