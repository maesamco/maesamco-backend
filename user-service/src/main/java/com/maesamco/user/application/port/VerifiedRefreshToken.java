package com.maesamco.user.application.port;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 서명과 만료시간, 토큰 타입 검증을 통과한 Refresh Token 정보입니다.
 *
 * <p>애플리케이션 계층이 JWT 라이브러리의 Claim 타입에 직접 의존하지 않도록
 * Refresh 처리에 필요한 값만 전달합니다.</p>
 *
 * @param userId Refresh Token 소유자 식별자
 * @param sessionId 인증 세션 식별자
 * @param tokenId JWT ID(jti)
 * @param expiresAt Refresh Token 만료 시각
 */
public record VerifiedRefreshToken(
        UUID userId,
        UUID sessionId,
        String tokenId,
        Instant expiresAt
) {

    /**
     * 검증된 Refresh Token 정보의 필수값을 확인합니다.
     */
    public VerifiedRefreshToken {
        Objects.requireNonNull(
                userId,
                "사용자 식별자는 필수입니다."
        );
        Objects.requireNonNull(
                sessionId,
                "세션 식별자는 필수입니다."
        );
        Objects.requireNonNull(
                tokenId,
                "토큰 식별자는 필수입니다."
        );
        Objects.requireNonNull(
                expiresAt,
                "토큰 만료 시각은 필수입니다."
        );

        if (tokenId.isBlank()) {
            throw new IllegalArgumentException(
                    "토큰 식별자는 비어 있을 수 없습니다."
            );
        }
    }
}
