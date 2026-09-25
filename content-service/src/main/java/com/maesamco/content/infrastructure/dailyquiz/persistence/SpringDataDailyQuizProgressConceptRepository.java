package com.maesamco.content.infrastructure.dailyquiz.persistence;

import com.maesamco.content.domain.entity.TagAttribute;
import com.maesamco.content.domain.entity.problem.ProblemProgress;
import com.maesamco.content.domain.entity.problem.ProblemProgressStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SpringDataDailyQuizProgressConceptRepository extends Repository<ProblemProgress, UUID> {

    boolean existsByUserId(UUID userId);

    @Query("""
            SELECT DISTINCT tag.name
            FROM ProblemProgress progress, ProblemTag problemTag, Tag tag
            WHERE progress.problemId = problemTag.problemId
              AND problemTag.tagId = tag.id
              AND progress.userId = :userId
              AND progress.progressStatus = :status
              AND tag.attribute = :attribute
            ORDER BY tag.name
            """)
    List<String> findConceptTagsByStatus(
            @Param("userId") UUID userId,
            @Param("status") ProblemProgressStatus status,
            @Param("attribute") TagAttribute attribute
    );

    @Query("""
            SELECT DISTINCT tag.name
            FROM ProblemProgress progress, ProblemTag problemTag, Tag tag
            WHERE progress.problemId = problemTag.problemId
              AND problemTag.tagId = tag.id
              AND progress.userId = :userId
              AND progress.progressStatus = :status
              AND progress.solvedAt < :quizDateStart
              AND tag.attribute = :attribute
            ORDER BY tag.name
            """)
    List<String> findConceptTagsByStatusAndSolvedBefore(
            @Param("userId") UUID userId,
            @Param("status") ProblemProgressStatus status,
            @Param("quizDateStart") Instant quizDateStart,
            @Param("attribute") TagAttribute attribute
    );
}
