-- 사용자의 XP 이력을 획득 시각과 식별자 기준으로
-- 안정적인 최신순 keyset pagination 조회가 가능하도록 합니다.

CREATE INDEX idx_xp_histories_user_earned_at_id_desc
    ON user_schema.p_xp_histories (
                                   user_id,
                                   earned_at DESC,
                                   id DESC
        );
