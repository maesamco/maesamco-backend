-- ============================================================
-- V22: 사용하지 않는 옛 개념 테이블 삭제
-- ============================================================
--
-- V7에서 개념 마스터/매핑이 태그 구조로 통합됐다.
--   p_concepts         → p_tags (attribute = 'CONCEPT')
--   p_problem_concepts → p_problem_tags
--   p_lesson_concepts  → 대응 테이블 없음(레슨에 태그를 다는 매핑은 재설계에 포함되지 않음)
-- 이후 엔티티·쿼리 어디에서도 세 테이블을 참조하지 않지만 V1 baseline이 만든 채로 남아 있었다.
--
-- 데이터가 남아 있으면 조용히 지우지 않고 마이그레이션을 실패시킨다.
-- (개발 서버는 세 테이블이 모두 비어 있음을 확인했다. 그 외 환경에 데이터가 있다면 먼저 확인해야 한다.)
--
-- FK 순서: 매핑 테이블(p_lesson_concepts, p_problem_concepts)이 p_concepts를 참조하므로
-- 매핑 테이블을 먼저 지우고 p_concepts를 마지막에 지운다.
-- ============================================================

DO $$
DECLARE
    legacy_table TEXT;
    row_count BIGINT;
BEGIN
    FOREACH legacy_table IN ARRAY ARRAY['p_lesson_concepts', 'p_problem_concepts', 'p_concepts']
    LOOP
        IF to_regclass('content_schema.' || legacy_table) IS NOT NULL THEN
            EXECUTE format('SELECT count(*) FROM content_schema.%I', legacy_table) INTO row_count;
            IF row_count > 0 THEN
                RAISE EXCEPTION
                    '옛 개념 테이블 content_schema.% 에 데이터가 %건 남아 있어 삭제할 수 없습니다. 데이터를 확인한 뒤 다시 실행하세요.',
                    legacy_table, row_count;
            END IF;
        END IF;
    END LOOP;
END $$;

DROP TABLE IF EXISTS content_schema.p_lesson_concepts;
DROP TABLE IF EXISTS content_schema.p_problem_concepts;
DROP TABLE IF EXISTS content_schema.p_concepts;
