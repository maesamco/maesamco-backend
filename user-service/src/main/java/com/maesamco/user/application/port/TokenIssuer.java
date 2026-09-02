package com.maesamco.user.application.port;

import com.maesamco.user.domain.entity.UserRole;

import java.util.UUID;

/**
 * 인증 세션에 사용할 토큰 발급 기능을 정의하는 애플리케이션 포트입니다.
 *
 * <p>애플리케이션 계층이 JWT 서명 방식과 구체적인 토큰 라이브러리에
 * 직접 의존하지 않도록 발급 기능만 추상화합니다.</p>
 */
public interface TokenIssuer {

    /**
     * 동일한 세션 식별자를 공유하는 Access Token과 Refresh Token을 발급합니다.
     *
     * @param userId 토큰 소유자의 사용자 식별자
     * @param role 토큰 소유자의 역할
     * @param sessionId 인증 세션 식별자
     * @return 발급된 토큰과 각각의 만료 시각
     */
    IssuedTokens issueTokens(
            UUID userId,
            UserRole role,
            UUID sessionId
    );
}
