package com.maesamco.user.infrastructure.security.session;

import com.maesamco.user.application.port.AuthSession;
import com.maesamco.user.application.port.AuthSessionRotationResult;
import com.maesamco.user.application.port.AuthSessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * RedisAuthSessionStore의 Redis 저장 및 Refresh Token Rotation 규칙을 검증합니다.
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

    private static final String EXPECTED_REFRESH_TOKEN_HASH =
            "expected-refresh-token-hash";

    private static final String NEW_REFRESH_TOKEN_HASH =
            "new-refresh-token-hash";

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
    @DisplayName("Refresh Token hash Rotation이 성공하면 ROTATED를 반환한다")
    void rotateRefreshToken_returnsRotated() {
        // given
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of(SESSION_KEY)),
                eq(EXPECTED_REFRESH_TOKEN_HASH),
                eq(NEW_REFRESH_TOKEN_HASH)
        )).thenReturn(1L);

        // when
        AuthSessionRotationResult result =
                authSessionStore.rotateRefreshToken(
                        SESSION_ID,
                        EXPECTED_REFRESH_TOKEN_HASH,
                        NEW_REFRESH_TOKEN_HASH
                );

        // then
        assertThat(result)
                .isEqualTo(
                        AuthSessionRotationResult.ROTATED
                );
    }

    @Test
    @DisplayName("Rotation 대상 세션이 없으면 SESSION_NOT_FOUND를 반환한다")
    void rotateRefreshToken_returnsSessionNotFound() {
        // given
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of(SESSION_KEY)),
                eq(EXPECTED_REFRESH_TOKEN_HASH),
                eq(NEW_REFRESH_TOKEN_HASH)
        )).thenReturn(0L);

        // when
        AuthSessionRotationResult result =
                authSessionStore.rotateRefreshToken(
                        SESSION_ID,
                        EXPECTED_REFRESH_TOKEN_HASH,
                        NEW_REFRESH_TOKEN_HASH
                );

        // then
        assertThat(result)
                .isEqualTo(
                        AuthSessionRotationResult.SESSION_NOT_FOUND
                );
    }

    @Test
    @DisplayName("이전 Refresh Token 재사용이 감지되면 TOKEN_REUSED를 반환한다")
    void rotateRefreshToken_returnsTokenReused() {
        // given
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of(SESSION_KEY)),
                eq(EXPECTED_REFRESH_TOKEN_HASH),
                eq(NEW_REFRESH_TOKEN_HASH)
        )).thenReturn(2L);

        // when
        AuthSessionRotationResult result =
                authSessionStore.rotateRefreshToken(
                        SESSION_ID,
                        EXPECTED_REFRESH_TOKEN_HASH,
                        NEW_REFRESH_TOKEN_HASH
                );

        // then
        assertThat(result)
                .isEqualTo(
                        AuthSessionRotationResult.TOKEN_REUSED
                );
    }

    @Test
    @DisplayName("Redis에서 Rotation 결과를 반환하지 않으면 실패한다")
    void rotateRefreshToken_rejectsNullResult() {
        // given
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of(SESSION_KEY)),
                eq(EXPECTED_REFRESH_TOKEN_HASH),
                eq(NEW_REFRESH_TOKEN_HASH)
        )).thenReturn(null);

        // when & then
        assertThatThrownBy(() ->
                authSessionStore.rotateRefreshToken(
                        SESSION_ID,
                        EXPECTED_REFRESH_TOKEN_HASH,
                        NEW_REFRESH_TOKEN_HASH
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "Refresh Token Rotation 결과를 확인할 수 없습니다."
                );
    }

    @Test
    @DisplayName("기존 Refresh Token hash가 비어 있으면 Rotation하지 않는다")
    void rotateRefreshToken_rejectsBlankExpectedHash() {
        // when & then
        assertThatThrownBy(() ->
                authSessionStore.rotateRefreshToken(
                        SESSION_ID,
                        " ",
                        NEW_REFRESH_TOKEN_HASH
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "기존 Refresh Token hash는 필수입니다."
                );

        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("새 Refresh Token hash가 비어 있으면 Rotation하지 않는다")
    void rotateRefreshToken_rejectsBlankNewHash() {
        // when & then
        assertThatThrownBy(() ->
                authSessionStore.rotateRefreshToken(
                        SESSION_ID,
                        EXPECTED_REFRESH_TOKEN_HASH,
                        " "
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "새 Refresh Token hash는 필수입니다."
                );

        verifyNoInteractions(redisTemplate);
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
