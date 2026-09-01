package com.maesamco.user.domain.entity;

import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UserGamificationState의 생성 규칙과 상태 변경을 검증하는 단위 테스트입니다.
 */
class UserGamificationStateTest {

    @Test
    @DisplayName("사용자의 게이미피케이션 상태를 기본값으로 생성한다")
    void createWithDefaultState() {
        // given
        UUID userId = UUID.randomUUID();

        // when
        UserGamificationState state =
                UserGamificationState.create(userId);

        // then
        assertThat(state.getUserId()).isEqualTo(userId);
        assertThat(state.getTotalXp()).isZero();
        assertThat(state.getLevel()).isEqualTo(1);
        assertThat(state.getCurrentStreak()).isZero();
        assertThat(state.getLongestStreak()).isZero();
        assertThat(state.getLastActivityDate()).isNull();
        assertThat(state.getVersion()).isNull();
    }

    @Test
    @DisplayName("사용자 ID가 null이면 게이미피케이션 상태를 생성할 수 없다")
    void rejectNullUserId() {
        // when & then
        assertThatThrownBy(() -> UserGamificationState.create(null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> {
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
                            assertThat(exception.getMessage())
                                    .isEqualTo("사용자 ID는 필수입니다.");
                        }
                );
    }

    @Test
    @DisplayName("XP 증감 결과와 계산된 레벨을 반영한다")
    void applyXp() {
        // given
        UserGamificationState state = createState();

        // when
        state.applyXp(150L, 2);
        state.applyXp(-50L, 2);

        // then
        assertThat(state.getTotalXp()).isEqualTo(100L);
        assertThat(state.getLevel()).isEqualTo(2);
    }

    @Test
    @DisplayName("XP 반영 결과가 음수이면 상태를 변경하지 않는다")
    void rejectNegativeTotalXp() {
        // given
        UserGamificationState state = createState();

        // when & then
        assertThatThrownBy(() -> state.applyXp(-1L, 1))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> {
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
                            assertThat(exception.getMessage())
                                    .isEqualTo("누적 XP는 0 이상이어야 합니다.");
                        }
                );
        assertThat(state.getTotalXp()).isZero();
        assertThat(state.getLevel()).isEqualTo(1);
    }

    @Test
    @DisplayName("계산된 레벨이 1보다 작으면 상태를 변경하지 않는다")
    void rejectInvalidLevel() {
        // given
        UserGamificationState state = createState();

        // when & then
        assertThatThrownBy(() -> state.applyXp(100L, 0))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> {
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
                            assertThat(exception.getMessage())
                                    .isEqualTo("레벨은 1 이상이어야 합니다.");
                        }
                );
        assertThat(state.getTotalXp()).isZero();
        assertThat(state.getLevel()).isEqualTo(1);
    }

    @Test
    @DisplayName("첫 학습 활동은 스트릭을 1일부터 시작한다")
    void startStreakOnFirstActivity() {
        // given
        UserGamificationState state = createState();
        LocalDate activityDate = LocalDate.of(2026, 8, 31);

        // when
        state.recordActivity(activityDate);

        // then
        assertThat(state.getCurrentStreak()).isEqualTo(1);
        assertThat(state.getLongestStreak()).isEqualTo(1);
        assertThat(state.getLastActivityDate()).isEqualTo(activityDate);
    }

    @Test
    @DisplayName("같은 날짜의 중복 활동은 스트릭을 증가시키지 않는다")
    void ignoreDuplicateActivityDate() {
        // given
        UserGamificationState state = createState();
        LocalDate activityDate = LocalDate.of(2026, 8, 31);
        state.recordActivity(activityDate);

        // when
        state.recordActivity(activityDate);

        // then
        assertThat(state.getCurrentStreak()).isEqualTo(1);
        assertThat(state.getLongestStreak()).isEqualTo(1);
    }

    @Test
    @DisplayName("다음 날 학습하면 스트릭과 최장 스트릭이 증가한다")
    void continueStreakOnNextDay() {
        // given
        UserGamificationState state = createState();
        LocalDate firstActivityDate = LocalDate.of(2026, 8, 30);
        state.recordActivity(firstActivityDate);

        // when
        state.recordActivity(firstActivityDate.plusDays(1));

        // then
        assertThat(state.getCurrentStreak()).isEqualTo(2);
        assertThat(state.getLongestStreak()).isEqualTo(2);
    }

    @Test
    @DisplayName("학습일을 건너뛰면 현재 스트릭을 1일로 초기화한다")
    void resetCurrentStreakAfterGap() {
        // given
        UserGamificationState state = createState();
        LocalDate firstActivityDate = LocalDate.of(2026, 8, 29);
        state.recordActivity(firstActivityDate);
        state.recordActivity(firstActivityDate.plusDays(1));

        // when
        state.recordActivity(firstActivityDate.plusDays(3));

        // then
        assertThat(state.getCurrentStreak()).isEqualTo(1);
        assertThat(state.getLongestStreak()).isEqualTo(2);
        assertThat(state.getLastActivityDate())
                .isEqualTo(firstActivityDate.plusDays(3));
    }

    @Test
    @DisplayName("마지막 학습일보다 이전 날짜는 스트릭에 반영할 수 없다")
    void rejectPastActivityDate() {
        // given
        UserGamificationState state = createState();
        LocalDate lastActivityDate = LocalDate.of(2026, 8, 31);
        state.recordActivity(lastActivityDate);

        // when & then
        assertThatThrownBy(
                () -> state.recordActivity(lastActivityDate.minusDays(1))
        )
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> {
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
                            assertThat(exception.getMessage())
                                    .isEqualTo(
                                            "마지막 학습일보다 이전 날짜는 반영할 수 없습니다."
                                    );
                        }
                );
        assertThat(state.getLastActivityDate())
                .isEqualTo(lastActivityDate);
    }

    /**
     * 테스트에서 사용할 기본 게이미피케이션 상태를 생성합니다.
     */
    private UserGamificationState createState() {
        return UserGamificationState.create(UUID.randomUUID());
    }
}
