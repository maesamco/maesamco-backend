-- ============================================================
-- V17: p_problems에 lesson_id 추가 (Lesson-Problem 1:N 연결)
-- ============================================================
--
-- 이슈 #291 — Lesson과 Problem을 연결하는 기능이 전혀 없어서
-- "이 레슨에 연결된 문제 목록" 조회 API를 만들 수 없던 문제 해결.
--
-- 하나의 문제가 여러 레슨에서 재사용되는 요구사항은 없는 것으로
-- 확인되어(기획 확인 완료), 1:N 관계로 설계한다 — 별도 연결 테이블
-- 대신 p_problems에 nullable lesson_id 컬럼만 추가한다.
--
-- nullable인 이유: 기존에 이미 만들어진 문제들, 그리고 아직 특정
-- 레슨에 배정되지 않은 문제(예: AI가 생성했지만 아직 커리큘럼에
-- 편입 전인 문제)를 허용하기 위함.
-- ============================================================

ALTER TABLE content_schema.p_problems
    ADD COLUMN lesson_id UUID;

ALTER TABLE content_schema.p_problems
    ADD CONSTRAINT fk_p_problems_lesson_id
    FOREIGN KEY (lesson_id) REFERENCES content_schema.p_lessons(lesson_id);

-- "이 레슨에 연결된 문제 목록" 조회(GET /api/v1/contents/problems?lessonId=...)가
-- 주 사용처이므로 조회 성능을 위해 인덱스를 추가한다.
CREATE INDEX idx_p_problems_lesson_id
    ON content_schema.p_problems (lesson_id)
    WHERE lesson_id IS NOT NULL;

COMMENT ON COLUMN content_schema.p_problems.lesson_id
    IS '연결된 레슨 ID. 아직 레슨에 배정되지 않은 문제는 NULL. 문제 하나는 최대 하나의 레슨에만 연결됨(1:N)';