ALTER TABLE user_schema.p_users
ALTER COLUMN email_lookup_hash
    TYPE VARCHAR(64)
    USING RTRIM(email_lookup_hash)::VARCHAR(64);

COMMENT ON COLUMN user_schema.p_users.email_lookup_hash
    IS '정규화 이메일의 HMAC-SHA256 해시 — 로그인 조회·중복 확인용';
