package com.maesamco.user.application.port;

/**
 * Refresh Token의 단방향 해시와 검증 기능을 정의하는 포트입니다.
 *
 * <p>Refresh Token 원문이 Redis에 저장되지 않도록 애플리케이션 계층과
 * 구체적인 해시 구현을 분리합니다.</p>
 */
public interface RefreshTokenHasher {

    /**
     * Refresh Token을 저장 가능한 단방향 해시로 변환합니다.
     *
     * @param refreshToken 해시할 Refresh Token
     * @return Refresh Token 해시
     */
    String hash(String refreshToken);

    /**
     * Refresh Token이 저장된 해시와 일치하는지 확인합니다.
     *
     * @param refreshToken 확인할 Refresh Token
     * @param refreshTokenHash 저장된 Refresh Token 해시
     * @return 일치하면 true
     */
    boolean matches(
            String refreshToken,
            String refreshTokenHash
    );
}
