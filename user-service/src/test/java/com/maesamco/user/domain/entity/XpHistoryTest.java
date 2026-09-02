package com.maesamco.user.domain.entity;

import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * XpHistory의 생성 규칙과 불변식을 검증하는 단위 테스트입니다.
 */
class XpHistoryTest {

    private static final Instant EARNED_AT =
            Instant.parse("2026-09-01T00:00:00Z");

    @Test
    @DisplayName("최초 정답 XP 이력을 생성한다")
    void createFirstCorrectHistory() {
        // given
        UUID userId = UUID.randomUUID();
        UUID sourceEventId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        UUID problemId = UUID.randomUUID();

        // when
        XpHistory history = XpHistory.create(
                userId,
                sourceEventId,
                RewardType.FIRST_CORRECT,
                XpSourceType.SUBMISSION,
                submissionId,
                problemId,
                100,
                250L,
                null,
                "문제 최초 정답",
                EARNED_AT
        );

        // then
        assertThat(history.getId()).isNotNull();
        assertThat(history.getUserId()).isEqualTo(userId);
        assertThat(history.getSourceEventId()).isEqualTo(sourceEventId);
        assertThat(history.getRewardType()).isEqualTo(RewardType.FIRST_CORRECT);
        assertThat(history.getSourceType()).isEqualTo(XpSourceType.SUBMISSION);
        assertThat(history.getSourceId()).isEqualTo(submissionId);
        assertThat(history.getProblemId()).isEqualTo(problemId);
        assertThat(history.getAmount()).isEqualTo(100);
        assertThat(history.getBalanceAfter()).isEqualTo(250L);
        assertThat(history.getRewardDate()).isNull();
        assertThat(history.getDescription()).isEqualTo("문제 최초 정답");
        assertThat(history.getEarnedAt()).isEqualTo(EARNED_AT);
        assertThat(history.getCreatedAt()).isNull();
        assertThat(history.isNew()).isTrue();
    }

    @Test
    @DisplayName("일일 목표 XP 이력은 보상 날짜와 함께 생성한다")
    void createDailyGoalHistory() {
        // given
        LocalDate rewardDate = LocalDate.of(2026, 9, 1);

        // when
        XpHistory history = createHistory(
                RewardType.DAILY_GOAL_COMPLETED,
                XpSourceType.DAILY_ACTIVITY,
                null,
                rewardDate,
                30,
                300L,
                "일일 목표 완료"
        );

        // then
        assertThat(history.getRewardDate()).isEqualTo(rewardDate);
        assertThat(history.getProblemId()).isNull();
    }

    @Test
    @DisplayName("관리자 조정은 XP를 차감할 수 있다")
    void allowNegativeAdminAdjustment() {
        // when
        XpHistory history = createHistory(
                RewardType.ADMIN_ADJUSTMENT,
                XpSourceType.SYSTEM,
                null,
                null,
                -50,
                200L,
                "중복 지급 회수"
        );

        // then
        assertThat(history.getAmount()).isEqualTo(-50);
        assertThat(history.getBalanceAfter()).isEqualTo(200L);
    }

    @Test
    @DisplayName("필수 값이 null이면 XP 이력을 생성할 수 없다")
    void rejectNullRequiredValues() {
        assertInvalidInput(
                () -> XpHistory.create(
                        null,
                        UUID.randomUUID(),
                        RewardType.COACHING_COMPLETED,
                        XpSourceType.COACHING,
                        UUID.randomUUID(),
                        null,
                        50,
                        50L,
                        null,
                        null,
                        EARNED_AT
                ),
                "사용자 ID는 필수입니다."
        );

        assertInvalidInput(
                () -> XpHistory.create(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        null,
                        XpSourceType.COACHING,
                        UUID.randomUUID(),
                        null,
                        50,
                        50L,
                        null,
                        null,
                        EARNED_AT
                ),
                "보상 유형은 필수입니다."
        );

        assertInvalidInput(
                () -> XpHistory.create(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        RewardType.COACHING_COMPLETED,
                        null,
                        UUID.randomUUID(),
                        null,
                        50,
                        50L,
                        null,
                        null,
                        EARNED_AT
                ),
                "원천 유형은 필수입니다."
        );

        assertInvalidInput(
                () -> XpHistory.create(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        RewardType.COACHING_COMPLETED,
                        XpSourceType.COACHING,
                        UUID.randomUUID(),
                        null,
                        50,
                        50L,
                        null,
                        null,
                        null
                ),
                "XP 획득 시각은 필수입니다."
        );
    }

    @Test
    @DisplayName("XP 증감량이 0이면 이력을 생성할 수 없다")
    void rejectZeroAmount() {
        assertInvalidInput(
                () -> createHistory(
                        RewardType.COACHING_COMPLETED,
                        XpSourceType.COACHING,
                        null,
                        null,
                        0,
                        100L,
                        null
                ),
                "XP 증감량은 0일 수 없습니다."
        );
    }

    @Test
    @DisplayName("관리자 조정이 아닌 보상은 XP를 차감할 수 없다")
    void rejectNegativeRewardAmount() {
        assertInvalidInput(
                () -> createHistory(
                        RewardType.COACHING_COMPLETED,
                        XpSourceType.COACHING,
                        null,
                        null,
                        -10,
                        90L,
                        null
                ),
                "XP 차감은 관리자 조정만 허용됩니다."
        );
    }

    @Test
    @DisplayName("XP 반영 후 잔액이 음수이면 이력을 생성할 수 없다")
    void rejectNegativeBalanceAfter() {
        assertInvalidInput(
                () -> createHistory(
                        RewardType.ADMIN_ADJUSTMENT,
                        XpSourceType.SYSTEM,
                        null,
                        null,
                        -10,
                        -1L,
                        null
                ),
                "XP 반영 후 잔액은 0 이상이어야 합니다."
        );
    }

    @Test
    @DisplayName("최초 정답 보상에 문제 ID가 없으면 생성할 수 없다")
    void rejectFirstCorrectWithoutProblemId() {
        assertInvalidInput(
                () -> createHistory(
                        RewardType.FIRST_CORRECT,
                        XpSourceType.SUBMISSION,
                        null,
                        null,
                        100,
                        100L,
                        null
                ),
                "최초 정답 보상에는 문제 ID가 필수입니다."
        );
    }

    @Test
    @DisplayName("일일 목표 보상에 보상 날짜가 없으면 생성할 수 없다")
    void rejectDailyGoalWithoutRewardDate() {
        assertInvalidInput(
                () -> createHistory(
                        RewardType.DAILY_GOAL_COMPLETED,
                        XpSourceType.DAILY_ACTIVITY,
                        null,
                        null,
                        30,
                        30L,
                        null
                ),
                "일일 목표 보상에는 보상 날짜가 필수입니다."
        );
    }

    @Test
    @DisplayName("설명이 255자를 초과하면 XP 이력을 생성할 수 없다")
    void rejectTooLongDescription() {
        assertInvalidInput(
                () -> createHistory(
                        RewardType.COACHING_COMPLETED,
                        XpSourceType.COACHING,
                        null,
                        null,
                        50,
                        50L,
                        "가".repeat(256)
                ),
                "XP 이력 설명은 255자 이하여야 합니다."
        );
    }

    /** 테스트에서 사용할 XP 이력을 생성합니다. */
    private XpHistory createHistory(
            RewardType rewardType,
            XpSourceType sourceType,
            UUID problemId,
            LocalDate rewardDate,
            int amount,
            long balanceAfter,
            String description
    ) {
        return XpHistory.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                rewardType,
                sourceType,
                UUID.randomUUID(),
                problemId,
                amount,
                balanceAfter,
                rewardDate,
                description,
                EARNED_AT
        );
    }

    /** INVALID_INPUT_VALUE 예외의 코드와 메시지를 함께 검증합니다. */
    private void assertInvalidInput(
            ThrowingAction action,
            String expectedMessage
    ) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> {
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
                            assertThat(exception.getMessage())
                                    .isEqualTo(expectedMessage);
                        }
                );
    }

    /** 예외 검증에 사용할 실행 단위입니다. */
    @FunctionalInterface
    private interface ThrowingAction {
        void run();
    }
}
