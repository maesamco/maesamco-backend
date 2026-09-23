CREATE TABLE user_schema.p_social_accounts (
                                               id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                                               user_id UUID NOT NULL,

                                               provider VARCHAR(20) NOT NULL,

                                               provider_user_id VARCHAR(255) NOT NULL,

                                               created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                                               created_by UUID NOT NULL,

                                               updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                                               updated_by UUID NOT NULL,

                                               deleted_at TIMESTAMPTZ,
                                               deleted_by UUID,

                                               CONSTRAINT fk_p_social_accounts_user
                                                   FOREIGN KEY (user_id)
                                                       REFERENCES user_schema.p_users(id),

                                               CONSTRAINT ck_p_social_accounts_provider
                                                   CHECK (
                                                       provider IN (
                                                                    'GOOGLE',
                                                                    'KAKAO',
                                                                    'NAVER'
                                                           )
                                                       )
);

COMMENT ON TABLE user_schema.p_social_accounts
    IS '소셜 인증 제공자 계정과 MAESAMCO 사용자 연결 정보';

COMMENT ON COLUMN user_schema.p_social_accounts.user_id
    IS 'MAESAMCO 사용자 ID';

COMMENT ON COLUMN user_schema.p_social_accounts.provider
    IS '소셜 인증 제공자';

COMMENT ON COLUMN user_schema.p_social_accounts.provider_user_id
    IS 'Provider가 보장하는 사용자 고유 ID. Google은 OIDC sub 사용';


CREATE UNIQUE INDEX uk_p_social_accounts_active_provider_user
    ON user_schema.p_social_accounts (
                                      provider,
                                      provider_user_id
        )
    WHERE deleted_at IS NULL;


CREATE UNIQUE INDEX uk_p_social_accounts_active_user_provider
    ON user_schema.p_social_accounts (
                                      user_id,
                                      provider
        )
    WHERE deleted_at IS NULL;


CREATE INDEX idx_p_social_accounts_active_user
    ON user_schema.p_social_accounts (
                                      user_id
        )
    WHERE deleted_at IS NULL;
