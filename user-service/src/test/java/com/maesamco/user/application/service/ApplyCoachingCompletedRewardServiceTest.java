package com.maesamco.user.application.service;

import com.maesamco.user.domain.entity.RewardType;
import com.maesamco.user.domain.entity.UserGamificationState;
import com.maesamco.user.domain.entity.XpHistory;
import com.maesamco.user.domain.entity.XpSourceType;
import com.maesamco.user.domain.repository.UserGamificationStateRepository;
import com.maesamco.user.domain.repository.XpHistoryRepository;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplyCoachingCompletedRewardServiceTest {

    private static final UUID COACHING_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final UUID USER_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static final UUID SUBMISSION_ID =
            UUID.fromString("33333333-3333-3333-3333-333333333333");

    private static final UUID PROBLEM_ID =
            UUID.fromString("44444444-4444-4444-4444-444444444444");

    private static final Instant COMPLETED_AT =
            Instant.parse("2026-09-20T15:30:00Z");

    @Mock
    private UserGamificationStateRepository
            userGamificationStateRepository;

    @Mock
    private XpHistoryRepository xpHistoryRepository;

    private ApplyCoachingCompletedRewardService rewardService;

    @BeforeEach
    void setUp() {
        rewardService = new ApplyCoachingCompletedRewardService(
                userGamificationStateRepository,
                xpHistoryRepository
        );
    }

    @Test
    @DisplayName("코칭 완료 시 XP 5와 Asia/Seoul 기준 스트릭을 함께 반영한다")
    void apply_addsXpAndUpdatesStreak() {
        UserGamificationState state =
                UserGamificationState.create(USER_ID);
        state.applyXp(10, 1);
        state.recordActivity(LocalDate.of(2026, 9, 20));

        when(xpHistoryRepository.existsBySourceEventId(COACHING_ID))
                .thenReturn(false);
        when(userGamificationStateRepository.findByUserId(USER_ID))
                .thenReturn(Optional.of(state));
        when(userGamificationStateRepository.save(state))
                .thenReturn(state);
        when(xpHistoryRepository.save(any(XpHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        boolean applied = rewardService.apply(command(COMPLETED_AT));

        assertThat(applied).isTrue();
        assertThat(state.getTotalXp()).isEqualTo(15L);
        assertThat(state.getLevel()).isEqualTo(1);
        assertThat(state.getCurrentStreak()).isEqualTo(2);
        assertThat(state.getLongestStreak()).isEqualTo(2);
        assertThat(state.getLastActivityDate())
                .isEqualTo(LocalDate.of(2026, 9, 21));

        ArgumentCaptor<XpHistory> historyCaptor =
                ArgumentCaptor.forClass(XpHistory.class);
        verify(xpHistoryRepository).save(historyCaptor.capture());

        XpHistory history = historyCaptor.getValue();
        assertThat(history.getUserId()).isEqualTo(USER_ID);
        assertThat(history.getSourceEventId()).isEqualTo(COACHING_ID);
        assertThat(history.getRewardType())
                .isEqualTo(RewardType.COACHING_COMPLETED);
        assertThat(history.getSourceType()).isEqualTo(XpSourceType.COACHING);
        assertThat(history.getSourceId()).isEqualTo(COACHING_ID);
        assertThat(history.getProblemId()).isEqualTo(PROBLEM_ID);
        assertThat(history.getAmount()).isEqualTo(5);
        assertThat(history.getBalanceAfter()).isEqualTo(15L);
        assertThat(history.getRewardDate()).isNull();
        assertThat(history.getDescription()).isEqualTo("코칭 완료 보상");
        assertThat(history.getEarnedAt()).isEqualTo(COMPLETED_AT);
    }

    @Test
    @DisplayName("이미 처리한 coachingId이면 XP와 스트릭을 다시 반영하지 않는다")
    void apply_skipsDuplicatedEvent() {
        when(xpHistoryRepository.existsBySourceEventId(COACHING_ID))
                .thenReturn(true);

        boolean applied = rewardService.apply(command(COMPLETED_AT));

        assertThat(applied).isFalse();
        verifyNoInteractions(userGamificationStateRepository);
        verify(xpHistoryRepository, never()).save(any(XpHistory.class));
    }

    @Test
    @DisplayName("게이미피케이션 상태가 없으면 이벤트 처리를 실패시킨다")
    void apply_rejectsMissingGamificationState() {
        when(xpHistoryRepository.existsBySourceEventId(COACHING_ID))
                .thenReturn(false);
        when(userGamificationStateRepository.findByUserId(USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> rewardService.apply(command(COMPLETED_AT))
        )
                .isInstanceOf(BusinessException.class)
                .extracting(
                        exception -> ((BusinessException) exception)
                                .getErrorCode()
                )
                .isEqualTo(ErrorCode.GAMIFICATION_STATE_NOT_FOUND);

        verify(xpHistoryRepository, never()).save(any(XpHistory.class));
    }

    @Test
    @DisplayName("늦게 도착한 과거 코칭 이벤트는 XP만 반영하고 스트릭을 되돌리지 않는다")
    void apply_doesNotRewindStreakForDelayedEvent() {
        UserGamificationState state =
                UserGamificationState.create(USER_ID);
        state.recordActivity(LocalDate.of(2026, 9, 21));

        when(xpHistoryRepository.existsBySourceEventId(COACHING_ID))
                .thenReturn(false);
        when(userGamificationStateRepository.findByUserId(USER_ID))
                .thenReturn(Optional.of(state));
        when(userGamificationStateRepository.save(state))
                .thenReturn(state);
        when(xpHistoryRepository.save(any(XpHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        boolean applied = rewardService.apply(
                command(Instant.parse("2026-09-19T14:00:00Z"))
        );

        assertThat(applied).isTrue();
        assertThat(state.getTotalXp()).isEqualTo(5L);
        assertThat(state.getCurrentStreak()).isEqualTo(1);
        assertThat(state.getLastActivityDate())
                .isEqualTo(LocalDate.of(2026, 9, 21));
    }

    private ApplyCoachingCompletedRewardCommand command(
            Instant completedAt
    ) {
        return new ApplyCoachingCompletedRewardCommand(
                COACHING_ID,
                USER_ID,
                SUBMISSION_ID,
                PROBLEM_ID,
                completedAt
        );
    }
}
