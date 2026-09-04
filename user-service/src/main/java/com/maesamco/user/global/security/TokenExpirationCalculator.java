package com.maesamco.user.global.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * JWT 및 인증 Cookie의 남은 만료 시간을
 * 초 단위로 계산하는 공통 유틸리티입니다.
 */
public final class TokenExpirationCalculator {

    private TokenExpirationCalculator() {
        throw new IllegalStateException(
                "유틸리티 클래스는 인스턴스화할 수 없습니다."
        );
    }

    /**
     * 현재 시각부터 만료 시각까지 남은 시간을 초 단위로 계산합니다.
     *
     * <p>밀리초 단위의 일부 시간이 남아 있는 경우 초 단위로 올림하여
     * 실제 만료 시각보다 짧은 값을 반환하지 않도록 합니다.</p>
     *
     * <p>이미 만료된 경우에는 0을 반환합니다.</p>
     *
     * @param now 현재 시각
     * @param expiresAt 만료 시각
     * @return 0 이상의 남은 시간(초)
     */
    public static long remainingSeconds(
            Instant now,
            Instant expiresAt
    ) {
        Objects.requireNonNull(
                now,
                "현재 시각은 필수입니다."
        );

        Objects.requireNonNull(
                expiresAt,
                "만료 시각은 필수입니다."
        );

        long remainingMillis =
                Duration.between(
                        now,
                        expiresAt
                ).toMillis();

        if (remainingMillis <= 0) {
            return 0;
        }

        return (remainingMillis + 999L) / 1000L;
    }
}
