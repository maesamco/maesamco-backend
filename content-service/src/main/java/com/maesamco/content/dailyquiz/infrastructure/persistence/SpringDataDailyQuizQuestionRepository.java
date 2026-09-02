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
                    SELECT q.*
                    FROM content_schema.p_daily_quiz_questions q
                    WHERE q.status = 'ACTIVE'
                      AND q.concept_tags ?| CAST(:conceptTags AS text[])
                    """,
            nativeQuery = true
    )
    List<DailyQuizQuestion> findActiveByAnyConceptTags(
            @Param("conceptTags") String[] conceptTags
    );
}
