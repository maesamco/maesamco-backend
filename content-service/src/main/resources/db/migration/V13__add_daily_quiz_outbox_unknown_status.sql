-- ============================================================
-- Daily Quiz Outbox UNKNOWN status
-- ============================================================
--
-- Kafka ACK timeout 등으로 실제 전달 여부를 확정할 수 없는 발행이
-- 재시도 한도에 도달하면 자동 재발행을 중단하고 UNKNOWN으로 격리합니다.
-- ============================================================

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
                       'FAILED',
                       'UNKNOWN'
                )
            ),
    ADD CONSTRAINT chk_daily_quiz_event_outboxes_published_at
        CHECK (
            (
                status IN ('PENDING', 'IN_PROGRESS', 'FAILED', 'UNKNOWN')
                    AND published_at IS NULL
                )
                OR
            (
                status = 'PUBLISHED'
                    AND published_at IS NOT NULL
                )
            );
