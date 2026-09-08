package com.maesamco.user.application.service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 현재 인증 세션 로그아웃에 필요한 입력값입니다.
 *
 * @param userId 인증된 사용자 식별자
 * @param sessionId 현재 인증 세션 식별자
 * @param accessTokenExpiresAt 현재 Access Token 만료 시각
 */
public record LogoutCommand(
        UUID userId,
        UUID sessionId,
        Instant accessTokenExpiresAt
) {

    /**
     * 로그아웃 입력값의 필수값을 검증합니다.
     */
    public LogoutCommand {
        Objects.requireNonNull(
                userId,
                "사용자 식별자는 필수입니다."
        );

        Objects.requireNonNull(
                sessionId,
                "세션 식별자는 필수입니다."
        );

        Objects.requireNonNull(
                accessTokenExpiresAt,
                "Access Token 만료 시각은 필수입니다."
        );
    }
}
