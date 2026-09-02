package com.maesamco.user.domain.repository;

import com.maesamco.user.domain.entity.RewardType;
import com.maesamco.user.domain.entity.XpHistory;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * XP 이력 도메인의 영속성 기능을 정의하는 Repository 인터페이스입니다.
 *
 * <p>도메인 계층이 Spring Data JPA와 같은 특정 기술에 직접 의존하지 않도록
 * XP 이력 저장 및 중복 확인에 필요한 기능만 추상화합니다.</p>
 */
public interface XpHistoryRepository {

    /**
     * XP 이력을 저장합니다.
     *
     * @param xpHistory 저장할 XP 이력
     * @return 저장된 XP 이력
     */
    XpHistory save(XpHistory xpHistory);

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
     * 동일한 사용자에게 같은 문제의 최초 정답 보상이 이미 지급되었는지 확인합니다.
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
     * 동일한 사용자에게 같은 날짜의 일일 보상이 이미 지급되었는지 확인합니다.
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
