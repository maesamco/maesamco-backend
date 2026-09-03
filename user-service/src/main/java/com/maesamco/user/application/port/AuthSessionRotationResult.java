package com.maesamco.user.application.port;

/**
 * Refresh Token Rotation을 원자적으로 처리한 결과입니다.
 *
 * <p>구체적인 Redis Lua Script 실행 결과를 애플리케이션 계층이
 * Redis 구현 세부사항 없이 판단할 수 있도록 정의합니다.</p>
 */
public enum AuthSessionRotationResult {

    /**
     * 기존 Refresh Token hash가 현재 세션의 hash와 일치하여
     * 새로운 Refresh Token hash로 정상 교체되었습니다.
     */
    ROTATED,

    /**
     * 요청한 인증 세션이 존재하지 않거나 이미 만료되었습니다.
     */
    SESSION_NOT_FOUND,

    /**
     * 세션은 존재하지만 요청한 Refresh Token hash가 현재 저장된 hash와 다릅니다.
     *
     * <p>이미 Rotation된 이전 Refresh Token의 재사용으로 판단하며,
     * 보안을 위해 해당 인증 세션도 함께 폐기합니다.</p>
     */
    TOKEN_REUSED
}
