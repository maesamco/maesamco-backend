package com.maesamco.user.infrastructure.persistence;

import com.maesamco.user.domain.entity.RewardType;
import com.maesamco.user.domain.entity.XpHistory;
import com.maesamco.user.domain.repository.XpHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 도메인의 XpHistoryRepository를 Spring Data JPA로 구현하는 영속성 어댑터입니다.
 *
 * <p>도메인 계층의 Repository 요청을 SpringDataXpHistoryRepository에 위임하여
 * 도메인 계층과 JPA 기술 사이의 의존성을 분리합니다.</p>
 */
@Repository
@RequiredArgsConstructor
public class XpHistoryRepositoryImpl implements XpHistoryRepository {

    /**
     * 실제 JPA 저장과 조회를 담당하는 내부 Repository입니다.
     */
    private final SpringDataXpHistoryRepository springDataXpHistoryRepository;

    /**
     * {@inheritDoc}
     */
    @Override
    public XpHistory save(XpHistory xpHistory) {
        return springDataXpHistoryRepository.save(xpHistory);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<XpHistory> findAllByUserIdOrderByEarnedAtDesc(UUID userId) {
        return springDataXpHistoryRepository
                .findAllByUserIdOrderByEarnedAtDesc(userId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean existsBySourceEventId(UUID sourceEventId) {
        return springDataXpHistoryRepository
                .existsBySourceEventId(sourceEventId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean existsByUserIdAndProblemIdAndRewardType(
            UUID userId,
            UUID problemId,
            RewardType rewardType
    ) {
        return springDataXpHistoryRepository
                .existsByUserIdAndProblemIdAndRewardType(
                        userId,
                        problemId,
                        rewardType
                );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean existsByUserIdAndRewardDateAndRewardType(
            UUID userId,
            LocalDate rewardDate,
            RewardType rewardType
    ) {
        return springDataXpHistoryRepository
                .existsByUserIdAndRewardDateAndRewardType(
                        userId,
                        rewardDate,
                        rewardType
                );
    }
}
