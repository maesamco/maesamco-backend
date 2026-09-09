package com.maesamco.user.application.port;

import java.time.Instant;
import java.util.UUID;

/**
 * 현재 인증 세션 삭제와 Access Token 세션 블랙리스트 등록을
 * 하나의 원자 연산으로 처리하는 포트입니다.
 */
public interface AuthSessionLogoutStore {

    /**
     * 현재 인증 세션의 소유자를 검증하고 세션을 종료합니다.
     *
     * <p>구현체는 AuthSession 삭제와
     * {@code session:{sessionId}:blacklisted} 등록을
     * 하나의 원자 연산으로 처리해야 합니다.</p>
     *
     * <p>이미 인증 세션이 없는 경우에도 반복 로그아웃을 지원하기 위해
     * 블랙리스트 등록은 완료해야 합니다.</p>
     *
     * @param userId 인증된 사용자 식별자
     * @param sessionId 현재 인증 세션 식별자
     * @param accessTokenExpiresAt 현재 Access Token 만료 시각
     * @return 원자적 로그아웃 처리 결과
     */
    AuthSessionLogoutResult logout(
            UUID userId,
            UUID sessionId,
            Instant accessTokenExpiresAt
    );
}
