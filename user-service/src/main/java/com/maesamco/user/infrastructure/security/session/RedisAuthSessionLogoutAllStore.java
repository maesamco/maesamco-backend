package com.maesamco.user.infrastructure.security.session;

import com.maesamco.user.application.port.AuthSessionLogoutAllStore;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import com.maesamco.user.global.security.JwtProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Redis Lua Script를 이용해
 * 사용자의 모든 인증 세션을 종료합니다.
 *
 * <p>{@code user:{userId}:sessions} 인덱스를 이용해
 * 사용자의 모든 {@code session:{sessionId}}를 제거하고,
 * {@code user:{userId}:invalidatedAt}에 전체 로그아웃 시각을 기록합니다.</p>
 *
 * <p>사용자 단위 무효화 정보는 Access Token과 Refresh Token 중
 * 더 긴 TTL 동안 유지하여 전체 로그아웃 이전에 발급된
 * Access Token의 사용과 Refresh Token의 회전을 모두 차단합니다.</p>
 */
@Repository
public class RedisAuthSessionLogoutAllStore
        implements AuthSessionLogoutAllStore {

    private static final String SESSION_KEY_PREFIX =
            "session:";

    private static final String USER_KEY_PREFIX =
            "user:";

    private static final String USER_SESSION_INDEX_KEY_SUFFIX =
            ":sessions";

    private static final String USER_INVALIDATED_AT_KEY_SUFFIX =
            ":invalidatedAt";

    private static final long LOGOUT_ALL_SUCCESS = 1L;

    /**
     * 사용자 전체 세션 삭제와
     * 사용자 단위 토큰 무효화를 원자적으로 수행합니다.
     *
     * <p>동일 사용자에 대한 전체 로그아웃 요청이 동시에 발생하더라도
     * 더 최신 invalidatedAt 값과 더 긴 TTL을 유지합니다.</p>
     */
    private static final DefaultRedisScript<Long> LOGOUT_ALL_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    local sessionIds = redis.call(
                        'SMEMBERS',
                        KEYS[1]
                    )

                    for _, sessionId in ipairs(sessionIds) do
                        redis.call(
                            'DEL',
                            'session:' .. sessionId
                        )
                    end

                    redis.call(
                        'DEL',
                        KEYS[1]
                    )

                    local requestedInvalidatedAt =
                        tonumber(ARGV[1])

                    local requestedTtlMillis =
                        tonumber(ARGV[2])

                    local currentInvalidatedAt =
                        redis.call(
                            'GET',
                            KEYS[2]
                        )

                    if currentInvalidatedAt then
                        local currentInvalidatedAtMillis =
                            tonumber(currentInvalidatedAt)

                        if currentInvalidatedAtMillis
                            and currentInvalidatedAtMillis
                                > requestedInvalidatedAt
                        then
                            requestedInvalidatedAt =
                                currentInvalidatedAtMillis
                        end
                    end

                    local currentTtlMillis =
                        redis.call(
                            'PTTL',
                            KEYS[2]
                        )

                    if currentTtlMillis > requestedTtlMillis then
                        requestedTtlMillis =
                            currentTtlMillis
                    end

                    redis.call(
                        'SET',
                        KEYS[2],
                        tostring(requestedInvalidatedAt),
                        'PX',
                        requestedTtlMillis
                    )

                    return 1
                    """,
                    Long.class
            );

    private final StringRedisTemplate redisTemplate;
    private final long invalidatedAtTtlMillis;

    public RedisAuthSessionLogoutAllStore(
            StringRedisTemplate redisTemplate,
            JwtProperties jwtProperties
    ) {
        this.redisTemplate =
                Objects.requireNonNull(
                        redisTemplate,
                        "RedisTemplate은 필수입니다."
                );

        JwtProperties requiredJwtProperties =
                Objects.requireNonNull(
                        jwtProperties,
                        "JWT 설정은 필수입니다."
                );

        Duration accessTokenTtl =
                Objects.requireNonNull(
                        requiredJwtProperties.accessTokenTtl(),
                        "Access Token TTL은 필수입니다."
                );

        Duration refreshTokenTtl =
                Objects.requireNonNull(
                        requiredJwtProperties.refreshTokenTtl(),
                        "Refresh Token TTL은 필수입니다."
                );

        if (accessTokenTtl.isZero()
                || accessTokenTtl.isNegative()) {
            throw new IllegalArgumentException(
                    "Access Token TTL은 양수여야 합니다."
            );
        }

        if (refreshTokenTtl.isZero()
                || refreshTokenTtl.isNegative()) {
            throw new IllegalArgumentException(
                    "Refresh Token TTL은 양수여야 합니다."
            );
        }

        this.invalidatedAtTtlMillis =
                Math.max(
                        accessTokenTtl.toMillis(),
                        refreshTokenTtl.toMillis()
                );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void logoutAll(
            UUID userId,
            Instant invalidatedAt
    ) {
        Objects.requireNonNull(
                userId,
                "사용자 식별자는 필수입니다."
        );

        Objects.requireNonNull(
                invalidatedAt,
                "토큰 무효화 기준 시각은 필수입니다."
        );

        Long result =
                redisTemplate.execute(
                        LOGOUT_ALL_SCRIPT,
                        List.of(
                                createUserSessionIndexKey(
                                        userId
                                ),
                                createUserInvalidatedAtKey(
                                        userId
                                )
                        ),
                        Long.toString(
                                invalidatedAt.toEpochMilli()
                        ),
                        Long.toString(
                                invalidatedAtTtlMillis
                        )
                );

        if (result == null) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    "전체 인증 세션 로그아웃 결과를 확인할 수 없습니다."
            );
        }

        if (result != LOGOUT_ALL_SUCCESS) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    "알 수 없는 전체 인증 세션 로그아웃 결과입니다: "
                            + result
            );
        }
    }

    /**
     * 사용자별 인증 세션 인덱스 Redis Key를 생성합니다.
     */
    private String createUserSessionIndexKey(
            UUID userId
    ) {
        return USER_KEY_PREFIX
                + userId
                + USER_SESSION_INDEX_KEY_SUFFIX;
    }

    /**
     * 사용자 단위 토큰 무효화 Redis Key를 생성합니다.
     */
    private String createUserInvalidatedAtKey(
            UUID userId
    ) {
        return USER_KEY_PREFIX
                + userId
                + USER_INVALIDATED_AT_KEY_SUFFIX;
    }
}
