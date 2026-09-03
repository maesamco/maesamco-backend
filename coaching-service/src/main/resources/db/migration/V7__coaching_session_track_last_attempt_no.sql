-- CoachingSessionFinder.findOrCreate()가 세션의 submission_id를 검증 없이 덮어써서,
-- 과거 제출에 대한 요청(지연된 요청, 과거 제출에 대한 뒤늦은 설명 등록 등)이 세션이 다루는
-- submission_id를 최신 제출 이전으로 되돌릴 수 있었다(PR #88 리뷰, 용현님 P1) — 세션이
-- 지금 다루는 제출이 실제로 최신인지 판단할 기준 자체가 없었기 때문이다.
--
-- Judge Service의 SubmissionSnapshot.attemptNo(문제당 제출 순번, 단조 증가)를 세션에도
-- last_attempt_no로 함께 저장해, 새 제출의 attemptNo가 기존 값보다 클 때만 submission_id를
-- 갈아타도록 한다(CoachingSession.advanceToSubmission()).
--
-- V4/V5/V6와 동일하게, 이 프로젝트는 아직 실사용자 데이터가 없는 개발 단계라(seed 데이터
-- 없음) 기존 행 백필 없이 컬럼만 추가한다. 나중에 실제 데이터가 쌓인 뒤 이 마이그레이션을
-- 다시 적용해야 하는 상황이 오면, 기존 세션들의 last_attempt_no를 먼저 백필하는 절차를
-- 마이그레이션에 포함시킬 것.

ALTER TABLE coaching_schema.p_coaching_sessions
    ADD COLUMN last_attempt_no INTEGER NOT NULL;

COMMENT ON COLUMN coaching_schema.p_coaching_sessions.last_attempt_no IS
    '이 세션이 현재 다루는 제출(submission_id)의 시도 번호(Judge Service attemptNo) — 더 오래된 제출로 submission_id가 역행하는 걸 막는 기준';
