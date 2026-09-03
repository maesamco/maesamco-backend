package com.maesamco.user.application.port;

import com.maesamco.user.domain.entity.UserRole;

import java.time.Instant;
import java.util.UUID;

/**
 * 인증 세션에 사용할 토큰 발급 기능을 정의하는 애플리케이션 포트입니다.
 *
 * <p>애플리케이션 계층이 JWT 서명 방식과 구체적인 토큰 라이브러리에
 * 직접 의존하지 않도록 발급 기능만 추상화합니다.</p>
 */
public interface TokenIssuer {

    /**
     * 신규 인증 세션에서 사용할 Access Token과 Refresh Token을 발급합니다.
     *
     * <p>회원가입과 로그인처럼 새로운 인증 세션을 생성할 때 사용하며,
     * Refresh Token 만료 시각은 현재 발급 시각을 기준으로 계산합니다.</p>
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

    /**
     * 기존 인증 세션의 Refresh Token을 Rotation하면서
     * 새로운 Access Token과 Refresh Token을 발급합니다.
     *
     * <p>Refresh Token의 절대 만료시간을 연장하지 않기 위해
     * 기존 인증 세션의 만료 시각을 그대로 전달받아 사용합니다.</p>
     *
     * @param userId 토큰 소유자의 사용자 식별자
     * @param role 토큰 소유자의 역할
     * @param sessionId 기존 인증 세션 식별자
     * @param refreshTokenExpiresAt 기존 인증 세션의 절대 만료 시각
     * @return Rotation으로 발급된 토큰과 각각의 만료 시각
     */
    IssuedTokens issueRotatedTokens(
            UUID userId,
            UserRole role,
            UUID sessionId,
            Instant refreshTokenExpiresAt
    );
}
