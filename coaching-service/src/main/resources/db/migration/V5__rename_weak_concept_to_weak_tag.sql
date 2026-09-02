-- Content Service가 p_concepts를 p_tags로 통합하면서(2026-09-02) "개념"이라는 용어 자체를
-- 없애고 "태그"로 통일했다. Coaching Service가 이 테이블/컬럼에 담는 문자열도 결국 Content의
-- 태그명이라 같은 이유로 개명한다.

ALTER TABLE coaching_schema.p_weak_concepts RENAME TO p_weak_tags;
ALTER TABLE coaching_schema.p_weak_tags RENAME COLUMN concept_tag TO tag;
ALTER TABLE coaching_schema.p_weak_tags
    RENAME CONSTRAINT p_weak_concepts_user_id_concept_tag_key TO uk_weak_tags_user_tag;
ALTER TABLE coaching_schema.p_weak_tags
    RENAME CONSTRAINT chk_weak_concepts_occurrence_count TO chk_weak_tags_occurrence_count;

COMMENT ON TABLE coaching_schema.p_weak_tags IS '사용자별 취약 태그 집계 — 사용자-태그당 1행, 발견 시 count만 증가';
COMMENT ON COLUMN coaching_schema.p_weak_tags.user_id IS '논리 FK → User Service p_users.id';

ALTER TABLE coaching_schema.p_ai_feedbacks RENAME COLUMN understood_concepts TO understood_tags;
ALTER TABLE coaching_schema.p_ai_feedbacks RENAME COLUMN weak_concepts TO weak_tags;
COMMENT ON COLUMN coaching_schema.p_ai_feedbacks.weak_tags IS '요약 값 — 상세 집계는 p_weak_tags';
