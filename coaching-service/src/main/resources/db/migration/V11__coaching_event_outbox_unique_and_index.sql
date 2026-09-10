-- PR #123 심층 재검토(2026-09-09) 반영.
--
-- 1. (aggregate_id, event_type) 유니크 제약 — 지금은 event_type이 'CoachingCompleted'
--    하나뿐이라 사실상 aggregate_id(코칭 세션) 하나당 Outbox 행이 하나여야 한다. 유니크
--    제약이 없으면 애플리케이션 로직에 버그가 생겨 같은 세션에 대해 Outbox가 중복 생성돼도
--    DB가 막아주지 않는다.
ALTER TABLE coaching_schema.p_coaching_event_outboxes
    ADD CONSTRAINT uq_coaching_event_outboxes_aggregate_event
        UNIQUE (aggregate_id, event_type);

-- 2. Relay의 실제 조회 패턴(WHERE status = ? ORDER BY created_at ASC LIMIT 100)에 맞춘
--    복합 인덱스로 교체 — 기존 status 단일 컬럼 인덱스로는 PENDING 후보를 찾은 뒤 매번
--    created_at 정렬 비용이 든다. Outbox가 누적될수록(COMPLETED/FAILED 비율 증가) 이 비용이
--    커진다.
DROP INDEX IF EXISTS coaching_schema.idx_coaching_event_outboxes_status;

CREATE INDEX idx_coaching_event_outboxes_status_created_at
    ON coaching_schema.p_coaching_event_outboxes (status, created_at);

-- 3. processed_at 컬럼 코멘트 정정 — markFailed()에서도 이 컬럼을 기록하게 되면서 "발행을
--    완료 처리한 시각"이 아니라 "Relay 처리가 종료된 시각"(성공/실패 무관)에 가까워졌다.
COMMENT ON COLUMN coaching_schema.p_coaching_event_outboxes.processed_at
    IS '릴레이 워커(#89)가 이 Outbox의 처리를 종료한 시각(COMPLETED 또는 FAILED 확정 시점) — 발행 성공만을 의미하지 않는다';
