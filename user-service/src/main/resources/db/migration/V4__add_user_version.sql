ALTER TABLE user_schema.p_users
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN user_schema.p_users.version
    IS '낙관적 락(@Version) - 사용자 정보 동시 갱신 충돌 방지';
