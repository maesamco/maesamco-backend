package com.maesamco.user.application.port;

import java.util.Optional;
import java.util.UUID;

/**
 * 인증 세션을 저장하고 조회하는 기능을 정의하는 포트입니다.
 *
 * <p>구체적인 Redis 자료구조와 직렬화 방식은 인프라 계층에서
 * 구현합니다.</p>
 */
public interface AuthSessionStore {

    /**
     * 인증 세션을 만료시간과 함께 저장합니다.
     *
     * @param session 저장할 인증 세션
     */
    void save(AuthSession session);

    /**
     * 세션 식별자로 인증 세션을 조회합니다.
     *
     * @param sessionId 세션 식별자
     * @return 존재하는 인증 세션
     */
    Optional<AuthSession> findBySessionId(UUID sessionId);

    /**
     * 세션 식별자에 해당하는 인증 세션을 삭제합니다.
     *
     * @param sessionId 세션 식별자
     */
    void deleteBySessionId(UUID sessionId);
}
