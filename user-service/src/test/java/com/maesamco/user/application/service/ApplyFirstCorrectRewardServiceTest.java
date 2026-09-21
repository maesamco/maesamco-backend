package com.maesamco.user.application.service;

import com.maesamco.user.domain.entity.RewardType;
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
class ApplyFirstCorrectRewardServiceTest {

    private static final UUID SUBMISSION_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PROBLEM_ID =
            UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant JUDGED_AT =
            Instant.parse("2026-09-20T15:30:00Z");

    @Mock
    private ApplyFirstCorrectRewardTransaction rewardTransaction;
    @Mock
    private XpHistoryRepository xpHistoryRepository;

    private ApplyFirstCorrectRewardService rewardService;

    @BeforeEach
    void setUp() {
        rewardService = new ApplyFirstCorrectRewardService(
                rewardTransaction,
                xpHistoryRepository
        );
    }

    @Test
    @DisplayName("첫 시도에서 성공하면 결과를 그대로 반환한다")
    void apply_returnsFirstAttemptResult() {
        ApplyFirstCorrectRewardCommand command = command();
        when(rewardTransaction.apply(command)).thenReturn(true);

        assertThat(rewardService.apply(command)).isTrue();

        verify(rewardTransaction).apply(command);
        verifyNoInteractions(xpHistoryRepository);
    }

    @Test
    @DisplayName("낙관적 락 충돌이면 새 트랜잭션으로 재시도한다")
    void apply_retriesGamificationStateConflict() {
        ApplyFirstCorrectRewardCommand command = command();
        when(rewardTransaction.apply(command))
                .thenThrow(exception(ErrorCode.GAMIFICATION_STATE_CONFLICT))
                .thenReturn(true);

        assertThat(rewardService.apply(command)).isTrue();

        verify(rewardTransaction, times(2)).apply(command);
    }

    @Test
    @DisplayName("낙관적 락 충돌이 계속되면 최대 시도 후 예외를 전파한다")
    void apply_propagatesConflictAfterMaximumAttempts() {
        ApplyFirstCorrectRewardCommand command = command();
        when(rewardTransaction.apply(command))
                .thenThrow(exception(ErrorCode.GAMIFICATION_STATE_CONFLICT));

        assertThatThrownBy(() -> rewardService.apply(command))
                .isInstanceOf(BusinessException.class)
                .extracting(
                        throwable -> ((BusinessException) throwable)
                                .getErrorCode()
                )
                .isEqualTo(ErrorCode.GAMIFICATION_STATE_CONFLICT);

        verify(rewardTransaction, times(
                ApplyFirstCorrectRewardService.MAX_ATTEMPTS
        )).apply(command);
    }

    @Test
    @DisplayName("UNIQUE 충돌 후 같은 submissionId가 존재하면 중복 처리한다")
    void apply_returnsFalseForCommittedSubmissionDuplicate() {
        ApplyFirstCorrectRewardCommand command = command();
        when(rewardTransaction.apply(command))
                .thenThrow(exception(ErrorCode.XP_HISTORY_ALREADY_EXISTS));
        when(xpHistoryRepository.existsBySourceEventId(SUBMISSION_ID))
                .thenReturn(true);

        assertThat(rewardService.apply(command)).isFalse();

        verify(xpHistoryRepository)
                .existsBySourceEventId(SUBMISSION_ID);
        verify(xpHistoryRepository, never())
                .existsByUserIdAndProblemIdAndRewardType(
                        USER_ID,
                        PROBLEM_ID,
                        RewardType.FIRST_CORRECT
                );
    }

    @Test
    @DisplayName("UNIQUE 충돌 후 같은 문제의 최초 정답 이력이 존재하면 중복 처리한다")
    void apply_returnsFalseForCommittedProblemDuplicate() {
        ApplyFirstCorrectRewardCommand command = command();
        when(rewardTransaction.apply(command))
                .thenThrow(exception(ErrorCode.XP_HISTORY_ALREADY_EXISTS));
        when(xpHistoryRepository.existsBySourceEventId(SUBMISSION_ID))
                .thenReturn(false);
        when(xpHistoryRepository.existsByUserIdAndProblemIdAndRewardType(
                USER_ID,
                PROBLEM_ID,
                RewardType.FIRST_CORRECT
        )).thenReturn(true);

        assertThat(rewardService.apply(command)).isFalse();

        verify(xpHistoryRepository)
                .existsByUserIdAndProblemIdAndRewardType(
                        USER_ID,
                        PROBLEM_ID,
                        RewardType.FIRST_CORRECT
                );
    }

    @Test
    @DisplayName("중복 이력이 확인되지 않은 무결성 오류는 전파한다")
    void apply_propagatesUnrelatedXpHistoryConflict() {
        ApplyFirstCorrectRewardCommand command = command();
        when(rewardTransaction.apply(command))
                .thenThrow(exception(ErrorCode.XP_HISTORY_ALREADY_EXISTS));
        when(xpHistoryRepository.existsBySourceEventId(SUBMISSION_ID))
                .thenReturn(false);
        when(xpHistoryRepository.existsByUserIdAndProblemIdAndRewardType(
                USER_ID,
                PROBLEM_ID,
                RewardType.FIRST_CORRECT
        )).thenReturn(false);

        assertThatThrownBy(() -> rewardService.apply(command))
                .isInstanceOf(BusinessException.class)
                .extracting(
                        throwable -> ((BusinessException) throwable)
                                .getErrorCode()
                )
                .isEqualTo(ErrorCode.XP_HISTORY_ALREADY_EXISTS);
    }

    private BusinessException exception(ErrorCode errorCode) {
        return new BusinessException(errorCode);
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
