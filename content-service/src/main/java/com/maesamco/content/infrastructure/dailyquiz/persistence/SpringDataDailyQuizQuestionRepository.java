package com.maesamco.content.infrastructure.dailyquiz.persistence;

import com.maesamco.content.domain.dailyquiz.entity.DailyQuizQuestion;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizQuestionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface SpringDataDailyQuizQuestionRepository extends JpaRepository<DailyQuizQuestion, UUID> {

    @Query(
            value = """
                    WITH requested_slots AS (
                        SELECT DISTINCT
                               slot.concept_tag,
                               slot.problem_type
                        FROM unnest(
                            CAST(:conceptTags AS text[]),
                            CAST(:problemTypes AS text[])
                        ) AS slot(concept_tag, problem_type)
                    )
                    SELECT DISTINCT q.*
                    FROM requested_slots rs
                    CROSS JOIN LATERAL (
                        SELECT candidate.*
                        FROM content_schema.p_daily_quiz_questions candidate
                        WHERE candidate.status = 'ACTIVE'
                          AND candidate.problem_type = rs.problem_type
                          AND candidate.concept_tags @> jsonb_build_array(rs.concept_tag)
                        ORDER BY candidate.id
                        LIMIT :limitPerSlotCriteria
                    ) q
                    """,
            nativeQuery = true
    )
    List<DailyQuizQuestion> findActiveByQuestionSlots(
            @Param("conceptTags") String[] conceptTags,
            @Param("problemTypes") String[] problemTypes,
            @Param("limitPerSlotCriteria") int limitPerSlotCriteria
    );

    List<DailyQuizQuestion> findByStatusAndFallbackEligibleTrue(DailyQuizQuestionStatus status);
}
