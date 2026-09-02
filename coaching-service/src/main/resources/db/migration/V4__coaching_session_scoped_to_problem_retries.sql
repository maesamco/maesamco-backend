-- 코칭 세션을 "제출 1건"이 아니라 "같은 문제에 대한 재시도 묶음"에 묶기로 확정(2026-09-02).
--
-- 배경: 사용자가 오답 제출 후 코드를 고쳐 재제출하면 Judge Service에 새 submission_id가
-- 생긴다. 기존 UNIQUE(submission_id) 제약 하에서는 재제출마다 코칭 세션이 새로 생겨서
-- 힌트 1~4단계 진행이 끊기고, API 명세의 "4단계 이후에도 오답이 반복되면 4단계 힌트를
-- 유지한 채 자유롭게 재시도한다"는 문구와 실제 동작이 맞지 않았다.
--
-- 다만 완전한 UNIQUE(user_id, problem_id)는 "한 번 정답을 맞춘 문제를 나중에 다시
-- 풀어보는" 시나리오를 막아버린다 — 이미 COMPLETED된 세션이 영구히 그 자리를 차지해서
-- 두 번째 도전이 아예 안 된다. 그래서 "진행 중(IN_PROGRESS)인 세션은 문제당 최대 1개"만
-- 강제하는 부분 UNIQUE 인덱스로 둔다 — 세션이 COMPLETED되면 다음 도전은 새 세션을 만들 수
-- 있다. 기존 UNIQUE(submission_id)는 그대로 둔다(각 세션의 submission_id 값 자체는
-- 여전히 서로 겹치지 않으므로 유지해도 무해함).

CREATE UNIQUE INDEX uk_coaching_sessions_user_problem_in_progress
    ON coaching_schema.p_coaching_sessions (user_id, problem_id)
    WHERE status = 'IN_PROGRESS';
