-- CoachingCompleted를 포함한 Kafka 이벤트가 재발행되더라도
-- 동일한 원천 이벤트의 XP가 두 번 지급되지 않도록 최종 멱등성을 보장합니다.
-- 관리자 조정처럼 Kafka 이벤트와 무관한 이력은 source_event_id가 NULL일 수 있으므로
-- NULL이 아닌 행만 대상으로 하는 부분 UNIQUE 인덱스를 사용합니다.

CREATE UNIQUE INDEX uk_xp_histories_source_event_id
    ON user_schema.p_xp_histories (source_event_id)
    WHERE source_event_id IS NOT NULL;
