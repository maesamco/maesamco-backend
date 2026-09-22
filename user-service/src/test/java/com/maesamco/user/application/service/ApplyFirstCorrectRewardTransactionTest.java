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
class ApplyFirstCorrectRewardTransactionTest {

    private static final UUID SUBMISSION_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PROBLEM_ID =
            UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant JUDGED_AT =
            Instant.parse("2026-09-20T15:30:00Z");

    @Mock
    private UserGamificationStateRepository
            userGamificationStateRepository;
    @Mock
    private XpHistoryRepository xpHistoryRepository;

    private ApplyFirstCorrectRewardTransaction rewardTransaction;

    @BeforeEach
    void setUp() {
        rewardTransaction = new ApplyFirstCorrectRewardTransaction(
                userGamificationStateRepository,
                xpHistoryRepository
        );
    }

    @Test
    @DisplayName("문제 최초 정답 시 XP 10을 지급하고 이력을 저장한다")
    void apply_addsFirstCorrectXp() {
        UserGamificationState state =
                UserGamificationState.create(USER_ID);

        when(xpHistoryRepository.existsBySourceEventId(SUBMISSION_ID))
                .thenReturn(false);
        when(xpHistoryRepository.existsByUserIdAndProblemIdAndRewardType(
                USER_ID,
                PROBLEM_ID,
                RewardType.FIRST_CORRECT
        )).thenReturn(false);
        when(userGamificationStateRepository.findByUserId(USER_ID))
                .thenReturn(Optional.of(state));
        when(userGamificationStateRepository.save(state))
                .thenReturn(state);
        when(xpHistoryRepository.save(any(XpHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(rewardTransaction.apply(command())).isTrue();
        assertThat(state.getTotalXp()).isEqualTo(10L);

        ArgumentCaptor<XpHistory> historyCaptor =
                ArgumentCaptor.forClass(XpHistory.class);
        verify(xpHistoryRepository).save(historyCaptor.capture());

        XpHistory history = historyCaptor.getValue();
        assertThat(history.getUserId()).isEqualTo(USER_ID);
        assertThat(history.getSourceEventId()).isEqualTo(SUBMISSION_ID);
        assertThat(history.getRewardType())
                .isEqualTo(RewardType.FIRST_CORRECT);
        assertThat(history.getSourceType())
                .isEqualTo(XpSourceType.SUBMISSION);
        assertThat(history.getProblemId()).isEqualTo(PROBLEM_ID);
        assertThat(history.getAmount()).isEqualTo(10);
        assertThat(history.getBalanceAfter()).isEqualTo(10L);
        assertThat(history.getEarnedAt()).isEqualTo(JUDGED_AT);
    }

    @Test
    @DisplayName("이미 처리한 제출 이벤트이면 XP를 다시 지급하지 않는다")
    void apply_skipsDuplicatedSubmissionEvent() {
        when(xpHistoryRepository.existsBySourceEventId(SUBMISSION_ID))
                .thenReturn(true);

        assertThat(rewardTransaction.apply(command())).isFalse();

        verifyNoInteractions(userGamificationStateRepository);
        verify(xpHistoryRepository, never()).save(any(XpHistory.class));
    }

    @Test
    @DisplayName("같은 문제의 최초 정답 보상이 있으면 다른 제출도 지급하지 않는다")
    void apply_skipsAlreadyRewardedProblem() {
        when(xpHistoryRepository.existsBySourceEventId(SUBMISSION_ID))
                .thenReturn(false);
        when(xpHistoryRepository.existsByUserIdAndProblemIdAndRewardType(
                USER_ID,
                PROBLEM_ID,
                RewardType.FIRST_CORRECT
        )).thenReturn(true);

        assertThat(rewardTransaction.apply(command())).isFalse();

        verifyNoInteractions(userGamificationStateRepository);
        verify(xpHistoryRepository, never()).save(any(XpHistory.class));
    }

    @Test
    @DisplayName("게이미피케이션 상태가 없으면 최초 정답 보상을 실패시킨다")
    void apply_rejectsMissingGamificationState() {
        when(xpHistoryRepository.existsBySourceEventId(SUBMISSION_ID))
                .thenReturn(false);
        when(xpHistoryRepository.existsByUserIdAndProblemIdAndRewardType(
                USER_ID,
                PROBLEM_ID,
                RewardType.FIRST_CORRECT
        )).thenReturn(false);
        when(userGamificationStateRepository.findByUserId(USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> rewardTransaction.apply(command()))
                .isInstanceOf(BusinessException.class)
                .extracting(
                        throwable -> ((BusinessException) throwable)
                                .getErrorCode()
                )
                .isEqualTo(ErrorCode.GAMIFICATION_STATE_NOT_FOUND);
    }

    private ApplyFirstCorrectRewardCommand command() {
        return new ApplyFirstCorrectRewardCommand(
                SUBMISSION_ID,
                USER_ID,
                PROBLEM_ID,
                JUDGED_AT
        );
    }
}
