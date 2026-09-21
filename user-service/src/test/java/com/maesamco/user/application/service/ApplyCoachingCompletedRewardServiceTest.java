package com.maesamco.user.application.service;

import com.maesamco.user.domain.repository.XpHistoryRepository;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    private ApplyCoachingCompletedRewardTransaction rewardTransaction;
    @Mock
    private XpHistoryRepository xpHistoryRepository;

    private ApplyCoachingCompletedRewardService rewardService;

    @BeforeEach
    void setUp() {
        rewardService = new ApplyCoachingCompletedRewardService(
                rewardTransaction,
                xpHistoryRepository
        );
    }

    @Test
    @DisplayName("첫 시도에서 성공하면 결과를 그대로 반환한다")
    void apply_returnsFirstAttemptResult() {
        ApplyCoachingCompletedRewardCommand command = command();
        when(rewardTransaction.apply(command)).thenReturn(true);

        boolean applied = rewardService.apply(command);

        assertThat(applied).isTrue();
        verify(rewardTransaction).apply(command);
        verifyNoInteractions(xpHistoryRepository);
    }

    @Test
    @DisplayName("낙관적 락 충돌이면 새 트랜잭션으로 재시도한다")
    void apply_retriesGamificationStateConflict() {
        ApplyCoachingCompletedRewardCommand command = command();
        when(rewardTransaction.apply(command))
                .thenThrow(conflict(ErrorCode.GAMIFICATION_STATE_CONFLICT))
                .thenReturn(true);

        boolean applied = rewardService.apply(command);

        assertThat(applied).isTrue();
        verify(rewardTransaction, times(2)).apply(command);
        verifyNoInteractions(xpHistoryRepository);
    }

    @Test
    @DisplayName("낙관적 락 충돌이 최대 횟수만큼 계속되면 마지막 예외를 전파한다")
    void apply_propagatesConflictAfterMaximumAttempts() {
        ApplyCoachingCompletedRewardCommand command = command();
        when(rewardTransaction.apply(command))
                .thenThrow(conflict(ErrorCode.GAMIFICATION_STATE_CONFLICT));

        assertThatThrownBy(() -> rewardService.apply(command))
                .isInstanceOf(BusinessException.class)
                .extracting(
                        exception -> ((BusinessException) exception)
                                .getErrorCode()
                )
                .isEqualTo(ErrorCode.GAMIFICATION_STATE_CONFLICT);

        verify(rewardTransaction, times(
                ApplyCoachingCompletedRewardService.MAX_ATTEMPTS
        )).apply(command);
    }

    @Test
    @DisplayName("UNIQUE 충돌 후 같은 coachingId가 존재하면 처리 완료된 중복으로 본다")
    void apply_returnsFalseForCommittedDuplicate() {
        ApplyCoachingCompletedRewardCommand command = command();
        when(rewardTransaction.apply(command))
                .thenThrow(conflict(ErrorCode.XP_HISTORY_ALREADY_EXISTS));
        when(xpHistoryRepository.existsBySourceEventId(COACHING_ID))
                .thenReturn(true);

        boolean applied = rewardService.apply(command);

        assertThat(applied).isFalse();
        verify(rewardTransaction).apply(command);
        verify(xpHistoryRepository)
                .existsBySourceEventId(COACHING_ID);
    }

    @Test
    @DisplayName("XP 이력 무결성 오류여도 같은 coachingId가 없으면 예외를 전파한다")
    void apply_propagatesUnrelatedXpHistoryConflict() {
        ApplyCoachingCompletedRewardCommand command = command();
        when(rewardTransaction.apply(command))
                .thenThrow(conflict(ErrorCode.XP_HISTORY_ALREADY_EXISTS));
        when(xpHistoryRepository.existsBySourceEventId(COACHING_ID))
                .thenReturn(false);

        assertThatThrownBy(() -> rewardService.apply(command))
                .isInstanceOf(BusinessException.class)
                .extracting(
                        exception -> ((BusinessException) exception)
                                .getErrorCode()
                )
                .isEqualTo(ErrorCode.XP_HISTORY_ALREADY_EXISTS);

        verify(rewardTransaction).apply(command);
    }

    @Test
    @DisplayName("재시도 대상이 아닌 예외는 즉시 전파한다")
    void apply_propagatesNonRetryableException() {
        ApplyCoachingCompletedRewardCommand command = command();
        when(rewardTransaction.apply(command))
                .thenThrow(conflict(ErrorCode.GAMIFICATION_STATE_NOT_FOUND));

        assertThatThrownBy(() -> rewardService.apply(command))
                .isInstanceOf(BusinessException.class)
                .extracting(
                        exception -> ((BusinessException) exception)
                                .getErrorCode()
                )
                .isEqualTo(ErrorCode.GAMIFICATION_STATE_NOT_FOUND);

        verify(rewardTransaction).apply(command);
        verify(xpHistoryRepository, never())
                .existsBySourceEventId(COACHING_ID);
    }

    private BusinessException conflict(ErrorCode errorCode) {
        return new BusinessException(errorCode);
    }

    private ApplyCoachingCompletedRewardCommand command() {
        return new ApplyCoachingCompletedRewardCommand(
                COACHING_ID,
                USER_ID,
                SUBMISSION_ID,
                PROBLEM_ID,
                COMPLETED_AT
        );
    }
}
