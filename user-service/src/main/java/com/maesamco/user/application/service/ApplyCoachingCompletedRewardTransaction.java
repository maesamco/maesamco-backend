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

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 코칭 완료 보상과 학습 스트릭을 한 번의 독립된 트랜잭션으로 반영합니다.
 *
 * <p>Coaching Service의 Outbox는 발행 성공 여부가 불확실할 때 같은
 * {@code coachingId}로 재발행할 수 있습니다. 따라서 코칭 ID를 원천 이벤트 ID로
 * 사용하고, 사전 조회와 DB UNIQUE 인덱스로 중복 지급을 방지합니다.</p>
 *
 * <p>트랜잭션 실패 후 같은 영속성 컨텍스트를 재사용하지 않도록 각 호출은
 * {@link Propagation#REQUIRES_NEW}로 실행됩니다.</p>
 */
@Component
@RequiredArgsConstructor
public class ApplyCoachingCompletedRewardTransaction {

    static final int COACHING_COMPLETED_XP = 5;

    private static final ZoneId ACTIVITY_ZONE_ID =
            ZoneId.of("Asia/Seoul");

    private static final String REWARD_DESCRIPTION =
            "코칭 완료 보상";

    private final UserGamificationStateRepository
            userGamificationStateRepository;

    private final XpHistoryRepository xpHistoryRepository;

    /**
     * @return 새 보상을 반영했으면 {@code true}, 이미 처리한 이벤트면 {@code false}
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean apply(
            ApplyCoachingCompletedRewardCommand command
    ) {
        if (xpHistoryRepository.existsBySourceEventId(
                command.coachingId()
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

        LocalDate activityDate =
                command.completedAt()
                        .atZone(ACTIVITY_ZONE_ID)
                        .toLocalDate();

        state.applyXp(
                COACHING_COMPLETED_XP,
                state.getLevel()
        );

        /*
         * coachingId가 Kafka key라 같은 사용자의 서로 다른 코칭 이벤트는 서로 다른
         * 파티션에서 순서가 바뀌어 도착할 수 있습니다. 늦게 도착한 과거 이벤트는 XP는
         * 지급하되 스트릭을 과거로 되돌리거나 실패시키지 않습니다.
         */
        if (state.getLastActivityDate() == null
                || !activityDate.isBefore(
                state.getLastActivityDate()
        )) {
            state.recordActivity(activityDate);
        }

        userGamificationStateRepository.save(state);

        xpHistoryRepository.save(
                XpHistory.create(
                        command.userId(),
                        command.coachingId(),
                        RewardType.COACHING_COMPLETED,
                        XpSourceType.COACHING,
                        command.coachingId(),
                        command.problemId(),
                        COACHING_COMPLETED_XP,
                        state.getTotalXp(),
                        null,
                        REWARD_DESCRIPTION,
                        command.completedAt()
                )
        );

        return true;
    }
}
