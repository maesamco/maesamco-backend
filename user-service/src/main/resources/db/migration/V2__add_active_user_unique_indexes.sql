-- 회원가입 시 미삭제 사용자의 이메일과 닉네임 중복을 방지합니다.
--
-- 탈퇴는 deleted_at을 기록하는 소프트 삭제 방식이므로 전체 UNIQUE 제약이 아니라
-- deleted_at IS NULL인 행만 대상으로 하는 PostgreSQL 부분 UNIQUE 인덱스를 사용합니다.
-- 닉네임은 원래 대소문자를 보존하되 중복 여부는 대소문자를 무시합니다.

CREATE UNIQUE INDEX uk_p_users_active_email_lookup_hash
    ON user_schema.p_users (email_lookup_hash)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uk_p_users_active_nickname_ci
    ON user_schema.p_users (LOWER(nickname))
    WHERE deleted_at IS NULL;
