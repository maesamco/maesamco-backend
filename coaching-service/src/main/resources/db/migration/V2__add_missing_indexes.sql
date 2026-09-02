-- PR #8 리뷰에서 발견된 인덱스 누락 2건.
--
-- 1) p_coaching_sessions.user_id: CoachingSession.java의 @Index(idx_coaching_sessions_user)가
--    V1 베이스라인에는 반영되지 않았음 — Flyway가 스키마의 source of truth가 된 지금
--    (ddl-auto=validate) JPA @Index만으로는 실제 인덱스가 생성되지 않는다.
-- 2) p_ai_call_histories.coaching_session_id: FK 컬럼이지만 PostgreSQL은 FK 생성 시
--    참조 컬럼에 인덱스를 자동으로 만들어주지 않는다. findByCoachingSessionIdOrderByCalledAtAsc()가
--    누적되는 호출 이력을 이 컬럼으로 필터링하는 주요 조회 경로라 인덱스가 필요하다.

CREATE INDEX idx_coaching_sessions_user ON coaching_schema.p_coaching_sessions(user_id);
CREATE INDEX idx_ai_call_histories_coaching_session ON coaching_schema.p_ai_call_histories(coaching_session_id);
