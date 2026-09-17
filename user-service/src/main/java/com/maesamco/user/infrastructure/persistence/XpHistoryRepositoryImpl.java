package com.maesamco.user.infrastructure.persistence;

import com.maesamco.user.domain.entity.RewardType;
import com.maesamco.user.domain.entity.XpHistory;
import com.maesamco.user.domain.repository.XpHistoryRepository;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 도메인의 XpHistoryRepository를 Spring Data JPA로 구현하는 영속성 어댑터입니다.
 */
@Repository
@RequiredArgsConstructor
public class XpHistoryRepositoryImpl implements XpHistoryRepository {

    private final SpringDataXpHistoryRepository
            springDataXpHistoryRepository;

    @Override
    public XpHistory save(XpHistory xpHistory) {
        try {
            return springDataXpHistoryRepository
                    .saveAndFlush(
                            xpHistory
                    );
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(
                    ErrorCode.XP_HISTORY_ALREADY_EXISTS
            );
        }
    }

    @Override
    public List<XpHistory> findFirstPageByUserId(
            UUID userId,
            int limit
    ) {
        return springDataXpHistoryRepository
                .findByUserIdOrderByEarnedAtDescIdDesc(
                        userId,
                        PageRequest.of(
                                0,
                                limit
                        )
                );
    }

    @Override
    public List<XpHistory> findNextPageByUserId(
            UUID userId,
            Instant cursorEarnedAt,
            UUID cursorId,
            int limit
    ) {
        return springDataXpHistoryRepository
                .findNextPageByUserId(
                        userId,
                        cursorEarnedAt,
                        cursorId,
                        PageRequest.of(
                                0,
                                limit
                        )
                );
    }

    @Override
    public boolean existsBySourceEventId(
            UUID sourceEventId
    ) {
        return springDataXpHistoryRepository
                .existsBySourceEventId(
                        sourceEventId
                );
    }

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
