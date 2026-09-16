-- 활성 관심 개념의 동일한 사용자·개념 중복 저장을 방지합니다.
-- 삭제된 관심 개념은 재등록할 수 있도록 부분 Unique 인덱스를 사용합니다.

CREATE UNIQUE INDEX uk_p_user_interest_concepts_active_user_concept
    ON user_schema.p_user_interest_concepts (user_id, concept_id)
    WHERE deleted_at IS NULL;
