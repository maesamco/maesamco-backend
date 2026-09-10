package com.maesamco.user.infrastructure.security.session;

import com.maesamco.user.application.port.AuthSession;
import com.maesamco.user.application.port.AuthSessionRotationResult;
import com.maesamco.user.application.port.AuthSessionStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Redis를 이용해 사용자 인증 세션을 저장하고 조회합니다.
 *
 * <p>Redis Key는 {@code session:{sessionId}} 형식을 사용하며,
 * 세션 만료 시각까지 남은 시간을 TTL로 설정합니다.</p>
 *
 * <p>Refresh Token Rotation은 Lua Script를 사용해 현재 Refresh Token hash 확인,
 * 새로운 hash로의 교체, 동시 요청 완화 및 재사용 감지를 하나의 원자 연산으로
 * 수행합니다.</p>
 */
@Repository
public class RedisAuthSessionStore implements AuthSessionStore {

    private static final String KEY_PREFIX = "session:";

    private static final long ROTATION_SESSION_NOT_FOUND = 0L;
    private static final long ROTATION_SUCCESS = 1L;

    private static final long
            ROTATION_PREVIOUS_TOKEN_WITHIN_GRACE = 2L;

    private static final long ROTATION_TOKEN_REUSED = 3L;

    /**
     * Refresh Token hash 비교와 교체를 원자적으로 수행하는 Lua Script입니다.
     *
     * <p>현재 hash와 일치하면 정상적으로 Rotation합니다.
     * 현재 hash와 다르더라도 직전 hash가 grace window 안에서 다시 요청된 경우에는
     * 중복 요청으로 판단하여 세션을 유지합니다. Grace window를 벗어난 재사용이나
     * 직전 토큰과 무관한 토큰이 사용되면 세션을 삭제합니다.</p>
     *
     * <p>반환값:</p>
     * <ul>
     *     <li>0: 세션이 존재하지 않거나 유효한 TTL이 없음</li>
     *     <li>1: 정상 Rotation</li>
     *     <li>2: grace window 안의 직전 토큰 재요청</li>
     *     <li>3: Refresh Token 재사용 감지 및 세션 폐기</li>
     * </ul>
     */
    private static final DefaultRedisScript<Long> ROTATE_REFRESH_TOKEN_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    local value = redis.call('GET', KEYS[1])

                    if not value then
                        return 0
                    end

                    local session = cjson.decode(value)
                    local ttl = redis.call('PTTL', KEYS[1])

                    if ttl <= 0 then
                        redis.call('DEL', KEYS[1])
                        return 0
                    end

                    local expectedHash = ARGV[1]
                    local newHash = ARGV[2]
                    local nowEpochMillis = tonumber(ARGV[3])
                    local gracePeriodMillis = tonumber(ARGV[4])

                    if session.refreshTokenHash ~= expectedHash then
                        local previousHash =
                            session.previousRefreshTokenHash

                        local rotatedAt =
                            session.refreshTokenRotatedAtEpochMillis

                        local hasRotationHistory =
                            previousHash ~= nil
                            and previousHash ~= cjson.null
                            and rotatedAt ~= nil
                            and rotatedAt ~= cjson.null

                        local withinGracePeriod =
                            hasRotationHistory
                            and previousHash == expectedHash
                            and nowEpochMillis >= tonumber(rotatedAt)
                            and nowEpochMillis - tonumber(rotatedAt)
                                <= gracePeriodMillis

                        if withinGracePeriod then
                            return 2
                        end

                        redis.call('DEL', KEYS[1])
                        return 3
                    end

                    session.previousRefreshTokenHash =
                        session.refreshTokenHash

                    session.refreshTokenHash = newHash

                    session.refreshTokenRotatedAtEpochMillis =
                        nowEpochMillis

                    redis.call(
                        'PSETEX',
                        KEYS[1],
                        ttl,
                        cjson.encode(session)
                    )

                    return 1
                    """,
                    Long.class
            );

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final AuthSessionProperties authSessionProperties;

    /**
     * Redis 인증 세션 저장소를 생성합니다.
     *
     * @param redisTemplate 문자열 기반 Redis 접근 객체
     * @param jsonMapper 인증 세션 JSON 직렬화 객체
     * @param clock 현재 시각을 제공하는 시계
     * @param authSessionProperties 인증 세션 설정
     */
    public RedisAuthSessionStore(
            StringRedisTemplate redisTemplate,
            JsonMapper jsonMapper,
            Clock clock,
            AuthSessionProperties authSessionProperties
    ) {
        this.redisTemplate = redisTemplate;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        this.authSessionProperties = authSessionProperties;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void save(AuthSession session) {
        Objects.requireNonNull(
                session,
                "인증 세션은 필수입니다."
        );

        Duration ttl = Duration.between(
                clock.instant(),
                session.expiresAt()
        );

        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException(
                    "만료된 인증 세션은 저장할 수 없습니다."
            );
        }

        redisTemplate.opsForValue().set(
                createKey(session.sessionId()),
                serialize(session),
                ttl
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<AuthSession> findBySessionId(
            UUID sessionId
    ) {
        Objects.requireNonNull(
                sessionId,
                "세션 식별자는 필수입니다."
        );

        String value = redisTemplate.opsForValue().get(
                createKey(sessionId)
        );

        if (value == null) {
            return Optional.empty();
        }

        return Optional.of(deserialize(value));
    }

    /**
     * 현재 Refresh Token hash가 Redis 세션에 저장된 hash와 일치할 때만
     * 새로운 hash로 원자적으로 교체합니다.
     *
     * <p>hash가 일치하지 않더라도 직전 Refresh Token이 grace window 안에
     * 다시 요청된 경우에는 세션을 유지합니다. Grace window를 벗어났거나
     * 직전 토큰이 아닌 경우에는 같은 Lua Script 안에서 세션을 삭제합니다.</p>
     *
     * <p>정상 Rotation 시 기존 Redis PTTL을 그대로 사용하므로 Refresh 요청으로
     * 인증 세션의 절대 만료시간이 연장되지 않습니다.</p>
     */
    @Override
    public AuthSessionRotationResult rotateRefreshToken(
            UUID sessionId,
            String expectedRefreshTokenHash,
            String newRefreshTokenHash
    ) {
        Objects.requireNonNull(
                sessionId,
                "세션 식별자는 필수입니다."
        );

        requireHash(
                expectedRefreshTokenHash,
                "기존 Refresh Token hash는 필수입니다."
        );

        requireHash(
                newRefreshTokenHash,
                "새 Refresh Token hash는 필수입니다."
        );

        Long result = redisTemplate.execute(
                ROTATE_REFRESH_TOKEN_SCRIPT,
                List.of(createKey(sessionId)),
                expectedRefreshTokenHash,
                newRefreshTokenHash,
                Long.toString(clock.millis()),
                Long.toString(
                        authSessionProperties
                                .refreshTokenRotationGracePeriod()
                                .toMillis()
                )
        );

        if (result == null) {
            throw new IllegalStateException(
                    "Refresh Token Rotation 결과를 확인할 수 없습니다."
            );
        }

        if (result == ROTATION_SUCCESS) {
            return AuthSessionRotationResult.ROTATED;
        }

        if (result == ROTATION_SESSION_NOT_FOUND) {
            return AuthSessionRotationResult.SESSION_NOT_FOUND;
        }

        if (result == ROTATION_PREVIOUS_TOKEN_WITHIN_GRACE) {
            return AuthSessionRotationResult
                    .PREVIOUS_TOKEN_WITHIN_GRACE;
        }

        if (result == ROTATION_TOKEN_REUSED) {
            return AuthSessionRotationResult.TOKEN_REUSED;
        }

        throw new IllegalStateException(
                "알 수 없는 Refresh Token Rotation 결과입니다: "
                        + result
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteBySessionId(UUID sessionId) {
        Objects.requireNonNull(
                sessionId,
                "세션 식별자는 필수입니다."
        );

        redisTemplate.delete(createKey(sessionId));
    }

    /**
     * Refresh Token hash 필수값을 검증합니다.
     */
    private void requireHash(
            String hash,
            String message
    ) {
        if (hash == null || hash.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 인증 세션을 JSON 문자열로 변환합니다.
     */
    private String serialize(AuthSession session) {
        try {
            return jsonMapper.writeValueAsString(session);
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "인증 세션을 직렬화할 수 없습니다.",
                    exception
            );
        }
    }

    /**
     * JSON 문자열을 인증 세션으로 변환합니다.
     */
    private AuthSession deserialize(String value) {
        try {
            return jsonMapper.readValue(
                    value,
                    AuthSession.class
            );
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "인증 세션을 역직렬화할 수 없습니다.",
                    exception
            );
        }
    }

    /**
     * 세션 식별자로 Redis Key를 생성합니다.
     */
    private String createKey(UUID sessionId) {
        return KEY_PREFIX + sessionId;
    }
}
