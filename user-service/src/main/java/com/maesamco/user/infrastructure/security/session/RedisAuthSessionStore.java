package com.maesamco.user.infrastructure.security.session;

import com.maesamco.user.application.port.AuthSession;
import com.maesamco.user.application.port.AuthSessionStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Redis를 이용해 사용자 인증 세션을 저장하고 조회합니다.
 *
 * <p>Redis Key는 {@code session:{sessionId}} 형식을 사용하며,
 * 세션 만료 시각까지 남은 시간을 TTL로 설정합니다.</p>
 */
@Repository
public class RedisAuthSessionStore implements AuthSessionStore {

    private static final String KEY_PREFIX = "session:";

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    /**
     * Redis 인증 세션 저장소를 생성합니다.
     *
     * @param redisTemplate 문자열 기반 Redis 접근 객체
     * @param jsonMapper 인증 세션 JSON 직렬화 객체
     * @param clock 현재 시각을 제공하는 시계
     */
    public RedisAuthSessionStore(
            StringRedisTemplate redisTemplate,
            JsonMapper jsonMapper,
            Clock clock
    ) {
        this.redisTemplate = redisTemplate;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
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
