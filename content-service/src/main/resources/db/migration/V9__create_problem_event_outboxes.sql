-- ============================================================
-- Problem Event Outbox
-- ============================================================
--
-- 문제 발행 승인 트랜잭션에서
--
-- ProblemVersion 확정
-- + Problem PUBLISHED 상태 전이
-- + ProblemPublished Outbox 저장
--
-- 을 하나의 DB 트랜잭션으로 처리하기 위한 Outbox 테이블입니다.
--
-- Kafka 발행은 이후 별도의 Relay Worker가 수행합니다.
-- ============================================================

CREATE TABLE content_schema.p_problem_event_outboxes
(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Kafka 이벤트 자체의 고유 식별자입니다.
    --
    -- Outbox 레코드 ID와 별도로 관리하며,
    -- Kafka 재시도 시에도 동일한 event_id를 유지합니다.
    event_id UUID NOT NULL,

    -- 현재 Outbox는 Problem Aggregate에서 발생한 이벤트를 저장합니다.
    aggregate_type VARCHAR(50) NOT NULL,

    -- ProblemPublished 이벤트에서는 problemId가 저장됩니다.
    aggregate_id UUID NOT NULL,

    -- 현재 발행 이벤트 유형:
    -- PROBLEM_PUBLISHED
    event_type VARCHAR(50) NOT NULL,

    -- 이벤트 스키마 버전입니다.
    event_version INTEGER NOT NULL,

    -- 직렬화된 ProblemPublished 이벤트 전체 JSON입니다.
    --
    -- 테스트케이스 input / expectedOutput이 포함되므로
    -- 애플리케이션 로그나 예외 메시지에 payload 전체를 출력하지 않습니다.
    payload JSONB NOT NULL,

    -- PENDING
    -- PUBLISHED
    -- FAILED
    status VARCHAR(20) NOT NULL,

    -- Kafka 발행 실패 후 재시도 횟수입니다.
    retry_count INTEGER NOT NULL DEFAULT 0,

    -- 이벤트가 발생한 시각입니다.
    occurred_at TIMESTAMPTZ NOT NULL,

    -- Kafka 발행 완료 시각입니다.
    --
    -- PENDING/FAILED 상태에서는 NULL이고,
    -- PUBLISHED 상태가 되면 기록됩니다.
    published_at TIMESTAMPTZ,

    -- 마지막 Kafka 발행 실패 원인을 저장합니다.
    --
    -- 테스트케이스 input / expectedOutput 등
    -- 민감한 payload 내용은 저장하지 않습니다.
    last_error TEXT,

    CONSTRAINT uk_problem_event_outboxes_event_id
        UNIQUE (event_id),

    CONSTRAINT chk_problem_event_outboxes_event_version
        CHECK (event_version > 0),

    CONSTRAINT chk_problem_event_outboxes_retry_count
        CHECK (retry_count >= 0),

    CONSTRAINT chk_problem_event_outboxes_status
        CHECK (
            status IN (
                       'PENDING',
                       'PUBLISHED',
                       'FAILED'
                )
            ),

    CONSTRAINT chk_problem_event_outboxes_published_at
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
--
-- Outbox Relay는 주로 PENDING 상태의 행을 조회하므로
-- status 단독 인덱스를 둡니다.
-- ============================================================

CREATE INDEX idx_problem_event_outboxes_status
    ON content_schema.p_problem_event_outboxes (status);


-- ============================================================
-- Relay 순차 처리 인덱스
-- ============================================================
--
-- PENDING Outbox를 발생 순서대로 처리할 수 있도록
-- status + occurred_at 복합 인덱스를 둡니다.
-- ============================================================

CREATE INDEX idx_problem_event_outboxes_status_occurred_at
    ON content_schema.p_problem_event_outboxes (
                                                status,
                                                occurred_at
        );


-- ============================================================
-- 동일 ProblemVersion 중복 Outbox 방지
-- ============================================================
--
-- #109 요구사항:
-- "동일 문제 버전에 대한 중복 Outbox 생성 방지"
--
-- aggregate_id는 problemId이므로
-- problemVersionId를 aggregate_id로 사용할 수 없습니다.
--
-- ProblemPublished payload 내부의 problemVersionId를 사용하여
--
-- event_type + problemVersionId
--
-- 조합을 UNIQUE로 보장합니다.
--
-- 향후 다른 Problem 이벤트가 같은 Outbox 테이블을 사용하더라도
-- 이벤트 유형별로 독립적인 중복 방지가 가능합니다.
-- ============================================================

CREATE UNIQUE INDEX uk_problem_event_outboxes_event_type_problem_version
    ON content_schema.p_problem_event_outboxes (
                                                event_type,
        (payload ->> 'problemVersionId')
        )
    WHERE payload ? 'problemVersionId';


COMMENT ON TABLE content_schema.p_problem_event_outboxes IS
    'Problem 도메인 Kafka 이벤트 발행을 위한 Transactional Outbox';

COMMENT ON COLUMN content_schema.p_problem_event_outboxes.event_id IS
    'Kafka 이벤트 고유 ID. 재시도 시에도 동일하게 유지';

COMMENT ON COLUMN content_schema.p_problem_event_outboxes.aggregate_id IS
    'ProblemPublished 이벤트의 problemId';

COMMENT ON COLUMN content_schema.p_problem_event_outboxes.payload IS
    '직렬화된 ProblemPublished 이벤트 JSON';

COMMENT ON COLUMN content_schema.p_problem_event_outboxes.retry_count IS
    'Kafka 발행 실패 후 재시도 횟수';

COMMENT ON COLUMN content_schema.p_problem_event_outboxes.last_error IS
    '마지막 발행 실패 요약. 테스트케이스 입력/정답 등 민감정보 저장 금지';
