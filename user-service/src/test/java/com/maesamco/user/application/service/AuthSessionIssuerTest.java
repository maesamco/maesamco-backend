package com.maesamco.user.application.service;

import com.maesamco.user.application.port.AuthSession;
import com.maesamco.user.application.port.AuthSessionStore;
import com.maesamco.user.application.port.IssuedTokens;
import com.maesamco.user.application.port.RefreshTokenHasher;
import com.maesamco.user.application.port.TokenIssuer;
import com.maesamco.user.domain.entity.LearningLevel;
import com.maesamco.user.domain.entity.User;
import com.maesamco.user.domain.entity.UserRole;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthSessionIssuerTest {

    private static final Instant SESSION_STARTED_AT =
            Instant.parse("2026-09-25T00:00:00Z");

    private static final Instant SAVED_AT =
            SESSION_STARTED_AT.plusMillis(1_500);

    private static final Instant ACCESS_TOKEN_EXPIRES_AT =
            SESSION_STARTED_AT.plusSeconds(900);

    private static final Instant REFRESH_TOKEN_EXPIRES_AT =
            SESSION_STARTED_AT.plusSeconds(1_209_600);

    private static final IssuedTokens ISSUED_TOKENS =
            new IssuedTokens(
                    "access-token",
                    ACCESS_TOKEN_EXPIRES_AT,
                    "refresh-token",
                    REFRESH_TOKEN_EXPIRES_AT
            );

    @Mock
    private TokenIssuer tokenIssuer;

    @Mock
    private RefreshTokenHasher refreshTokenHasher;

    @Mock
    private AuthSessionStore authSessionStore;

    @Mock
    private Clock clock;

    private AuthSessionIssuer authSessionIssuer;

    private User user;

    @BeforeEach
    void setUp() {
        authSessionIssuer =
                new AuthSessionIssuer(
                        tokenIssuer,
                        refreshTokenHasher,
                        authSessionStore,
                        clock
                );

        user =
                User.create(
                        "encrypted-email",
                        "a".repeat(64),
                        "password-hash",
                        "김티암",
                        3,
                        LearningLevel.BEGINNER
                );
    }

    @Test
    @DisplayName(
            "새 세션 계열로 토큰을 발급하고 Refresh Token 해시만 담은 세션을 저장한다"
    )
    void issue_success() {
        // given
        givenTokensIssued();

        // when
        IssuedAuthSession result =
                authSessionIssuer.issue(
                        user,
                        AuthSessionPurpose.LOGIN
                );

        // then
        ArgumentCaptor<UUID> sessionIdCaptor =
                ArgumentCaptor.forClass(UUID.class);

        verify(tokenIssuer)
                .issueTokens(
                        eq(user.getId()),
                        eq(UserRole.USER),
                        sessionIdCaptor.capture()
                );

        ArgumentCaptor<AuthSession> authSessionCaptor =
                ArgumentCaptor.forClass(AuthSession.class);

        verify(authSessionStore)
                .save(authSessionCaptor.capture());

        AuthSession authSession =
                authSessionCaptor.getValue();

        assertThat(authSession.sessionId())
                .isEqualTo(sessionIdCaptor.getValue());
        assertThat(authSession.familyId())
                .isNotNull()
                .isNotEqualTo(authSession.sessionId());
        assertThat(authSession.userId())
                .isEqualTo(user.getId());
        assertThat(authSession.refreshTokenHash())
                .isEqualTo("refresh-token-hash");
        assertThat(authSession.createdAt())
                .isEqualTo(SESSION_STARTED_AT);
        assertThat(authSession.expiresAt())
                .isEqualTo(REFRESH_TOKEN_EXPIRES_AT);

        assertThat(result.sessionId())
                .isEqualTo(authSession.sessionId());
        assertThat(result.issuedTokens())
                .isEqualTo(ISSUED_TOKENS);
    }

    @Test
    @DisplayName(
            "세션 시작 시각은 토큰 발급 전에, Access Token 남은 시간은 세션 저장 후 시각으로 계산한다"
    )
    void issue_usesStartAndSavedInstants() {
        // given
        givenTokensIssued();

        // when
        IssuedAuthSession result =
                authSessionIssuer.issue(
                        user,
                        AuthSessionPurpose.LOGIN
                );

        // then
        InOrder order =
                inOrder(
                        clock,
                        tokenIssuer,
                        authSessionStore
                );

        order.verify(clock).instant();
        order.verify(tokenIssuer)
                .issueTokens(
                        eq(user.getId()),
                        eq(UserRole.USER),
                        any(UUID.class)
                );
        order.verify(authSessionStore)
                .save(any(AuthSession.class));
        order.verify(clock).instant();

        // 900초 - 1.5초 = 898.5초 → 올림하여 899초
        assertThat(result.accessTokenExpiresIn())
                .isEqualTo(899L);
    }

    @Test
    @DisplayName(
            "호출할 때마다 서로 다른 sessionId와 familyId를 만든다"
    )
    void issue_generatesNewIdentifiersEachTime() {
        // given
        when(clock.instant())
                .thenReturn(SESSION_STARTED_AT);
        when(
                tokenIssuer.issueTokens(
                        eq(user.getId()),
                        eq(UserRole.USER),
                        any(UUID.class)
                )
        ).thenReturn(ISSUED_TOKENS);
        when(refreshTokenHasher.hash("refresh-token"))
                .thenReturn("refresh-token-hash");

        // when
        authSessionIssuer.issue(user, AuthSessionPurpose.LOGIN);
        authSessionIssuer.issue(user, AuthSessionPurpose.LOGIN);

        // then
        ArgumentCaptor<AuthSession> captor =
                ArgumentCaptor.forClass(AuthSession.class);

        verify(authSessionStore, times(2))
                .save(captor.capture());

        AuthSession first = captor.getAllValues().get(0);
        AuthSession second = captor.getAllValues().get(1);

        assertThat(first.sessionId())
                .isNotEqualTo(second.sessionId());
        assertThat(first.familyId())
                .isNotEqualTo(second.familyId());
    }

    @ParameterizedTest
    @EnumSource(
            value = AuthSessionPurpose.class,
            names = {"LOGIN", "SOCIAL_LOGIN"}
    )
    @DisplayName(
            "로그인 계열은 세션 저장 실패 예외를 그대로 전파한다"
    )
    void issue_loginPurpose_propagatesSaveFailure(
            AuthSessionPurpose purpose
    ) {
        // given
        givenTokensIssuedWithoutSecondInstant();

        IllegalStateException saveFailure =
                new IllegalStateException("Redis 저장 실패");

        doThrow(saveFailure)
                .when(authSessionStore)
                .save(any(AuthSession.class));

        // when & then
        assertThatThrownBy(
                () -> authSessionIssuer.issue(user, purpose)
        ).isSameAs(saveFailure);
    }

    @ParameterizedTest
    @EnumSource(
            value = AuthSessionPurpose.class,
            names = {"SIGNUP", "SOCIAL_SIGNUP"}
    )
    @DisplayName(
            "회원가입 계열은 세션 저장 실패를 SIGNUP_AUTO_LOGIN_FAILED로 변환한다"
    )
    void issue_signupPurpose_translatesSaveFailure(
            AuthSessionPurpose purpose
    ) {
        // given
        givenTokensIssuedWithoutSecondInstant();

        doThrow(new IllegalStateException("Redis 저장 실패"))
                .when(authSessionStore)
                .save(any(AuthSession.class));

        // when & then
        assertThatThrownBy(
                () -> authSessionIssuer.issue(user, purpose)
        )
                .isInstanceOf(BusinessException.class)
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(ErrorCode.SIGNUP_AUTO_LOGIN_FAILED);
    }

    @Test
    @DisplayName(
            "토큰 발급 실패는 회원가입 계열이어도 변환하지 않고 세션을 저장하지 않는다"
    )
    void issue_tokenIssueFailure_propagatesWithoutSaving() {
        // given
        when(clock.instant())
                .thenReturn(SESSION_STARTED_AT);

        IllegalStateException issueFailure =
                new IllegalStateException("서명 키 오류");

        when(
                tokenIssuer.issueTokens(
                        eq(user.getId()),
                        eq(UserRole.USER),
                        any(UUID.class)
                )
        ).thenThrow(issueFailure);

        // when & then
        assertThatThrownBy(
                () -> authSessionIssuer.issue(
                        user,
                        AuthSessionPurpose.SIGNUP
                )
        ).isSameAs(issueFailure);

        verifyNoInteractions(authSessionStore);
    }

    @Test
    @DisplayName("toString은 토큰 원문을 노출하지 않는다")
    void issuedAuthSession_toStringMasksTokens() {
        IssuedAuthSession issuedAuthSession =
                new IssuedAuthSession(
                        UUID.randomUUID(),
                        ISSUED_TOKENS,
                        900L
                );

        assertThat(issuedAuthSession.toString())
                .doesNotContain("access-token")
                .doesNotContain("refresh-token")
                .contains("[PROTECTED]");
    }

    private void givenTokensIssued() {
        when(clock.instant())
                .thenReturn(SESSION_STARTED_AT, SAVED_AT);

        when(
                tokenIssuer.issueTokens(
                        eq(user.getId()),
                        eq(UserRole.USER),
                        any(UUID.class)
                )
        ).thenReturn(ISSUED_TOKENS);

        when(refreshTokenHasher.hash("refresh-token"))
                .thenReturn("refresh-token-hash");
    }

    private void givenTokensIssuedWithoutSecondInstant() {
        when(clock.instant())
                .thenReturn(SESSION_STARTED_AT);

        when(
                tokenIssuer.issueTokens(
                        eq(user.getId()),
                        eq(UserRole.USER),
                        any(UUID.class)
                )
        ).thenReturn(ISSUED_TOKENS);

        when(refreshTokenHasher.hash("refresh-token"))
                .thenReturn("refresh-token-hash");
    }
}
