-- 이슈 #352 — 힌트를 "오답 제출(시도)당 1개"로 제한하기 위해, 각 힌트가 어느 시도(attempt_no)에서
-- 발급됐는지 기록한다. 같은 시도로 다시 요청하면 새로 만들지 않고 그 시도의 힌트를 그대로 돌려준다.
--
-- 기존 힌트는 시도 번호를 알 수 없어 NULL로 둔다(발급 제한 판단에서 제외). 새로 생성되는 힌트만
-- 값을 채운다.
ALTER TABLE coaching_schema.p_hints
    ADD COLUMN attempt_no INTEGER;

ALTER TABLE coaching_schema.p_hints
    ADD CONSTRAINT chk_hints_attempt_no CHECK (attempt_no IS NULL OR attempt_no >= 1);

COMMENT ON COLUMN coaching_schema.p_hints.attempt_no IS '이 힌트가 발급된 제출의 시도 번호(attempt_no). 이슈 #352 이전 힌트는 NULL';
