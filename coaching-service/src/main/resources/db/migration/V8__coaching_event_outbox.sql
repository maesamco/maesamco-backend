-- 이슈 #51(역질문 답변 API) — 답변 등록 시 코칭 세션이 COMPLETED로 전이되면 CoachingCompleted
-- 이벤트를 User Service에 발행해야 한다. DB 커밋과 Kafka 발행이 서로 다른 트랜잭션이라 그대로
-- 직접 발행하면 하나만 성공하는 경우 XP·스트릭 반영이 영구히 누락될 수 있어(기술 선택 근거
-- 로그.md 2026-09-04), Outbox 패턴으로 완료 처리와 같은 트랜잭션에 이벤트를 기록한다.
--
-- 이 Outbox는 CoachingCompleted 전용이 아니라 CoachingSession 애그리거트에 대한 발행
-- 대기함이다(judge_schema.p_submission_event_outboxes와 동일한 설계 — 이슈 #63 패턴).
--
-- 이슈 #51은 이 테이블에 row를 쓰는 것까지만 담당한다. 실제로 폴링해서 Kafka에 발행하는
-- Relay Worker는 이슈 #89에서 별도로 구현한다 — 그 전까지는 row가 쌓이기만 하고 발행은
-- 안 된다.

CREATE TABLE coaching_schema.p_coaching_event_outboxes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL CHECK (event_type IN ('CoachingCompleted')),
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','COMPLETED')),
    attempt_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,
    FOREIGN KEY (aggregate_id) REFERENCES coaching_schema.p_coaching_sessions(id)
);
COMMENT ON TABLE coaching_schema.p_coaching_event_outboxes IS '코칭 세션 완료 이벤트 발행용 Outbox';
COMMENT ON COLUMN coaching_schema.p_coaching_event_outboxes.processed_at IS '릴레이 워커(#89)가 이벤트 발행을 완료 처리한 시각';

CREATE INDEX idx_coaching_event_outboxes_status
    ON coaching_schema.p_coaching_event_outboxes (status);
