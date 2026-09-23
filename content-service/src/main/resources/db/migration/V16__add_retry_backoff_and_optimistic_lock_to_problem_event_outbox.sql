-- ============================================================
-- 1. ProblemEventOutbox 낙관적 락 버전 컬럼 추가
--    기존 데이터는 lock_version = 0으로 초기화
-- ============================================================

ALTER TABLE p_problem_event_outboxes
    ADD COLUMN lock_version BIGINT NOT NULL DEFAULT 0;


-- ============================================================
-- 2. ProblemEventOutbox 재시도 가능 시각 컬럼 추가
--    기존 데이터는 NULL로 유지하여 즉시 폴링 가능 상태로 처리
-- ============================================================

ALTER TABLE p_problem_event_outboxes
    ADD COLUMN next_attempt_at TIMESTAMPTZ;


-- ============================================================
-- 3. ProblemEventOutbox Relay 폴링 조회 인덱스 보강
--    PENDING + next_attempt_at 조건과 occurred_at, id 정렬 최적화
-- ============================================================

CREATE INDEX idx_problem_event_outboxes_pollable
    ON p_problem_event_outboxes (
                                 status,
                                 next_attempt_at,
                                 occurred_at,
                                 id
        );