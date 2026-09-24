-- ============================================================
-- V19: 일반 Problem의 MULTIPLE_CHOICE 명칭을 MULTI_SELECT로 변경
-- ============================================================
--
-- ProblemType.MULTIPLE_CHOICE:
--   2개 이상 선택하는 문제
--
-- DailyQuizProblemType.MULTIPLE_CHOICE:
--   선택지 중 1개를 선택하는 문제
--
-- 서로 다른 의미에 동일한 이름을 사용하던 혼동을 제거한다.
--
-- V18에서 일반 Problem의 non-CODE 데이터는 ARCHIVED 상태로
-- 격리되어 있으므로 기존 데이터의 의미만 보존하여 이름을 변경한다.
-- ============================================================

UPDATE content_schema.p_problems
SET type = 'MULTI_SELECT',
    updated_at = CURRENT_TIMESTAMP
WHERE type = 'MULTIPLE_CHOICE';
