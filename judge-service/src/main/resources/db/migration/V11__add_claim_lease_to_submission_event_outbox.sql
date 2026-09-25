-- ============================================================
-- Submission Event Outbox Relay claim (#272, #261 judge-service 스코프)
-- ============================================================
--
-- 여러 judge-service 인스턴스의 Relay가 같은 PENDING Outbox를 동시에
-- Kafka로 발행하지 않도록 발행 직전에 행을 선점합니다.
--
-- 1) SELECT ... FOR UPDATE SKIP LOCKED 로 다른 인스턴스가 잠근 행은 건너뛰고
-- 2) 짧은 트랜잭션 안에서 IN_PROGRESS + lease_until + claim_id 를 기록한 뒤
-- 3) 트랜잭션을 커밋하고 나서 Kafka 발행을 수행합니다.
--
-- lease_until: 선점 만료 시각. 처리 중인 인스턴스가 종료되면
--              만료 이후 다른 인스턴스가 재선점하여 이벤트 유실을 막습니다.
-- claim_id   : 발행 시도 식별자(fencing token). lease 만료 후 재선점된 행에
--              이전 Worker가 늦게 결과를 기록하는 것을 막습니다.
-- version    : JPA @Version 낙관적 락. claim_id 검증을 통과한 뒤의 쓰기 충돌에 대한 2차 방어입니다.
--
-- content-service Problem/Daily Quiz Outbox(V20/V12), coaching-service
-- Outbox(V16)와 동일한 선점 모델입니다.
-- ============================================================

ALTER TABLE judge_schema.p_submission_event_outboxes
    ADD COLUMN lease_until TIMESTAMPTZ,
    ADD COLUMN claim_id UUID,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE judge_schema.p_submission_event_outboxes
    DROP CONSTRAINT chk_submission_event_outboxes_status_values;

ALTER TABLE judge_schema.p_submission_event_outboxes
    ADD CONSTRAINT chk_submission_event_outboxes_status_values
        CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED')),
    ADD CONSTRAINT chk_submission_event_outboxes_claim
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

-- 선점 조회(PENDING 오래된 순 / lease 만료된 IN_PROGRESS)용 인덱스.
-- PENDING 조회는 (status, created_at) 순서를 그대로 타고, IN_PROGRESS는 극소수라 부분 인덱스로 둔다.
CREATE INDEX idx_submission_event_outboxes_status_created_at
    ON judge_schema.p_submission_event_outboxes (status, created_at, id);

CREATE INDEX idx_submission_event_outboxes_in_progress_lease
    ON judge_schema.p_submission_event_outboxes (lease_until)
    WHERE status = 'IN_PROGRESS';

COMMENT ON COLUMN judge_schema.p_submission_event_outboxes.lease_until IS
    'Relay Worker의 발행 선점 만료 시각. 만료된 IN_PROGRESS 행은 재선점 가능';

COMMENT ON COLUMN judge_schema.p_submission_event_outboxes.claim_id IS
    '현재 발행 시도를 식별하고 이전 Worker의 늦은 상태 변경을 막는 선점 ID (fencing token)';
