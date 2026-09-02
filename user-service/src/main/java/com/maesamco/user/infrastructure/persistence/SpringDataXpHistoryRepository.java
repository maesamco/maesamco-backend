package com.maesamco.user.infrastructure.persistence;

import com.maesamco.user.domain.entity.RewardType;
import com.maesamco.user.domain.entity.XpHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA를 이용해 XpHistory 엔티티에 접근하는 내부 Repository입니다.
 *
 * <p>이 인터페이스는 인프라 계층 내부에서만 사용하며,
 * 애플리케이션 계층에서는 도메인의 XpHistoryRepository를 사용합니다.</p>
 */
public interface SpringDataXpHistoryRepository
        extends JpaRepository<XpHistory, UUID> {

    /**
     * 사용자의 XP 이력을 획득 시각 기준 최신순으로 조회합니다.
     *
     * @param userId 사용자 식별자
     * @return 최신순 XP 이력 목록
     */
    List<XpHistory> findAllByUserIdOrderByEarnedAtDesc(UUID userId);

    /**
     * 동일한 Kafka 원천 이벤트로 생성된 XP 이력이 존재하는지 확인합니다.
     *
     * @param sourceEventId Kafka 원천 이벤트 식별자
     * @return 존재하면 true
     */
    boolean existsBySourceEventId(UUID sourceEventId);

    /**
     * 동일한 사용자에게 같은 문제의 보상 이력이 존재하는지 확인합니다.
     *
     * @param userId 사용자 식별자
     * @param problemId 문제 식별자
     * @param rewardType 보상 유형
     * @return 존재하면 true
     */
    boolean existsByUserIdAndProblemIdAndRewardType(
            UUID userId,
            UUID problemId,
            RewardType rewardType
    );

    /**
     * 동일한 사용자에게 같은 날짜의 보상 이력이 존재하는지 확인합니다.
     *
     * @param userId 사용자 식별자
     * @param rewardDate 보상 기준 날짜
     * @param rewardType 보상 유형
     * @return 존재하면 true
     */
    boolean existsByUserIdAndRewardDateAndRewardType(
            UUID userId,
            LocalDate rewardDate,
            RewardType rewardType
    );
}
