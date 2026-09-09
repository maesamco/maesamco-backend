package com.maesamco.user.infrastructure.security.session;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 인증 세션과 Refresh Token Rotation에 사용할 설정입니다.
 *
 * @param refreshTokenRotationGracePeriod 정상적인 동시 Refresh 요청으로
 *                                        판단할 유예 시간
 */
@Validated
@ConfigurationProperties(prefix = "security.auth-session")
public record AuthSessionProperties(
        @NotNull Duration refreshTokenRotationGracePeriod
) {

    /**
     * Refresh Token Rotation 유예 시간이 양수인지 검증합니다.
     */
    public AuthSessionProperties {
        if (refreshTokenRotationGracePeriod != null
                && (refreshTokenRotationGracePeriod.isZero()
                || refreshTokenRotationGracePeriod.isNegative())) {
            throw new IllegalArgumentException(
                    "Refresh Token Rotation 유예 시간은 양수여야 합니다."
            );
        }
    }
}
