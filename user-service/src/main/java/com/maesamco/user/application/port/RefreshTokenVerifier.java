package com.maesamco.user.application.port;

/**
 * Refresh Token의 유효성을 검증하는 포트입니다.
 *
 * <p>JWT 라이브러리와 RSA 공개키를 사용하는 실제 검증 방식은
 * infrastructure 계층에서 구현합니다.</p>
 */
public interface RefreshTokenVerifier {

    /**
     * Refresh Token의 서명, 만료시간, 토큰 타입 및 필수 Claim을 검증합니다.
     *
     * @param refreshToken 검증할 원본 Refresh Token
     * @return 검증을 통과한 Refresh Token 정보
     */
    VerifiedRefreshToken verify(String refreshToken);
}
