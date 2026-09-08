package com.maesamco.user.global.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TokenExpirationCalculator의 남은 만료시간 계산 규칙을 검증합니다.
 */
class TokenExpirationCalculatorTest {

    @Test
    @DisplayName(
            "만료 시각까지 정확히 정수 초가 남아 있으면 "
                    + "그 값을 그대로 반환한다"
    )
    void remainingSeconds_exactSeconds() {
        // given
        Instant now =
                Instant.parse(
                        "2026-09-04T01:00:00Z"
                );

        Instant expiresAt =
                now.plusSeconds(900);

        // when
        long result =
                TokenExpirationCalculator
                        .remainingSeconds(
                                now,
                                expiresAt
                        );

        // then
        assertThat(result)
                .isEqualTo(900);
    }

    @Test
    @DisplayName(
            "1초 미만의 시간이 남아 있어도 "
                    + "1초로 올림한다"
    )
    void remainingSeconds_roundsUpMilliseconds() {
        // given
        Instant now =
                Instant.parse(
                        "2026-09-04T01:00:00Z"
                );

        Instant expiresAt =
                now.plusMillis(1);

        // when
        long result =
                TokenExpirationCalculator
                        .remainingSeconds(
                                now,
                                expiresAt
                        );

        // then
        assertThat(result)
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "1초를 초과하는 밀리초가 남아 있으면 "
                    + "다음 초로 올림한다"
    )
    void remainingSeconds_roundsUpPartialSecond() {
        // given
        Instant now =
                Instant.parse(
                        "2026-09-04T01:00:00Z"
                );

        Instant expiresAt =
                now.plusMillis(1001);

        // when
        long result =
                TokenExpirationCalculator
                        .remainingSeconds(
                                now,
                                expiresAt
                        );

        // then
        assertThat(result)
                .isEqualTo(2);
    }

    @Test
    @DisplayName(
            "현재 시각과 만료 시각이 같으면 "
                    + "0을 반환한다"
    )
    void remainingSeconds_sameInstant() {
        // given
        Instant now =
                Instant.parse(
                        "2026-09-04T01:00:00Z"
                );

        // when
        long result =
                TokenExpirationCalculator
                        .remainingSeconds(
                                now,
                                now
                        );

        // then
        assertThat(result)
                .isZero();
    }

    @Test
    @DisplayName(
            "이미 만료된 경우에는 음수가 아니라 "
                    + "0을 반환한다"
    )
    void remainingSeconds_alreadyExpired() {
        // given
        Instant now =
                Instant.parse(
                        "2026-09-04T01:00:00Z"
                );

        Instant expiresAt =
                now.minusSeconds(10);

        // when
        long result =
                TokenExpirationCalculator
                        .remainingSeconds(
                                now,
                                expiresAt
                        );

        // then
        assertThat(result)
                .isZero();
    }

    @Test
    @DisplayName(
            "현재 시각이 null이면 "
                    + "NullPointerException을 발생시킨다"
    )
    void remainingSeconds_nullNow() {
        // given
        Instant expiresAt =
                Instant.parse(
                        "2026-09-04T01:00:00Z"
                );

        // when & then
        assertThatThrownBy(
                () -> TokenExpirationCalculator
                        .remainingSeconds(
                                null,
                                expiresAt
                        )
        )
                .isInstanceOf(
                        NullPointerException.class
                )
                .hasMessage(
                        "현재 시각은 필수입니다."
                );
    }

    @Test
    @DisplayName(
            "만료 시각이 null이면 "
                    + "NullPointerException을 발생시킨다"
    )
    void remainingSeconds_nullExpiresAt() {
        // given
        Instant now =
                Instant.parse(
                        "2026-09-04T01:00:00Z"
                );

        // when & then
        assertThatThrownBy(
                () -> TokenExpirationCalculator
                        .remainingSeconds(
                                now,
                                null
                        )
        )
                .isInstanceOf(
                        NullPointerException.class
                )
                .hasMessage(
                        "만료 시각은 필수입니다."
                );
    }
}
