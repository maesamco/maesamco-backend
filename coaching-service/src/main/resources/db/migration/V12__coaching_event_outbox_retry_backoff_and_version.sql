-- PR #123 재검토 2차(용현님, 2026-09-10) 반영.
--
-- 1. next_attempt_at — recordPostPublishFailure()는 이벤트가 이미 Kafka에 전달됐을 수
--    있어 의도적으로 FAILED 종료 없이 무한 재시도한다. 그런데 Relay의 폴링 쿼리가
--    (status, created_at) 오래된 순 최대 100건이라, 영구히 실패하는 행이 100개 쌓이면
--    그 뒤에 생긴 정상 이벤트가 폴링 대상에서 계속 밀려나는 head-of-line blocking이
--    생길 수 있다. 실패할 때마다 지수 백오프로 다음 재시도 가능 시각을 기록해두고,
--    Relay 조회에서 그 시각이 지나지 않은 행은 제외한다.
ALTER TABLE coaching_schema.p_coaching_event_outboxes
    ADD COLUMN next_attempt_at TIMESTAMPTZ;

COMMENT ON COLUMN coaching_schema.p_coaching_event_outboxes.next_attempt_at
    IS 'Relay 폴링에서 이 시각 이전에는 제외한다(지수 백오프) — NULL이면 즉시 재시도 가능';

-- 2. version — markPublished()/recordFailedAttempt()/recordPostPublishFailure()가 id로
--    재조회한 뒤 status가 PENDING인지 확인하고 저장하는 check-then-act 방식이라, 두 Relay
--    인스턴스가 거의 동시에 같은 행을 PENDING으로 읽어버리면 이 확인만으로는 막을 수 없다.
--    낙관적 락을 걸어 두 트랜잭션 중 하나만 실제로 반영되게 한다 — 이 프로젝트에서 이미
--    확립된 패턴이다(Problem 엔티티 + GlobalExceptionHandler.handleOptimisticLockingFailure()).
ALTER TABLE coaching_schema.p_coaching_event_outboxes
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
