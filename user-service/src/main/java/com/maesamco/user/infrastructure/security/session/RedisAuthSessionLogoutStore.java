package com.maesamco.user.infrastructure.security.session;

import com.maesamco.user.application.port.AuthSessionLogoutResult;
import com.maesamco.user.application.port.AuthSessionLogoutStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Redis Lua Script를 이용해 현재 인증 세션을 종료합니다.
 *
 * <p>{@code session:{sessionId}} 인증 세션 삭제와
 * {@code session:{sessionId}:blacklisted} 등록을 하나의
 * 원자 연산으로 실행합니다.</p>
 *
 * <p>블랙리스트는 Access Token의 남은 유효시간 동안 유지되며,
 * 기존 블랙리스트의 TTL이 더 길다면 TTL을 단축하지 않습니다.</p>
 */
@Repository
public class RedisAuthSessionLogoutStore
        implements AuthSessionLogoutStore {

    private static final String SESSION_KEY_PREFIX =
            "session:";

    private static final String BLACKLIST_KEY_SUFFIX =
            ":blacklisted";

    private static final long SESSION_NOT_FOUND = 0L;
    private static final long LOGGED_OUT = 1L;
    private static final long SESSION_OWNER_MISMATCH = 2L;

    /**
     * 인증 세션 소유권 검증, 블랙리스트 등록 및
     * 인증 세션 삭제를 원자적으로 수행합니다.
     *
     * <p>반환값:</p>
     * <ul>
     *     <li>0: 세션은 이미 없으며 블랙리스트 등록 완료</li>
     *     <li>1: 세션 삭제 및 블랙리스트 등록 완료</li>
     *     <li>2: 세션 소유자 불일치</li>
     * </ul>
     */
    private static final DefaultRedisScript<Long> LOGOUT_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    local sessionValue = redis.call(
                        'GET',
                        KEYS[1]
                    )

                    if sessionValue then
                        local session = cjson.decode(sessionValue)

                        if session.userId ~= ARGV[1] then
                            return 2
                        end
                    end

                    local requestedTtlMillis = tonumber(ARGV[2])
                    local currentTtlMillis = redis.call(
                        'PTTL',
                        KEYS[2]
                    )

                    if currentTtlMillis == -2
                        or (
                            currentTtlMillis >= 0
                            and currentTtlMillis < requestedTtlMillis
                        )
                    then
                        redis.call(
                            'SET',
                            KEYS[2],
                            '1',
                            'PX',
                            requestedTtlMillis
                        )
                    end

                    if sessionValue then
                        redis.call(
                            'DEL',
                            KEYS[1]
                        )

                        return 1
                    end

                    return 0
                    """,
                    Long.class
            );

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;

    public RedisAuthSessionLogoutStore(
            StringRedisTemplate redisTemplate,
            Clock clock
    ) {
        this.redisTemplate =
                Objects.requireNonNull(
                        redisTemplate,
                        "RedisTemplate은 필수입니다."
                );

        this.clock =
                Objects.requireNonNull(
                        clock,
                        "Clock은 필수입니다."
                );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AuthSessionLogoutResult logout(
            UUID userId,
            UUID sessionId,
            Instant accessTokenExpiresAt
    ) {
        Objects.requireNonNull(
                userId,
                "사용자 식별자는 필수입니다."
        );

        Objects.requireNonNull(
                sessionId,
                "세션 식별자는 필수입니다."
        );

        Objects.requireNonNull(
                accessTokenExpiresAt,
                "Access Token 만료 시각은 필수입니다."
        );

        long ttlMillis =
                calculateTtlMillis(
                        accessTokenExpiresAt
                );

        Long result =
                redisTemplate.execute(
                        LOGOUT_SCRIPT,
                        List.of(
                                createSessionKey(sessionId),
                                createBlacklistKey(sessionId)
                        ),
                        userId.toString(),
                        Long.toString(ttlMillis)
                );

        return convertResult(result);
    }

    /**
     * Access Token 만료 시각을 기준으로
     * 블랙리스트 TTL을 밀리초 단위로 계산합니다.
     */
    private long calculateTtlMillis(
            Instant accessTokenExpiresAt
    ) {
        long ttlMillis =
                Duration.between(
                        clock.instant(),
                        accessTokenExpiresAt
                ).toMillis();

        if (ttlMillis <= 0) {
            throw new IllegalArgumentException(
                    "만료된 Access Token은 로그아웃할 수 없습니다."
            );
        }

        return ttlMillis;
    }

    /**
     * Redis Lua Script의 숫자 결과를
     * 애플리케이션 결과 타입으로 변환합니다.
     */
    private AuthSessionLogoutResult convertResult(
            Long result
    ) {
        if (result == null) {
            throw new IllegalStateException(
                    "인증 세션 로그아웃 결과를 확인할 수 없습니다."
            );
        }

        if (result == LOGGED_OUT) {
            return AuthSessionLogoutResult.LOGGED_OUT;
        }

        if (result == SESSION_NOT_FOUND) {
            return AuthSessionLogoutResult
                    .SESSION_NOT_FOUND;
        }

        if (result == SESSION_OWNER_MISMATCH) {
            return AuthSessionLogoutResult
                    .SESSION_OWNER_MISMATCH;
        }

        throw new IllegalStateException(
                "알 수 없는 인증 세션 로그아웃 결과입니다: "
                        + result
        );
    }

    /**
     * 인증 세션 Redis Key를 생성합니다.
     */
    private String createSessionKey(
            UUID sessionId
    ) {
        return SESSION_KEY_PREFIX + sessionId;
    }

    /**
     * 세션 블랙리스트 Redis Key를 생성합니다.
     */
    private String createBlacklistKey(
            UUID sessionId
    ) {
        return createSessionKey(sessionId)
                + BLACKLIST_KEY_SUFFIX;
    }
}
