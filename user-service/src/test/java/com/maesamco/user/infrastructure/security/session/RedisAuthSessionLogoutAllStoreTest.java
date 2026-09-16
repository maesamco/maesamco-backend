package com.maesamco.user.infrastructure.security.session;

import com.maesamco.user.application.port.AuthSessionLogoutAllStore;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import com.maesamco.user.global.security.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * RedisAuthSessionLogoutAllStore의
 * 사용자 전체 인증 세션 종료 정책을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class RedisAuthSessionLogoutAllStoreTest {

    private static final UUID USER_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    private static final Instant INVALIDATED_AT =
            Instant.parse(
                    "2026-09-14T11:00:00Z"
            );

    private static final Duration ACCESS_TOKEN_TTL =
            Duration.ofMinutes(15);

    private static final Duration REFRESH_TOKEN_TTL =
            Duration.ofDays(14);

    private static final String USER_SESSION_INDEX_KEY =
            "user:" + USER_ID + ":sessions";

    private static final String USER_INVALIDATED_AT_KEY =
            "user:" + USER_ID + ":invalidatedAt";

    private static final String INVALIDATED_AT_EPOCH_MILLIS =
            Long.toString(
                    INVALIDATED_AT.toEpochMilli()
            );

    private static final String INVALIDATED_AT_TTL_MILLIS =
            Long.toString(
                    Math.max(
                            ACCESS_TOKEN_TTL.toMillis(),
                            REFRESH_TOKEN_TTL.toMillis()
                    )
            );

    @Mock
    private StringRedisTemplate redisTemplate;

    private AuthSessionLogoutAllStore authSessionLogoutAllStore;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties =
                new JwtProperties(
                        null,
                        null,
                        null,
                        null,
                        ACCESS_TOKEN_TTL,
                        REFRESH_TOKEN_TTL
                );

        authSessionLogoutAllStore =
                new RedisAuthSessionLogoutAllStore(
                        redisTemplate,
                        jwtProperties
                );
    }

    @Test
    @DisplayName(
            "사용자의 모든 인증 세션 삭제와 "
                    + "사용자 단위 토큰 무효화를 원자적으로 요청한다"
    )
    void logoutAll_executesAtomicRedisOperation() {
        // given
        when(
                redisTemplate.execute(
                        any(),
                        eq(
                                List.of(
                                        USER_SESSION_INDEX_KEY,
                                        USER_INVALIDATED_AT_KEY
                                )
                        ),
                        eq(INVALIDATED_AT_EPOCH_MILLIS),
                        eq(INVALIDATED_AT_TTL_MILLIS)
                )
        ).thenReturn(
                1L
        );

        // when & then
        assertThatCode(
                () -> authSessionLogoutAllStore.logoutAll(
                        USER_ID,
                        INVALIDATED_AT
                )
        ).doesNotThrowAnyException();

        verify(redisTemplate)
                .execute(
                        any(),
                        eq(
                                List.of(
                                        USER_SESSION_INDEX_KEY,
                                        USER_INVALIDATED_AT_KEY
                                )
                        ),
                        eq(INVALIDATED_AT_EPOCH_MILLIS),
                        eq(INVALIDATED_AT_TTL_MILLIS)
                );
    }

    @Test
    @DisplayName(
            "전체 로그아웃 Redis 처리 결과를 확인할 수 없으면 실패한다"
    )
    void logoutAll_nullResult() {
        // given
        when(
                redisTemplate.execute(
                        any(),
                        eq(
                                List.of(
                                        USER_SESSION_INDEX_KEY,
                                        USER_INVALIDATED_AT_KEY
                                )
                        ),
                        eq(INVALIDATED_AT_EPOCH_MILLIS),
                        eq(INVALIDATED_AT_TTL_MILLIS)
                )
        ).thenReturn(null);

        // when & then
        assertThatThrownBy(
                () -> authSessionLogoutAllStore.logoutAll(
                        USER_ID,
                        INVALIDATED_AT
                )
        )
                .isInstanceOf(
                        BusinessException.class
                )
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                ErrorCode.INTERNAL_SERVER_ERROR
                        )
                )
                .hasMessage(
                        "전체 인증 세션 로그아웃 결과를 확인할 수 없습니다."
                );
    }

    @Test
    @DisplayName(
            "전체 로그아웃 Redis 처리 결과가 예상하지 못한 값이면 서버 오류로 처리한다"
    )
    void logoutAll_unknownResult() {
        // given
        when(
                redisTemplate.execute(
                        any(),
                        eq(
                                List.of(
                                        USER_SESSION_INDEX_KEY,
                                        USER_INVALIDATED_AT_KEY
                                )
                        ),
                        eq(INVALIDATED_AT_EPOCH_MILLIS),
                        eq(INVALIDATED_AT_TTL_MILLIS)
                )
        ).thenReturn(
                99L
        );

        // when & then
        assertThatThrownBy(
                () -> authSessionLogoutAllStore.logoutAll(
                        USER_ID,
                        INVALIDATED_AT
                )
        )
                .isInstanceOf(
                        BusinessException.class
                )
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                ErrorCode.INTERNAL_SERVER_ERROR
                        )
                )
                .hasMessage(
                        "알 수 없는 전체 인증 세션 로그아웃 결과입니다: 99"
                );
    }

    @Test
    @DisplayName(
            "전체 로그아웃 사용자 식별자가 없으면 Redis를 호출하지 않는다"
    )
    void logoutAll_nullUserId() {
        // when & then
        assertThatThrownBy(
                () -> authSessionLogoutAllStore.logoutAll(
                        null,
                        INVALIDATED_AT
                )
        )
                .isInstanceOf(
                        NullPointerException.class
                )
                .hasMessage(
                        "사용자 식별자는 필수입니다."
                );

        verifyNoInteractions(
                redisTemplate
        );
    }

    @Test
    @DisplayName(
            "토큰 무효화 기준 시각이 없으면 Redis를 호출하지 않는다"
    )
    void logoutAll_nullInvalidatedAt() {
        // when & then
        assertThatThrownBy(
                () -> authSessionLogoutAllStore.logoutAll(
                        USER_ID,
                        null
                )
        )
                .isInstanceOf(
                        NullPointerException.class
                )
                .hasMessage(
                        "토큰 무효화 기준 시각은 필수입니다."
                );

        verifyNoInteractions(
                redisTemplate
        );
    }
}
