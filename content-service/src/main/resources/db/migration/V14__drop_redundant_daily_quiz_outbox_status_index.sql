-- status는 아래 복합 인덱스들의 선두 컬럼이므로 단일 인덱스가 중복됩니다.
-- idx_daily_quiz_event_outboxes_status_occurred_at
-- idx_daily_quiz_event_outboxes_claimable
DROP INDEX content_schema.idx_daily_quiz_event_outboxes_status;
