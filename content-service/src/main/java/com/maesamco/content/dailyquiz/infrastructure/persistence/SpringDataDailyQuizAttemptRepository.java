package com.maesamco.content.dailyquiz.infrastructure.persistence;

import com.maesamco.content.dailyquiz.domain.entity.DailyQuizAttempt;
import com.maesamco.content.dailyquiz.domain.entity.DailyQuizAttemptStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

interface SpringDataDailyQuizAttemptRepository
        extends JpaRepository<DailyQuizAttempt, UUID> {

    boolean existsByUserIdAndAttemptDate(UUID userId, LocalDate attemptDate);

    Optional<DailyQuizAttempt> findByUserIdAndAttemptDate(
            UUID userId,
            LocalDate attemptDate
    );

    /**
     * 기대 상태와 일치하는 세트의 상태와 최초 시작 시각을 한 번에 변경합니다.
     * 벌크 UPDATE 전에는 보류 중인 변경을 반영하고, 실행 후에는 영속성 컨텍스트를
     * 비워 이미 조회한 엔티티에 이전 상태가 남지 않도록 합니다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE DailyQuizAttempt attempt
            SET attempt.status = :targetStatus,
                attempt.startedAt = :startedAt
            WHERE attempt.id = :attemptId
              AND attempt.status = :expectedStatus
            """)
    int updateStatusAndStartedAtIfStatus(
            @Param("attemptId") UUID attemptId,
            @Param("expectedStatus") DailyQuizAttemptStatus expectedStatus,
            @Param("targetStatus") DailyQuizAttemptStatus targetStatus,
            @Param("startedAt") Instant startedAt
    );
}
