-- 60초 설명의 유일성 기준을 coaching_session_id에서 submission_id로 옮긴다
-- (2026-09-03, 이슈 #84 결정 2).
--
-- 배경: 코칭 세션은 V5에서 "문제당 평생 최대 1개"로 확정됐다(힌트·스트릭이 "막혔을 때
-- 도와주는" 장치라 문제당 한 번이면 충분하다는 판단). 반면 60초 설명은 "이번 제출의
-- 접근 방식을 이해했는가"를 확인하는 장치라, 같은 문제를 다른 접근으로 재도전해 새로
-- 정답 제출할 때마다 별도로 등록할 수 있어야 한다는 게 재검토 결론이었다 — 세션 단위
-- UNIQUE(coaching_session_id)로는 이걸 표현할 수 없다(세션이 하나뿐이니 설명도 하나뿐).
--
-- coaching_session_id 컬럼과 FK는 그대로 둔다 — 이 설명이 어느 세션(문제) 소속인지
-- 추적하는 용도로는 계속 필요하다(내부 API #8 등). 유일성 기준만 옮긴다.
--
-- V4/V5와 동일하게, 이 프로젝트는 아직 실사용자 데이터가 없는 개발 단계라(seed 데이터
-- 없음) 기존 행 이관 없이 컬럼·제약만 교체한다. 나중에 실제 데이터가 쌓인 뒤 이
-- 마이그레이션을 다시 적용해야 하는 상황이 오면, 기존 행의 submission_id를 먼저
-- 백필하는 절차를 마이그레이션에 포함시킬 것.

ALTER TABLE coaching_schema.p_explanations
    ADD COLUMN submission_id UUID NOT NULL;

ALTER TABLE coaching_schema.p_explanations
    DROP CONSTRAINT p_explanations_coaching_session_id_key;

ALTER TABLE coaching_schema.p_explanations
    ADD CONSTRAINT uk_explanations_submission UNIQUE (submission_id);

COMMENT ON COLUMN coaching_schema.p_explanations.submission_id IS
    '이 설명이 어떤 제출에 대한 것인지(논리 FK → Judge Service p_submissions.id) — 재도전으로 같은 문제를 다른 접근으로 다시 정답 제출하면, 새 제출마다 별도 설명을 등록할 수 있다';
