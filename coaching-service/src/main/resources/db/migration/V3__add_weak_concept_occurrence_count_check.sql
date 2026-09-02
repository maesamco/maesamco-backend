-- PR #34 리뷰에서 용현님이 남긴 P3(개선 제안), 이슈 #45.
--
-- WeakConcept.occurrenceCount는 도메인상 최초 생성 시 1이고 이후 증가만 하므로 항상 1
-- 이상이어야 하는데, V1 베이스라인엔 CHECK 제약이 없어 DB 레벨에서는 0이나 음수도
-- 저장할 수 있었다. Flyway가 스키마의 source of truth인 지금(ddl-auto=validate) 도메인
-- 불변식을 DB에서도 같이 보호한다.

ALTER TABLE coaching_schema.p_weak_concepts
    ADD CONSTRAINT chk_weak_concepts_occurrence_count CHECK (occurrence_count >= 1);
