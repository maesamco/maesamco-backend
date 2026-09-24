-- ============================================================
-- 소셜 회원가입 사용자의 비밀번호 미설정 허용 (#308)
-- ============================================================
--
-- Google 등 소셜 계정으로만 가입한 사용자는 MAESAMCO 비밀번호가 없습니다.
-- 사용할 수 없는 임의 해시를 넣는 대신 NULL로 "비밀번호 없음"을 명시합니다.
--
-- - 이메일/비밀번호 로그인: 비밀번호가 없는 계정은 INVALID_CREDENTIALS로 거부
-- - 비밀번호 변경 / 회원 탈퇴: USER_PASSWORD_NOT_SET으로 거부
--   (소셜 재인증 기반 탈퇴는 후속 이슈에서 지원)
--
-- 소셜 계정 연결 정보는 p_social_accounts(V9)가 관리합니다.
-- ============================================================

ALTER TABLE user_schema.p_users
    ALTER COLUMN password_hash DROP NOT NULL;

COMMENT ON COLUMN user_schema.p_users.password_hash IS
    'Argon2id 비밀번호 해시. 소셜 계정으로만 가입한 사용자는 NULL';
