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
     * 직전 Refresh Token이 grace window 안에 다시 요청되었습니다.
     *
     * <p>멀티탭 또는 네트워크 재시도와 같은 동시 요청일 수 있으므로
     * 현재 인증 세션은 삭제하지 않습니다.</p>
     */
    PREVIOUS_TOKEN_WITHIN_GRACE,

    /**
     * 현재 토큰 또는 grace window 안의 직전 토큰과 일치하지 않는
     * Refresh Token이 사용되었습니다.
     *
     * <p>Refresh Token 탈취 후 재사용 가능성이 있으므로
     * 해당 인증 세션을 폐기합니다.</p>
     */
    TOKEN_REUSED
}
