package com.maesamco.user.infrastructure.security.session;

import com.maesamco.user.application.port.AuthSession;
import com.maesamco.user.application.port.AuthSessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * RedisAuthSessionStore의 Redis 저장 규칙을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class RedisAuthSessionStoreTest {

    private static final Instant NOW =
            Instant.parse("2026-09-02T00:00:00Z");

    private static final Duration SESSION_TTL =
            Duration.ofDays(14);

    private static final UUID SESSION_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    private static final UUID FAMILY_ID =
            UUID.fromString(
                    "22222222-2222-2222-2222-222222222222"
            );

    private static final UUID USER_ID =
            UUID.fromString(
                    "33333333-3333-3333-3333-333333333333"
            );

    private static final String SESSION_KEY =
            "session:" + SESSION_ID;

    private static final String SESSION_JSON =
            "{\"sessionId\":\"" + SESSION_ID + "\"}";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private JsonMapper jsonMapper;

    private AuthSessionStore authSessionStore;
    private AuthSession authSession;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                NOW,
                ZoneOffset.UTC
        );

        authSessionStore = new RedisAuthSessionStore(
                redisTemplate,
                jsonMapper,
                clock
        );

        authSession = new AuthSession(
                SESSION_ID,
                FAMILY_ID,
                USER_ID,
                "refresh-token-hash",
                NOW,
                NOW.plus(SESSION_TTL)
        );
    }

    @Test
    @DisplayName("인증 세션을 JSON과 만료시간을 사용해 Redis에 저장한다")
    void save_storesSessionWithTtl() throws JacksonException {
        // given
        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

        when(jsonMapper.writeValueAsString(authSession))
                .thenReturn(SESSION_JSON);

        // when
        authSessionStore.save(authSession);

        // then
        verify(valueOperations).set(
                SESSION_KEY,
                SESSION_JSON,
                SESSION_TTL
        );
    }

    @Test
    @DisplayName("세션 식별자로 Redis 인증 세션을 조회한다")
    void findBySessionId_returnsStoredSession()
            throws JacksonException {
        // given
        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

        when(valueOperations.get(SESSION_KEY))
                .thenReturn(SESSION_JSON);

        when(jsonMapper.readValue(
                SESSION_JSON,
                AuthSession.class
        )).thenReturn(authSession);

        // when
        var result =
                authSessionStore.findBySessionId(SESSION_ID);

        // then
        assertThat(result)
                .contains(authSession);
    }

    @Test
    @DisplayName("Redis에 인증 세션이 없으면 빈 결과를 반환한다")
    void findBySessionId_returnsEmptyWhenMissing() {
        // given
        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

        // when
        var result =
                authSessionStore.findBySessionId(SESSION_ID);

        // then
        assertThat(result)
                .isEmpty();

        verify(valueOperations).get(SESSION_KEY);
        verifyNoInteractions(jsonMapper);
    }

    @Test
    @DisplayName("세션 식별자에 해당하는 Redis 인증 세션을 삭제한다")
    void deleteBySessionId_deletesSession() {
        // when
        authSessionStore.deleteBySessionId(SESSION_ID);

        // then
        verify(redisTemplate).delete(SESSION_KEY);
    }

    @Test
    @DisplayName("이미 만료된 인증 세션은 Redis에 저장하지 않는다")
    void save_rejectsExpiredSession() {
        // given
        AuthSession expiredSession = new AuthSession(
                SESSION_ID,
                FAMILY_ID,
                USER_ID,
                "refresh-token-hash",
                NOW.minus(Duration.ofDays(2)),
                NOW.minus(Duration.ofDays(1))
        );

        // then
        assertThatThrownBy(() ->
                authSessionStore.save(expiredSession)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "만료된 인증 세션은 저장할 수 없습니다."
                );

        verifyNoInteractions(
                redisTemplate,
                jsonMapper
        );
    }
}
