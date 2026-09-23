package com.maesamco.user.application.service;

import com.maesamco.user.domain.entity.RewardType;
import com.maesamco.user.domain.entity.UserGamificationState;
import com.maesamco.user.domain.entity.XpHistory;
import com.maesamco.user.domain.entity.XpSourceType;
import com.maesamco.user.domain.repository.UserGamificationStateRepository;
import com.maesamco.user.domain.repository.XpHistoryRepository;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자의 문제별 최초 정답 XP를 한 번의 독립된 트랜잭션으로 반영합니다.
 *
 * <p>같은 제출 이벤트의 재발행은 {@code submissionId}로 차단하고, 서로 다른
 * 제출이 동시에 정답 처리되는 경우는 사용자·문제·보상 유형 조합으로 차단합니다.
 * 애플리케이션 사전 조회와 DB 부분 UNIQUE 인덱스를 함께 사용합니다.</p>
 */
@Component
@RequiredArgsConstructor
public class ApplyFirstCorrectRewardTransaction {

    static final int FIRST_CORRECT_XP = 10;

    private static final String REWARD_DESCRIPTION =
            "최초 정답 보상";

    private final UserGamificationStateRepository
            userGamificationStateRepository;

    private final XpHistoryRepository xpHistoryRepository;

    /**
     * @return 보상을 새로 반영했으면 {@code true}, 이미 지급했으면 {@code false}
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean apply(ApplyFirstCorrectRewardCommand command) {
        if (xpHistoryRepository.existsBySourceEventId(
                command.submissionId()
        )) {
            return false;
        }

        if (xpHistoryRepository.existsByUserIdAndProblemIdAndRewardType(
                command.userId(),
                command.problemId(),
                RewardType.FIRST_CORRECT
        )) {
            return false;
        }

        UserGamificationState state =
                userGamificationStateRepository
                        .findByUserId(command.userId())
                        .orElseThrow(
                                () -> new BusinessException(
                                        ErrorCode.GAMIFICATION_STATE_NOT_FOUND
                                )
                        );

        state.applyXp(
                FIRST_CORRECT_XP,
                state.getLevel()
        );

        userGamificationStateRepository.save(state);

        xpHistoryRepository.save(
                XpHistory.create(
                        command.userId(),
                        command.submissionId(),
                        RewardType.FIRST_CORRECT,
                        XpSourceType.SUBMISSION,
                        command.submissionId(),
                        command.problemId(),
                        FIRST_CORRECT_XP,
                        state.getTotalXp(),
                        null,
                        REWARD_DESCRIPTION,
                        command.judgedAt()
                )
        );

        return true;
    }
}
