-- 같은 사용자가 동일 문제를 여러 번 정답 제출하거나 이벤트가 동시에 처리되더라도
-- 최초 정답 XP가 한 번만 지급되도록 최종 멱등성을 보장합니다.

CREATE UNIQUE INDEX uk_xp_histories_first_correct_user_problem
    ON user_schema.p_xp_histories (user_id, problem_id)
    WHERE reward_type = 'FIRST_CORRECT';
