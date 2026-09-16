-- ============================================================
-- Daily Quiz Event Outbox
-- ============================================================
--
-- 마지막 Daily Quiz 문항 제출 트랜잭션에서
--
-- Daily Quiz Attempt COMPLETED 상태 전이
-- + DailyQuizCompleted Outbox 저장
--
-- 을 하나의 DB 트랜잭션으로 처리하기 위한 Outbox 테이블입니다.
--
-- Kafka 발행은 후속 작업에서 별도의 Relay Worker가 수행합니다.
-- ============================================================

CREATE TABLE content_schema.p_daily_quiz_event_outboxes
(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Kafka 이벤트 자체의 고유 식별자입니다.
    -- Kafka 재시도 시에도 동일한 event_id를 유지합니다.
    event_id UUID NOT NULL,

    -- Daily Quiz Aggregate에서 발생한 이벤트임을 나타냅니다.
    aggregate_type VARCHAR(50) NOT NULL,

    -- DailyQuizCompleted 이벤트에서는 quizAttemptId가 저장됩니다.
    aggregate_id UUID NOT NULL,

    -- 현재 저장하는 이벤트 유형은 DAILY_QUIZ_COMPLETED입니다.
    event_type VARCHAR(50) NOT NULL,

    -- 이벤트 payload 계약의 버전입니다.
    event_version INTEGER NOT NULL,

    -- 직렬화된 DailyQuizCompleted 이벤트 전체 JSON입니다.
    payload JSONB NOT NULL,

    -- PENDING
    -- PUBLISHED
    -- FAILED
    status VARCHAR(20) NOT NULL,

    -- Kafka 발행 실패 후 재시도 횟수입니다.
    retry_count INTEGER NOT NULL DEFAULT 0,

    -- DailyQuizCompleted 이벤트가 발생한 시각입니다.
    occurred_at TIMESTAMPTZ NOT NULL,

    -- Kafka 발행 완료 시각입니다.
    -- PENDING/FAILED 상태에서는 NULL이고 PUBLISHED가 되면 기록됩니다.
    published_at TIMESTAMPTZ,

    -- 마지막 Kafka 발행 실패 원인의 안전한 요약입니다.
    last_error TEXT,

    CONSTRAINT uk_daily_quiz_event_outboxes_event_id
        UNIQUE (event_id),

    -- 하나의 Daily Quiz Attempt 완료 이벤트가 서로 다른 event_id로
    -- 중복 생성되는 상황까지 DB에서 방지합니다.
    CONSTRAINT uk_daily_quiz_event_outboxes_event_type_aggregate_id
        UNIQUE (event_type, aggregate_id),

    CONSTRAINT chk_daily_quiz_event_outboxes_event_version
        CHECK (event_version > 0),

    CONSTRAINT chk_daily_quiz_event_outboxes_retry_count
        CHECK (retry_count >= 0),

    CONSTRAINT chk_daily_quiz_event_outboxes_status
        CHECK (
            status IN (
                       'PENDING',
                       'PUBLISHED',
                       'FAILED'
                )
            ),

    CONSTRAINT chk_daily_quiz_event_outboxes_published_at
        CHECK (
            (
                status IN ('PENDING', 'FAILED')
                    AND published_at IS NULL
                )
                OR
            (
                status = 'PUBLISHED'
                    AND published_at IS NOT NULL
                )
            )
);


-- ============================================================
-- Relay 조회 인덱스
-- ============================================================

CREATE INDEX idx_daily_quiz_event_outboxes_status
    ON content_schema.p_daily_quiz_event_outboxes (status);


-- ============================================================
-- Relay 순차 처리 인덱스
-- ============================================================
--
-- 후속 Relay가 PENDING 이벤트를 발생 순서대로 조회할 수 있도록 합니다.
-- ============================================================

CREATE INDEX idx_daily_quiz_event_outboxes_status_occurred_at
    ON content_schema.p_daily_quiz_event_outboxes (
                                                   status,
                                                   occurred_at
        );


COMMENT ON TABLE content_schema.p_daily_quiz_event_outboxes IS
    'Daily Quiz 도메인 Kafka 이벤트 발행을 위한 Transactional Outbox';

COMMENT ON COLUMN content_schema.p_daily_quiz_event_outboxes.event_id IS
    'Kafka 이벤트 고유 ID. 재시도 시에도 동일하게 유지';

COMMENT ON COLUMN content_schema.p_daily_quiz_event_outboxes.aggregate_id IS
    'DailyQuizCompleted 이벤트의 quizAttemptId';

COMMENT ON COLUMN content_schema.p_daily_quiz_event_outboxes.payload IS
    '직렬화된 DailyQuizCompleted 이벤트 JSON';

COMMENT ON COLUMN content_schema.p_daily_quiz_event_outboxes.retry_count IS
    'Kafka 발행 실패 후 재시도 횟수';

COMMENT ON COLUMN content_schema.p_daily_quiz_event_outboxes.last_error IS
    '마지막 발행 실패 원인의 안전한 요약';
