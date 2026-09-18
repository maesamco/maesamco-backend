package com.maesamco.user.domain.repository;

import com.maesamco.user.domain.entity.RewardType;
import com.maesamco.user.domain.entity.XpHistory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * XP 이력 도메인의 영속성 기능을 정의하는 Repository 인터페이스입니다.
 *
 * <p>도메인 계층이 Spring Data JPA와 같은 특정 기술에 직접 의존하지 않도록
 * XP 이력 저장, keyset 조회 및 중복 확인 기능만 추상화합니다.</p>
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
     * 사용자의 XP 이력 첫 페이지를 최신순으로 조회합니다.
     *
     * @param userId 사용자 식별자
     * @param limit 최대 조회 개수
     * @return earnedAt DESC, id DESC 순서의 XP 이력
     */
    List<XpHistory> findFirstPageByUserId(
            UUID userId,
            int limit
    );

    /**
     * cursor 이후의 XP 이력을 최신순으로 조회합니다.
     *
     * @param userId 사용자 식별자
     * @param cursorEarnedAt cursor의 XP 획득 시각
     * @param cursorId cursor의 XP 이력 식별자
     * @param limit 최대 조회 개수
     * @return cursor 이후의 XP 이력
     */
    List<XpHistory> findNextPageByUserId(
            UUID userId,
            Instant cursorEarnedAt,
            UUID cursorId,
            int limit
    );

    /**
     * 동일한 Kafka 원천 이벤트로 생성된 XP 이력이 존재하는지 확인합니다.
     */
    boolean existsBySourceEventId(UUID sourceEventId);

    /**
     * 동일한 사용자에게 같은 문제의 최초 정답 보상이 이미 지급되었는지 확인합니다.
     */
    boolean existsByUserIdAndProblemIdAndRewardType(
            UUID userId,
            UUID problemId,
            RewardType rewardType
    );

    /**
     * 동일한 사용자에게 같은 날짜의 일일 보상이 이미 지급되었는지 확인합니다.
     */
    boolean existsByUserIdAndRewardDateAndRewardType(
            UUID userId,
            LocalDate rewardDate,
            RewardType rewardType
    );
}
