-- 주의:
-- PostgreSQL에서 CHAR(64) -> VARCHAR(64) 타입 변경은
-- p_users 테이블 재작성 및 ACCESS EXCLUSIVE LOCK을 유발할 수 있습니다.
-- 운영 데이터가 충분히 쌓인 환경에서는 점검/유지보수 시간에 적용합니다.

ALTER TABLE user_schema.p_users
ALTER COLUMN email_lookup_hash
    TYPE VARCHAR(64)
    USING RTRIM(email_lookup_hash)::VARCHAR(64);

COMMENT ON COLUMN user_schema.p_users.email_lookup_hash
    IS '정규화 이메일의 HMAC-SHA256 해시 — 로그인 조회·중복 확인용';
