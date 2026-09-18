package com.maesamco.user.infrastructure.persistence;

import com.maesamco.user.domain.entity.RewardType;
import com.maesamco.user.domain.entity.XpHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA를 이용해 XpHistory 엔티티에 접근하는 내부 Repository입니다.
 */
public interface SpringDataXpHistoryRepository
        extends JpaRepository<XpHistory, UUID> {

    /**
     * 사용자의 XP 이력을 안정적인 최신순으로 제한 조회합니다.
     */
    List<XpHistory> findByUserIdOrderByEarnedAtDescIdDesc(
            UUID userId,
            Pageable pageable
    );

    /**
     * 주어진 cursor보다 뒤에 위치한 XP 이력을 keyset 방식으로 조회합니다.
     */
    @Query(
            """
            SELECT history
            FROM XpHistory history
            WHERE history.userId = :userId
              AND (
                    history.earnedAt < :cursorEarnedAt
                    OR (
                        history.earnedAt = :cursorEarnedAt
                        AND history.id < :cursorId
                    )
              )
            ORDER BY history.earnedAt DESC, history.id DESC
            """
    )
    List<XpHistory> findNextPageByUserId(
            @Param("userId")
            UUID userId,

            @Param("cursorEarnedAt")
            Instant cursorEarnedAt,

            @Param("cursorId")
            UUID cursorId,

            Pageable pageable
    );

    boolean existsBySourceEventId(UUID sourceEventId);

    boolean existsByUserIdAndProblemIdAndRewardType(
            UUID userId,
            UUID problemId,
            RewardType rewardType
    );

    boolean existsByUserIdAndRewardDateAndRewardType(
            UUID userId,
            LocalDate rewardDate,
            RewardType rewardType
    );
}
