-- 이슈 #89(Outbox Relay Worker) — 재시도 상한 소진 시 Outbox를 종료 처리하기 위해
-- FAILED 상태를 추가한다. Judge Service의 p_submission_event_outboxes(이슈 #63)와
-- 동일한 이유(PENDING만 계속 재시도하면 무제한 재시도가 되고, 다음 폴링마다 최대 100건
-- 배치를 오래된 실패 행이 계속 채워 정상 건을 밀어낼 수 있음).
ALTER TABLE coaching_schema.p_coaching_event_outboxes
    DROP CONSTRAINT IF EXISTS p_coaching_event_outboxes_status_check;

ALTER TABLE coaching_schema.p_coaching_event_outboxes
    ADD CONSTRAINT p_coaching_event_outboxes_status_check
        CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED'));
