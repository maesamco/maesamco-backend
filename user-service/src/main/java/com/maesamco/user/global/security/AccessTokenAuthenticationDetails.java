package com.maesamco.user.global.security;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Access Token에서 추출한 현재 인증 세션 정보를 보관합니다.
 *
 * <p>로그아웃 처리에서 현재 세션만 무효화하고,
 * 세션 블랙리스트의 TTL을 계산할 때 사용합니다.</p>
 *
 * @param sessionId Access Token에 포함된 인증 세션 식별자
 * @param expiresAt Access Token 만료 시각
 */
public record AccessTokenAuthenticationDetails(
        UUID sessionId,
        Instant expiresAt
) {

    /**
     * 인증 상세 정보의 필수값을 검증합니다.
     */
    public AccessTokenAuthenticationDetails {
        Objects.requireNonNull(
                sessionId,
                "세션 식별자는 필수입니다."
        );

        Objects.requireNonNull(
                expiresAt,
                "Access Token 만료 시각은 필수입니다."
        );
    }
}
