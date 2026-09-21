package com.maesamco.user.application.service;

import com.maesamco.user.domain.entity.RewardType;
import com.maesamco.user.domain.repository.XpHistoryRepository;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 최초 정답 보상 반영을 조정하고 동시 갱신 충돌을 재시도합니다.
 *
 * <p>각 시도는 {@link ApplyFirstCorrectRewardTransaction}에서 독립된
 * 트랜잭션으로 실행됩니다. 낙관적 락 충돌 시 최신 상태를 다시 조회할 수 있도록
 * 새 트랜잭션으로 재시도합니다.</p>
 */
@Service
@RequiredArgsConstructor
public class ApplyFirstCorrectRewardService {

    static final int MAX_ATTEMPTS = 3;

    private final ApplyFirstCorrectRewardTransaction rewardTransaction;

    private final XpHistoryRepository xpHistoryRepository;

    /**
     * @return 보상을 새로 반영했으면 {@code true}, 이미 지급했으면 {@code false}
     */
    public boolean apply(ApplyFirstCorrectRewardCommand command) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return rewardTransaction.apply(command);
            } catch (BusinessException exception) {
                if (isCommittedDuplicate(command, exception)) {
                    return false;
                }

                if (!isRetryableConflict(exception)
                        || attempt == MAX_ATTEMPTS) {
                    throw exception;
                }
            }
        }

        throw new IllegalStateException(
                "최초 정답 보상 재시도 흐름이 비정상적으로 종료되었습니다."
        );
    }

    private boolean isCommittedDuplicate(
            ApplyFirstCorrectRewardCommand command,
            BusinessException exception
    ) {
        if (exception.getErrorCode()
                != ErrorCode.XP_HISTORY_ALREADY_EXISTS) {
            return false;
        }

        return xpHistoryRepository.existsBySourceEventId(
                command.submissionId()
        ) || xpHistoryRepository.existsByUserIdAndProblemIdAndRewardType(
                command.userId(),
                command.problemId(),
                RewardType.FIRST_CORRECT
        );
    }

    private boolean isRetryableConflict(BusinessException exception) {
        return exception.getErrorCode()
                == ErrorCode.GAMIFICATION_STATE_CONFLICT;
    }
}
