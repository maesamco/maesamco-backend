package com.maesamco.user.application.port;

/**
 * Redis에서 현재 인증 세션 로그아웃을 원자적으로 처리한 결과입니다.
 */
public enum AuthSessionLogoutResult {

    /**
     * 인증 세션 삭제와 세션 블랙리스트 등록이 완료됐습니다.
     */
    LOGGED_OUT,

    /**
     * 인증 세션은 이미 없지만 세션 블랙리스트 등록은 완료됐습니다.
     */
    SESSION_NOT_FOUND,

    /**
     * 인증된 사용자와 Redis 인증 세션 소유자가 일치하지 않습니다.
     */
    SESSION_OWNER_MISMATCH
}
