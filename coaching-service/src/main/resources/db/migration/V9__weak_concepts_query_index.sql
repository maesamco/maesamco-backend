-- PR #124 심층 재검토(용현님) 반영 — 이슈 #54 조회 API의 실제 접근 패턴은
-- WHERE user_id = ? ORDER BY improved ASC, occurrence_count DESC, last_detected_at DESC
-- 인데, 기존 UNIQUE(user_id, concept_tag) 제약은 user_id 필터엔 쓰이지만 정렬까지는
-- 커버하지 못한다. 이 조회 패턴에 맞춘 복합 인덱스를 추가한다.
CREATE INDEX idx_weak_concepts_user_priority
    ON coaching_schema.p_weak_concepts (user_id, improved, occurrence_count DESC, last_detected_at DESC);
