-- ============================================================
-- Daily Quiz Outbox Relay claim
-- ============================================================
--
-- 여러 Relay 인스턴스가 같은 Outbox를 동시에 Kafka로 발행하지 않도록
-- 짧은 트랜잭션에서 IN_PROGRESS 상태와 lease를 기록합니다.
-- claim_id는 lease 만료 후 재선점된 행에 이전 Worker가 늦게 결과를
-- 기록하는 것을 방지하는 fencing token 역할을 합니다.
-- ============================================================

ALTER TABLE content_schema.p_daily_quiz_event_outboxes
    ADD COLUMN lease_until TIMESTAMPTZ,
    ADD COLUMN claim_id UUID;

ALTER TABLE content_schema.p_daily_quiz_event_outboxes
    DROP CONSTRAINT chk_daily_quiz_event_outboxes_status,
    DROP CONSTRAINT chk_daily_quiz_event_outboxes_published_at;

ALTER TABLE content_schema.p_daily_quiz_event_outboxes
    ADD CONSTRAINT chk_daily_quiz_event_outboxes_status
        CHECK (
            status IN (
                       'PENDING',
                       'IN_PROGRESS',
                       'PUBLISHED',
                       'FAILED'
                )
            ),
    ADD CONSTRAINT chk_daily_quiz_event_outboxes_published_at
        CHECK (
            (
                status IN ('PENDING', 'IN_PROGRESS', 'FAILED')
                    AND published_at IS NULL
                )
                OR
            (
                status = 'PUBLISHED'
                    AND published_at IS NOT NULL
                )
            ),
    ADD CONSTRAINT chk_daily_quiz_event_outboxes_claim
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

CREATE INDEX idx_daily_quiz_event_outboxes_claimable
    ON content_schema.p_daily_quiz_event_outboxes (
        status,
        next_attempt_at,
        lease_until,
        occurred_at
    );

COMMENT ON COLUMN content_schema.p_daily_quiz_event_outboxes.lease_until IS
    'Relay Worker의 발행 선점 만료 시각. 만료된 IN_PROGRESS 행은 재선점 가능';

COMMENT ON COLUMN content_schema.p_daily_quiz_event_outboxes.claim_id IS
    '현재 발행 시도를 식별하고 이전 Worker의 늦은 상태 변경을 막는 선점 ID';
