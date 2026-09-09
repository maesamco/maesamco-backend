package com.maesamco.content.dailyquiz.infrastructure.persistence;

import com.maesamco.content.dailyquiz.domain.entity.DailyQuizQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface SpringDataDailyQuizQuestionRepository extends JpaRepository<DailyQuizQuestion, UUID> {

    @Query(
            value = """
                    WITH requested_concepts AS (
                        SELECT DISTINCT
                               unnest(CAST(:conceptTags AS text[])) AS concept_tag
                    )
                    SELECT DISTINCT q.*
                    FROM requested_concepts rc
                    CROSS JOIN LATERAL (
                        SELECT candidate.*
                        FROM content_schema.p_daily_quiz_questions candidate
                        WHERE candidate.status = 'ACTIVE'
                          AND candidate.concept_tags @> jsonb_build_array(rc.concept_tag)
                        ORDER BY candidate.id
                        LIMIT :limitPerConcept
                    ) q
                    """,
            nativeQuery = true
    )
    List<DailyQuizQuestion> findActiveByAnyConceptTags(
            @Param("conceptTags") String[] conceptTags,
            @Param("limitPerConcept") int limitPerConcept
    );
}
