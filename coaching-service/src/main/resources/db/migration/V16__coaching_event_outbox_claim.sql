-- ============================================================
-- Coaching Event Outbox claim (이슈 #261)
-- ============================================================
--
-- 여러 Relay 인스턴스가 같은 Outbox를 동시에 Kafka로 발행하지 않도록
-- 짧은 트랜잭션에서 IN_PROGRESS 상태와 lease를 기록합니다.
-- claim_id는 lease 만료 후 재선점된 행에 이전 Worker가 늦게 결과를
-- 기록하는 것을 방지하는 fencing token 역할을 합니다.
--
-- content-service DailyQuizEventOutbox(PR #244), judge_schema.
-- p_submission_event_outboxes(이슈 #63)와 동일한 설계입니다.
-- ============================================================

ALTER TABLE coaching_schema.p_coaching_event_outboxes
    ADD COLUMN lease_until TIMESTAMPTZ,
    ADD COLUMN claim_id UUID;

ALTER TABLE coaching_schema.p_coaching_event_outboxes
    DROP CONSTRAINT IF EXISTS p_coaching_event_outboxes_status_check;

ALTER TABLE coaching_schema.p_coaching_event_outboxes
    ADD CONSTRAINT p_coaching_event_outboxes_status_check
        CHECK (
            status IN (
                       'PENDING',
                       'IN_PROGRESS',
                       'COMPLETED',
                       'FAILED'
                )
            ),
    ADD CONSTRAINT chk_coaching_event_outboxes_claim
        CHECK (
            (
                status = 'IN_PROGRESS'
                    AND lease_until IS NOT NULL
                    AND claim_id IS NOT NULL
                )
                OR
            (
                status <> 'IN_PROGRESS'
                    AND lease_until IS NULL
                    AND claim_id IS NULL
                )
            );

-- Relay의 선점 조회(status, next_attempt_at, lease_until 조합)를 위한 복합 인덱스.
-- 기존 idx_coaching_event_outboxes_status_created_at(status, created_at)은 남겨둔다 —
-- 이 인덱스가 대체하는 건 조회 조건이지 정렬 기준(created_at)이 아니라서, 두 인덱스의
-- 선두 컬럼(status)만 겹치고 완전한 상위집합 관계는 아니다.
CREATE INDEX idx_coaching_event_outboxes_claimable
    ON coaching_schema.p_coaching_event_outboxes (
        status,
        next_attempt_at,
        lease_until,
        created_at
    );

COMMENT ON COLUMN coaching_schema.p_coaching_event_outboxes.lease_until IS
    'Relay Worker의 발행 선점 만료 시각. 만료된 IN_PROGRESS 행은 재선점 가능';

COMMENT ON COLUMN coaching_schema.p_coaching_event_outboxes.claim_id IS
    '현재 발행 시도를 식별하고 이전 Worker의 늦은 상태 변경을 막는 선점 ID';
