-- 이슈 #218에서 발견 — CoachingSession.advanceToSubmission()/complete()는 낙관적 락
-- 없이 확인 후 갱신(check-then-act)하는 구조라, 동시 요청이 겹치면 먼저 flush된 쪽의
-- 변경이 나중에 flush되는 쪽에 조용히 덮어써질 수 있었다.
--
-- @Version 컬럼을 추가해 이 경합을 실제 충돌(ObjectOptimisticLockingFailureException)로
-- 드러내고, CoachingSessionFinder/FollowUpAnswerFacade가 이를 감지해 재시도하도록 한다.
ALTER TABLE coaching_schema.p_coaching_sessions
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
