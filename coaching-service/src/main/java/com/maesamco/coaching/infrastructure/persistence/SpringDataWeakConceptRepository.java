package com.maesamco.coaching.infrastructure.persistence;

import com.maesamco.coaching.domain.entity.WeakConcept;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataWeakConceptRepository extends JpaRepository<WeakConcept, UUID> {

    Optional<WeakConcept> findByUserIdAndConceptTag(UUID userId, String conceptTag);

    List<WeakConcept> findByUserIdOrderByImprovedAscOccurrenceCountDescLastDetectedAtDesc(UUID userId);

    /**
     * PR #166 리뷰(용현님 P1) 대응 — id는 컬럼 DEFAULT(gen_random_uuid())에 맡기고,
     * 최초 생성/재발견 갱신을 한 문장으로 원자 처리한다. improved는 갱신 대상에서 빼서
     * 기존 값을 그대로 유지한다(재발견 시 improved를 되돌릴지는 여전히 미정 — 이 정책을
     * 추적하던 WeakConcept.recordOccurrence()의 TODO는 PR #228에서 죽은 코드와 함께
     * 제거됐다).
     *
     * @Modifying 커스텀 쿼리는 SimpleJpaRepository의 기본 CRUD 메서드와 달리 자동으로
     * 트랜잭션이 걸리지 않는다 — 호출 측에 이미 트랜잭션이 있으면 참여하고, 없으면 이
     * @Transactional이 자체적으로 하나 열어서 그 안에서 실행·flush한다.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO coaching_schema.p_weak_concepts (user_id, concept_tag, occurrence_count, last_detected_at, improved)
            VALUES (:userId, :conceptTag, 1, :detectedAt, false)
            ON CONFLICT (user_id, concept_tag)
            DO UPDATE SET occurrence_count = p_weak_concepts.occurrence_count + 1,
                          last_detected_at = EXCLUDED.last_detected_at
            """, nativeQuery = true)
    void upsertOccurrence(
            @Param("userId") UUID userId,
            @Param("conceptTag") String conceptTag,
            @Param("detectedAt") Instant detectedAt
    );
}
