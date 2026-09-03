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
     * 현재 Refresh Token hash가 예상한 값과 일치하는 경우
     * 새로운 Refresh Token hash로 원자적으로 교체합니다.
     *
     * <p>세션이 존재하지만 hash가 일치하지 않는 경우 이미 Rotation된
     * Refresh Token이 다시 사용된 것으로 판단하며, 같은 원자 연산 안에서
     * 해당 인증 세션을 폐기해야 합니다.</p>
     *
     * <p>구현체는 비교와 교체를 GET → 비교 → SET처럼 여러 명령으로
     * 분리해서는 안 됩니다. 동시 Refresh 요청에서도 하나의 원자 연산으로
     * 처리되어야 합니다.</p>
     *
     * @param sessionId Rotation할 인증 세션 식별자
     * @param expectedRefreshTokenHash 요청으로 전달된 기존 Refresh Token의 hash
     * @param newRefreshTokenHash 새로 발급된 Refresh Token의 hash
     * @return 원자적 Rotation 처리 결과
     */
    AuthSessionRotationResult rotateRefreshToken(
            UUID sessionId,
            String expectedRefreshTokenHash,
            String newRefreshTokenHash
    );

    /**
     * 세션 식별자에 해당하는 인증 세션을 삭제합니다.
     *
     * @param sessionId 세션 식별자
     */
    void deleteBySessionId(UUID sessionId);
}
