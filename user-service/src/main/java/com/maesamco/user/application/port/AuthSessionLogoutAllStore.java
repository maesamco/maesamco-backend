package com.maesamco.user.application.port;

import java.time.Instant;
import java.util.UUID;

/**
 * 사용자의 모든 인증 세션을 종료하고
 * 기존 Access Token을 사용자 단위로 무효화하는 기능을 정의합니다.
 *
 * <p>구체적인 Redis 자료구조와 원자 처리 방식은
 * 인프라 계층에서 구현합니다.</p>
 */
public interface AuthSessionLogoutAllStore {

    /**
     * 사용자의 모든 인증 세션을 제거하고
     * 전체 로그아웃 시각을 기록합니다.
     *
     * <p>{@code invalidatedAt} 이전에 발급된 Access Token은
     * 더 이상 사용할 수 없어야 합니다.</p>
     *
     * @param userId 전체 로그아웃 대상 사용자 식별자
     * @param invalidatedAt 기존 Access Token 무효화 기준 시각
     */
    void logoutAll(
            UUID userId,
            Instant invalidatedAt
    );
}
