ALTER TABLE content_schema.p_ai_generation_histories
    ADD COLUMN request_context JSONB;

UPDATE content_schema.p_ai_generation_histories
SET request_context = '{}'::jsonb
WHERE request_context IS NULL;

ALTER TABLE content_schema.p_ai_generation_histories
    ALTER COLUMN request_context SET NOT NULL;

COMMENT ON COLUMN content_schema.p_ai_generation_histories.request_context IS
    '원본 프롬프트 대신 저장하는 AI 호출 최소 요청 문맥';
