-- ============================================================
-- V7: Curriculum 및 문제 지원 도메인 테이블 정렬
-- ============================================================
--
-- PR #121에서 추가된 엔티티 구조를 기존 V1 스키마에 반영합니다.
--
-- 주요 변경:
--   p_chapters       -> p_curriculums
--   p_units          -> 신규 Unit 엔티티 구조
--   p_lessons        -> 신규 Lesson 엔티티 구조
--   p_tags           -> 신규 생성
--   p_problem_tags   -> 신규 생성
--   p_problem_versions 컬럼 정렬
--   p_problem_progress -> 신규 생성
--   p_test_cases     -> p_testcases
-- ============================================================


-- ============================================================
-- 1. Curriculum
-- ============================================================

ALTER TABLE content_schema.p_chapters
    RENAME TO p_curriculums;

ALTER TABLE content_schema.p_curriculums
    RENAME COLUMN id TO curriculum_id;

ALTER TABLE content_schema.p_curriculums
    ADD COLUMN language VARCHAR(20) NOT NULL DEFAULT 'JAVA';

ALTER TABLE content_schema.p_curriculums
    ALTER COLUMN language DROP DEFAULT;

COMMENT ON TABLE content_schema.p_curriculums
    IS '프로그래밍 언어별 학습 커리큘럼';

COMMENT ON COLUMN content_schema.p_curriculums.curriculum_id
    IS '커리큘럼 식별자';

COMMENT ON COLUMN content_schema.p_curriculums.language
    IS '커리큘럼 프로그래밍 언어';


-- ============================================================
-- 2. Unit
-- ============================================================

ALTER TABLE content_schema.p_units
    RENAME COLUMN id TO unit_id;

ALTER TABLE content_schema.p_units
    RENAME COLUMN chapter_id TO curriculum_id;

ALTER TABLE content_schema.p_units
    ADD COLUMN language VARCHAR(20) NOT NULL DEFAULT 'JAVA';

ALTER TABLE content_schema.p_units
    ALTER COLUMN language DROP DEFAULT;

COMMENT ON TABLE content_schema.p_units
    IS '커리큘럼에 포함되는 학습 유닛';

COMMENT ON COLUMN content_schema.p_units.unit_id
    IS '유닛 식별자';

COMMENT ON COLUMN content_schema.p_units.curriculum_id
    IS '소속 커리큘럼 식별자';

COMMENT ON COLUMN content_schema.p_units.language
    IS '유닛 프로그래밍 언어';


-- ============================================================
-- 3. Lesson
-- ============================================================

ALTER TABLE content_schema.p_lessons
    RENAME COLUMN id TO lesson_id;

ALTER TABLE content_schema.p_lessons
    ADD COLUMN description VARCHAR(100);

ALTER TABLE content_schema.p_lessons
    ADD COLUMN language VARCHAR(20) NOT NULL DEFAULT 'JAVA';

ALTER TABLE content_schema.p_lessons
    ALTER COLUMN language DROP DEFAULT;

ALTER TABLE content_schema.p_lessons
    ALTER COLUMN content DROP NOT NULL;

COMMENT ON TABLE content_schema.p_lessons
    IS '유닛에 포함되는 학습 레슨';

COMMENT ON COLUMN content_schema.p_lessons.lesson_id
    IS '레슨 식별자';

COMMENT ON COLUMN content_schema.p_lessons.description
    IS '레슨 요약 설명';

COMMENT ON COLUMN content_schema.p_lessons.language
    IS '레슨 프로그래밍 언어';


-- ============================================================
-- 4. Tag
-- ============================================================

CREATE TABLE content_schema.p_tags (
                                       id UUID PRIMARY KEY,
                                       name VARCHAR(50) NOT NULL,
                                       attribute VARCHAR(20) NOT NULL,
                                       created_at TIMESTAMPTZ NOT NULL,
                                       created_by UUID NOT NULL,
                                       updated_at TIMESTAMPTZ NOT NULL,
                                       updated_by UUID NOT NULL,
                                       deleted_at TIMESTAMPTZ,
                                       deleted_by UUID
);

CREATE UNIQUE INDEX uq_p_tags_active_name
    ON content_schema.p_tags (LOWER(name))
    WHERE deleted_at IS NULL;

COMMENT ON TABLE content_schema.p_tags
    IS '문제 분류 태그';

COMMENT ON COLUMN content_schema.p_tags.attribute
    IS '태그 속성: CONCEPT, DATA_STRUCTURE, ALGORITHM, ETC';


-- ============================================================
-- 5. ProblemTag
-- ============================================================

CREATE TABLE content_schema.p_problem_tags (
                                               id UUID PRIMARY KEY,
                                               problem_id UUID NOT NULL,
                                               tag_id UUID NOT NULL,

                                               CONSTRAINT uq_p_problem_tags_problem_tag
                                                   UNIQUE (problem_id, tag_id),

                                               CONSTRAINT fk_p_problem_tags_problem
                                                   FOREIGN KEY (problem_id)
                                                       REFERENCES content_schema.p_problems(id),

                                               CONSTRAINT fk_p_problem_tags_tag
                                                   FOREIGN KEY (tag_id)
                                                       REFERENCES content_schema.p_tags(id)
);

COMMENT ON TABLE content_schema.p_problem_tags
    IS '문제와 태그의 다대다 연결';

COMMENT ON COLUMN content_schema.p_problem_tags.problem_id
    IS '문제 식별자';

COMMENT ON COLUMN content_schema.p_problem_tags.tag_id
    IS '태그 식별자';


-- ============================================================
-- 6. ProblemVersion
-- ============================================================

ALTER TABLE content_schema.p_problem_versions
    RENAME COLUMN content_snapshot TO problem_snapshot;

-- 기존 V1 컬럼은 보존합니다.
-- 새 ProblemVersion 엔티티는 published_at과 created_by를
-- 직접 관리하지 않으므로 신규 INSERT가 실패하지 않도록
-- DB 기본값을 지정합니다.

ALTER TABLE content_schema.p_problem_versions
    ALTER COLUMN published_at
        SET DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE content_schema.p_problem_versions
    ALTER COLUMN created_by
        SET DEFAULT
        '00000000-0000-0000-0000-000000000000'::UUID;

COMMENT ON TABLE content_schema.p_problem_versions
    IS '문제 수정 이력 스냅샷';

COMMENT ON COLUMN content_schema.p_problem_versions.problem_snapshot
    IS '해당 버전의 문제 정보를 보관하는 JSON 스냅샷';


-- ============================================================
-- 7. ProblemProgress
-- ============================================================

CREATE TABLE content_schema.p_problem_progress (
                                                   id UUID PRIMARY KEY,
                                                   user_id UUID NOT NULL,
                                                   problem_id UUID NOT NULL,
                                                   version_no INTEGER NOT NULL,
                                                   solved_at TIMESTAMPTZ,
                                                   progress_status VARCHAR(20) NOT NULL,

                                                   CONSTRAINT ck_p_problem_progress_version_no
                                                       CHECK (version_no >= 1),

                                                   CONSTRAINT ck_p_problem_progress_status
                                                       CHECK (
                                                           progress_status IN (
                                                                               'NOT_ATTEMPTED',
                                                                               'WRONG',
                                                                               'CORRECT'
                                                               )
                                                           ),

                                                   CONSTRAINT fk_p_problem_progress_problem
                                                       FOREIGN KEY (problem_id)
                                                           REFERENCES content_schema.p_problems(id)
);

CREATE UNIQUE INDEX uq_p_problem_progress_user_problem
    ON content_schema.p_problem_progress (
                                          user_id,
                                          problem_id
        );

CREATE INDEX idx_p_problem_progress_problem_id
    ON content_schema.p_problem_progress (
                                          problem_id
        );

CREATE INDEX idx_p_problem_progress_user_status
    ON content_schema.p_problem_progress (
                                          user_id,
                                          progress_status
        );

COMMENT ON TABLE content_schema.p_problem_progress
    IS '사용자별 문제 풀이 진행 상태';

COMMENT ON COLUMN content_schema.p_problem_progress.user_id
    IS '논리 FK: User Service 사용자 식별자';

COMMENT ON COLUMN content_schema.p_problem_progress.problem_id
    IS '문제 식별자';

COMMENT ON COLUMN content_schema.p_problem_progress.version_no
    IS '풀이 및 채점에 사용한 문제 버전';

COMMENT ON COLUMN content_schema.p_problem_progress.solved_at
    IS '최초 정답 처리 시각';

COMMENT ON COLUMN content_schema.p_problem_progress.progress_status
    IS '풀이 상태: NOT_ATTEMPTED, WRONG, CORRECT';


-- ============================================================
-- 8. TestCase
-- ============================================================

ALTER TABLE content_schema.p_test_cases
    RENAME TO p_testcases;

ALTER TABLE content_schema.p_testcases
    RENAME COLUMN id TO test_case_id;

ALTER TABLE content_schema.p_testcases
    RENAME COLUMN display_order TO test_case_order;

ALTER TABLE content_schema.p_testcases
    ADD COLUMN test_case_status VARCHAR(20)
        NOT NULL DEFAULT 'APPROVED';

ALTER TABLE content_schema.p_testcases
    ALTER COLUMN test_case_status DROP DEFAULT;

COMMENT ON TABLE content_schema.p_testcases
    IS '문제 채점용 공개·비공개 테스트케이스';

COMMENT ON COLUMN content_schema.p_testcases.test_case_id
    IS '테스트케이스 식별자';

COMMENT ON COLUMN content_schema.p_testcases.test_case_order
    IS '공개 여부 그룹 안에서의 실행 순서';

COMMENT ON COLUMN content_schema.p_testcases.test_case_status
    IS '테스트케이스 상태: PENDING, APPROVED, REJECTED';


-- ============================================================
-- 9. 조회 및 조인 인덱스
-- ============================================================

CREATE INDEX idx_p_units_curriculum_id
    ON content_schema.p_units (
                               curriculum_id
        );

CREATE INDEX idx_p_lessons_unit_id
    ON content_schema.p_lessons (
                                 unit_id
        );

CREATE INDEX idx_p_problem_tags_problem_id
    ON content_schema.p_problem_tags (
                                      problem_id
        );

CREATE INDEX idx_p_problem_tags_tag_id
    ON content_schema.p_problem_tags (
                                      tag_id
        );

CREATE INDEX idx_p_problem_versions_problem_id
    ON content_schema.p_problem_versions (
                                          problem_id
        );

CREATE INDEX idx_p_testcases_problem_public_order
    ON content_schema.p_testcases (
                                   problem_id,
                                   is_public,
                                   test_case_order
        );
