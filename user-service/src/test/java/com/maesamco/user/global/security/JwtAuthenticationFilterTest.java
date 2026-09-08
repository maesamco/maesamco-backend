package com.maesamco.user.global.security;

import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * JwtAuthenticationFilter의 Access Token 검증 및
 * 공개 API 요청 통과 규칙을 검증합니다.
 *
 * <p>특히 Access Token이 만료된 상태에서도 Refresh Token으로
 * 재발급을 요청할 수 있도록, 잘못되거나 만료된 Access Token 때문에
 * 요청 자체를 중단하지 않는지 검증합니다.</p>
 */
class JwtAuthenticationFilterTest {

    private static final UUID USER_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    private static final UUID SESSION_ID =
            UUID.fromString(
                    "22222222-2222-2222-2222-222222222222"
            );

    private static final KeyPair ACCESS_KEY_PAIR =
            generateKeyPair();

    private final JwtAuthenticationFilter filter =
            new JwtAuthenticationFilter(
                    ACCESS_KEY_PAIR.getPublic()
            );

    /**
     * 각 테스트 종료 후 SecurityContext가 다음 테스트로
     * 전달되지 않도록 정리합니다.
     */
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName(
            "만료된 Access Token이 있어도 요청을 중단하지 않고 "
                    + "다음 FilterChain으로 전달한다"
    )
    void expiredAccessToken_doesNotBlockRequest()
            throws Exception {
        // given
        Instant now = Instant.now();

        String expiredAccessToken =
                createAccessToken(
                        now.minusSeconds(1_800),
                        now.minusSeconds(900)
                );

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        request.addHeader(
                HttpHeaders.AUTHORIZATION,
                "Bearer " + expiredAccessToken
        );

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        FilterChain filterChain =
                mock(FilterChain.class);

        // when
        filter.doFilter(
                request,
                response,
                filterChain
        );

        // then
        verify(filterChain)
                .doFilter(
                        request,
                        response
                );

        assertThat(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        ).isNull();
    }

    @Test
    @DisplayName(
            "Authorization 헤더가 없어도 공개 API 요청은 "
                    + "다음 FilterChain으로 전달한다"
    )
    void missingAccessToken_doesNotBlockRequest()
            throws Exception {
        // given
        MockHttpServletRequest request =
                new MockHttpServletRequest();

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        FilterChain filterChain =
                mock(FilterChain.class);

        // when
        filter.doFilter(
                request,
                response,
                filterChain
        );

        // then
        verify(filterChain)
                .doFilter(
                        request,
                        response
                );

        assertThat(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        ).isNull();
    }

    @Test
    @DisplayName(
            "위조된 Access Token이 있어도 SecurityContext만 비우고 "
                    + "다음 FilterChain으로 전달한다"
    )
    void invalidAccessToken_doesNotBlockRequest()
            throws Exception {
        // given
        KeyPair attackerKeyPair =
                generateKeyPair();

        Instant now = Instant.now();

        String forgedAccessToken =
                Jwts.builder()
                        .subject(USER_ID.toString())
                        .claim(
                                "role",
                                "USER"
                        )
                        .claim(
                                "tokenType",
                                TokenType.ACCESS.name()
                        )
                        .claim(
                                "sessionId",
                                SESSION_ID.toString()
                        )
                        .issuedAt(
                                Date.from(
                                        now.minusSeconds(60)
                                )
                        )
                        .expiration(
                                Date.from(
                                        now.plusSeconds(900)
                                )
                        )
                        .id(UUID.randomUUID().toString())
                        .signWith(
                                attackerKeyPair.getPrivate()
                        )
                        .compact();

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        request.addHeader(
                HttpHeaders.AUTHORIZATION,
                "Bearer " + forgedAccessToken
        );

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        FilterChain filterChain =
                mock(FilterChain.class);

        // when
        filter.doFilter(
                request,
                response,
                filterChain
        );

        // then
        verify(filterChain)
                .doFilter(
                        request,
                        response
                );

        assertThat(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        ).isNull();
    }

    /**
     * Access Token 공개키로 검증할 수 있는 테스트용 JWT를 생성합니다.
     *
     * @param issuedAt 발급 시각
     * @param expiresAt 만료 시각
     * @return 서명된 Access Token
     */
    private String createAccessToken(
            Instant issuedAt,
            Instant expiresAt
    ) {
        return Jwts.builder()
                .subject(USER_ID.toString())
                .claim(
                        "role",
                        "USER"
                )
                .claim(
                        "tokenType",
                        TokenType.ACCESS.name()
                )
                .claim(
                        "sessionId",
                        SESSION_ID.toString()
                )
                .issuedAt(
                        Date.from(issuedAt)
                )
                .expiration(
                        Date.from(expiresAt)
                )
                .id(UUID.randomUUID().toString())
                .signWith(
                        ACCESS_KEY_PAIR.getPrivate()
                )
                .compact();
    }

    /**
     * 테스트용 2048비트 RSA 키쌍을 생성합니다.
     *
     * @return RSA 키쌍
     */
    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator =
                    KeyPairGenerator.getInstance("RSA");

            generator.initialize(2048);

            return generator.generateKeyPair();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "테스트 RSA 키쌍을 생성할 수 없습니다.",
                    exception
            );
        }
    }
}
