-- 이슈 #218에서 발견 — Hint.stage에 대한 CHECK (stage BETWEEN 1 AND 4) 제약이
-- 매삼코_ERD.sql 원본에도 원래 없어서 V1 베이스라인부터 빠져 있었다.
--
-- Hint.requireValidStage()가 애플리케이션 레벨에서 1~4 범위를 이미 검증하고 있어
-- 지금까지 잘못된 stage 값이 저장된 적은 없지만, 이 검증을 우회할 수 있는 경로
-- (다른 서비스의 직접 INSERT, 수동 마이그레이션 등)가 생기면 DB가 막아줄 방법이
-- 없었다. 애플리케이션 레벨 검증은 유지하고, DB 레벨 방어를 추가로 건다.
ALTER TABLE coaching_schema.p_hints
    ADD CONSTRAINT chk_hints_stage CHECK (stage BETWEEN 1 AND 4);
