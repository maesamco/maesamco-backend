-- ============================================================
-- V22: Curriculum / Unit / Lesson 공개 상태 도입 (#344 A안: 검수 후 공개)
-- ============================================================
--
-- - 세 테이블에 status 컬럼(DRAFT / PUBLISHED)을 추가한다.
-- - 기존 행은 이미 학습자에게 노출되고 있으므로 PUBLISHED로 채운다.
--   (DRAFT로 채우면 배포 직후 학습자 화면이 비게 된다.)
-- - 이후 새로 만드는 행의 기본값은 DRAFT다.
--   애플리케이션도 생성 시 DRAFT를 명시하지만, 직접 INSERT에도 같은 규칙을 적용한다.
-- - 상위 비공개 시 하위 숨김은 조회 시점에 계산하므로 하위 행의 status는 건드리지 않는다.
-- ============================================================


-- 1. 컬럼 추가: DEFAULT 'PUBLISHED'로 추가해 기존 행을 한 번에 PUBLISHED로 채운다.
ALTER TABLE content_schema.p_curriculums
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED';

ALTER TABLE content_schema.p_units
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED';

ALTER TABLE content_schema.p_lessons
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED';


-- 2. 이후 신규 행의 기본값은 DRAFT
ALTER TABLE content_schema.p_curriculums
    ALTER COLUMN status SET DEFAULT 'DRAFT';

ALTER TABLE content_schema.p_units
    ALTER COLUMN status SET DEFAULT 'DRAFT';

ALTER TABLE content_schema.p_lessons
    ALTER COLUMN status SET DEFAULT 'DRAFT';


-- 3. 허용 값 제한
ALTER TABLE content_schema.p_curriculums
    ADD CONSTRAINT ck_p_curriculums_status CHECK (status IN ('DRAFT', 'PUBLISHED'));

ALTER TABLE content_schema.p_units
    ADD CONSTRAINT ck_p_units_status CHECK (status IN ('DRAFT', 'PUBLISHED'));

ALTER TABLE content_schema.p_lessons
    ADD CONSTRAINT ck_p_lessons_status CHECK (status IN ('DRAFT', 'PUBLISHED'));


-- 4. 컬럼 설명
COMMENT ON COLUMN content_schema.p_curriculums.status
    IS '공개 상태. DRAFT(작성 중, 관리자만 조회) / PUBLISHED(학습자 공개)';

COMMENT ON COLUMN content_schema.p_units.status
    IS '공개 상태. DRAFT / PUBLISHED. 상위 Curriculum이 DRAFT이면 학습자에게 숨긴다';

COMMENT ON COLUMN content_schema.p_lessons.status
    IS '공개 상태. DRAFT / PUBLISHED. 상위 Unit 또는 Curriculum이 DRAFT이면 학습자에게 숨긴다';
