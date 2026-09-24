-- ============================================================
-- V20: Curriculum / Unit / Lesson display_order 정책 정리
-- ============================================================
--
-- Curriculum
--   - 순서 개념 제거
--
-- Unit
--   - 동일 Curriculum의 활성 Unit끼리 display_order UNIQUE
--
-- Lesson
--   - 동일 Unit의 활성 Lesson끼리 display_order UNIQUE
--
-- 기존 데이터에 중복 또는 순번 공백이 있을 수 있으므로
-- 활성 데이터는 현재 순서를 보존한 채 1부터 재정렬한 뒤
-- 부분 UNIQUE 인덱스를 생성한다.
-- ============================================================


-- 1. Curriculum은 순서 개념을 사용하지 않는다.
ALTER TABLE content_schema.p_curriculums
DROP COLUMN IF EXISTS display_order;


-- 2. 기존 활성 Unit 순서를 부모별로 정규화한다.
WITH ranked_units AS (
    SELECT
        unit_id,
        CAST(
                ROW_NUMBER() OVER (
                                   PARTITION BY curriculum_id
                ORDER BY display_order ASC, unit_id ASC
            )
            AS INTEGER
        ) AS new_display_order
    FROM content_schema.p_units
    WHERE deleted_at IS NULL
)
UPDATE content_schema.p_units AS unit
SET display_order = ranked.new_display_order
    FROM ranked_units AS ranked
WHERE unit.unit_id = ranked.unit_id
  AND unit.display_order <> ranked.new_display_order;


-- 3. 기존 활성 Lesson 순서를 부모별로 정규화한다.
WITH ranked_lessons AS (
    SELECT
        lesson_id,
        CAST(
                ROW_NUMBER() OVER (
                                   PARTITION BY unit_id
                ORDER BY display_order ASC, lesson_id ASC
            )
            AS INTEGER
        ) AS new_display_order
    FROM content_schema.p_lessons
    WHERE deleted_at IS NULL
)
UPDATE content_schema.p_lessons AS lesson
SET display_order = ranked.new_display_order
    FROM ranked_lessons AS ranked
WHERE lesson.lesson_id = ranked.lesson_id
  AND lesson.display_order <> ranked.new_display_order;


-- 4. 활성 Unit의 형제 순번 중복 방지
CREATE UNIQUE INDEX IF NOT EXISTS
    uq_p_units_active_curriculum_display_order
    ON content_schema.p_units (
    curriculum_id,
    display_order
    )
    WHERE deleted_at IS NULL;


-- 5. 활성 Lesson의 형제 순번 중복 방지
CREATE UNIQUE INDEX IF NOT EXISTS
    uq_p_lessons_active_unit_display_order
    ON content_schema.p_lessons (
    unit_id,
    display_order
    )
    WHERE deleted_at IS NULL;
