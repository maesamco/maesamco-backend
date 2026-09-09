package com.maesamco.user.infrastructure.security.session;

import com.maesamco.user.application.port.AuthSessionLogoutResult;
import com.maesamco.user.application.port.AuthSessionLogoutStore;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
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
 * RedisAuthSessionLogoutStore의 세션 삭제와
 * 세션 블랙리스트 등록 원자성을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class RedisAuthSessionLogoutStoreTest {

    private static final Instant NOW =
            Instant.parse(
                    "2026-09-07T00:00:00Z"
            );

    private static final Instant ACCESS_TOKEN_EXPIRES_AT =
            NOW.plusSeconds(900);

    private static final UUID USER_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    private static final UUID SESSION_ID =
            UUID.fromString(
                    "22222222-2222-2222-2222-222222222222"
            );

    private static final String SESSION_KEY =
            "session:" + SESSION_ID;

    private static final String SESSION_BLACKLIST_KEY =
            SESSION_KEY + ":blacklisted";

    private static final String ACCESS_TOKEN_TTL_MILLIS =
            "900000";

    @Mock
    private StringRedisTemplate redisTemplate;

    private AuthSessionLogoutStore authSessionLogoutStore;

    @BeforeEach
    void setUp() {
        Clock clock =
                Clock.fixed(
                        NOW,
                        ZoneOffset.UTC
                );

        authSessionLogoutStore =
                new RedisAuthSessionLogoutStore(
                        redisTemplate,
                        clock
                );
    }

    @Test
    @DisplayName(
            "현재 인증 세션을 삭제하고 Access Token 만료시간까지 "
                    + "세션 블랙리스트를 등록한다"
    )
    void logout() {
        // given
        prepareLogoutResult(1L);

        // when
        AuthSessionLogoutResult result =
                authSessionLogoutStore.logout(
                        USER_ID,
                        SESSION_ID,
                        ACCESS_TOKEN_EXPIRES_AT
                );

        // then
        assertThat(result)
                .isEqualTo(
                        AuthSessionLogoutResult.LOGGED_OUT
                );

        verify(redisTemplate)
                .execute(
                        any(),
                        eq(
                                List.of(
                                        SESSION_KEY,
                                        SESSION_BLACKLIST_KEY
                                )
                        ),
                        eq(USER_ID.toString()),
                        eq(ACCESS_TOKEN_TTL_MILLIS)
                );
    }

    @Test
    @DisplayName(
            "인증 세션이 이미 없어도 블랙리스트를 등록하고 "
                    + "SESSION_NOT_FOUND를 반환한다"
    )
    void logout_sessionNotFound() {
        // given
        prepareLogoutResult(0L);

        // when
        AuthSessionLogoutResult result =
                authSessionLogoutStore.logout(
                        USER_ID,
                        SESSION_ID,
                        ACCESS_TOKEN_EXPIRES_AT
                );

        // then
        assertThat(result)
                .isEqualTo(
                        AuthSessionLogoutResult
                                .SESSION_NOT_FOUND
                );
    }

    @Test
    @DisplayName(
            "인증된 사용자와 세션 소유자가 다르면 "
                    + "SESSION_OWNER_MISMATCH를 반환한다"
    )
    void logout_sessionOwnerMismatch() {
        // given
        prepareLogoutResult(2L);

        // when
        AuthSessionLogoutResult result =
                authSessionLogoutStore.logout(
                        USER_ID,
                        SESSION_ID,
                        ACCESS_TOKEN_EXPIRES_AT
                );

        // then
        assertThat(result)
                .isEqualTo(
                        AuthSessionLogoutResult
                                .SESSION_OWNER_MISMATCH
                );
    }

    @Test
    @DisplayName(
            "Redis에서 로그아웃 결과를 반환하지 않으면 실패한다"
    )
    void logout_nullResult() {
        // given
        prepareLogoutResult(null);

        // when & then
        assertThatThrownBy(
                () -> authSessionLogoutStore.logout(
                        USER_ID,
                        SESSION_ID,
                        ACCESS_TOKEN_EXPIRES_AT
                )
        )
                .isInstanceOf(
                        IllegalStateException.class
                )
                .hasMessage(
                        "인증 세션 로그아웃 결과를 확인할 수 없습니다."
                );
    }

    @Test
    @DisplayName(
            "Redis가 알 수 없는 로그아웃 결과를 반환하면 실패한다"
    )
    void logout_unknownResult() {
        // given
        prepareLogoutResult(99L);

        // when & then
        assertThatThrownBy(
                () -> authSessionLogoutStore.logout(
                        USER_ID,
                        SESSION_ID,
                        ACCESS_TOKEN_EXPIRES_AT
                )
        )
                .isInstanceOf(
                        IllegalStateException.class
                )
                .hasMessage(
                        "알 수 없는 인증 세션 로그아웃 결과입니다: 99"
                );
    }

    @Test
    @DisplayName(
            "이미 만료된 Access Token이면 "
                    + "AUTH_EXPIRED_TOKEN으로 실패하고 Redis를 호출하지 않는다"
    )
    void logout_expiredAccessToken() {
        // when & then
        assertThatThrownBy(
                () -> authSessionLogoutStore.logout(
                        USER_ID,
                        SESSION_ID,
                        NOW
                )
        )
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> {
                            assertThat(
                                    exception.getErrorCode()
                            ).isEqualTo(
                                    ErrorCode.AUTH_EXPIRED_TOKEN
                            );

                            assertThat(
                                    exception.getMessage()
                            ).isEqualTo(
                                    ErrorCode.AUTH_EXPIRED_TOKEN
                                            .getMessage()
                            );
                        }
                );

        verifyNoInteractions(redisTemplate);
    }

    /**
     * Redis Lua Script 실행 결과를 준비합니다.
     */
    private void prepareLogoutResult(
            Long result
    ) {
        when(
                redisTemplate.execute(
                        any(),
                        eq(
                                List.of(
                                        SESSION_KEY,
                                        SESSION_BLACKLIST_KEY
                                )
                        ),
                        eq(USER_ID.toString()),
                        eq(ACCESS_TOKEN_TTL_MILLIS)
                )
        ).thenReturn(result);
    }
}
