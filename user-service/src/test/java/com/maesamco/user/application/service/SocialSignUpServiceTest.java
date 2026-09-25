package com.maesamco.user.application.service;

import com.maesamco.user.application.port.AuthSession;
import com.maesamco.user.application.port.AuthSessionStore;
import com.maesamco.user.application.port.ConsumedSocialSignupToken;
import com.maesamco.user.application.port.EmailVerificationSecretHasher;
import com.maesamco.user.application.port.IssuedTokens;
import com.maesamco.user.application.port.RefreshTokenHasher;
import com.maesamco.user.application.port.SocialSignupTicket;
import com.maesamco.user.application.port.SocialSignupTokenStore;
import com.maesamco.user.application.port.TokenIssuer;
import com.maesamco.user.domain.entity.LearningLevel;
import com.maesamco.user.domain.entity.SocialProvider;
import com.maesamco.user.domain.entity.User;
import com.maesamco.user.domain.entity.UserRole;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 소셜 신규 회원가입 완료 흐름의 순서와 Token 소비 정책을 검증합니다(#308).
 */
@ExtendWith(MockitoExtension.class)
class SocialSignUpServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-09-24T00:00:00Z");

    private static final String RAW_TOKEN = "social-signup-token";
    private static final String TOKEN_HASH = "h".repeat(64);
    private static final String EMAIL_LOOKUP_HASH = "e".repeat(64);
    private static final String ENCRYPTED_EMAIL = "encrypted-email";
    private static final String PROVIDER_USER_ID = "google-sub-123";
    private static final String NICKNAME = "구글유저";

    private static final SocialSignupTicket TICKET =
            new SocialSignupTicket(
                    SocialProvider.GOOGLE,
                    PROVIDER_USER_ID,
                    EMAIL_LOOKUP_HASH,
                    ENCRYPTED_EMAIL
            );

    private static final Duration REMAINING_TTL =
            Duration.ofMinutes(7);

    private static final ConsumedSocialSignupToken CONSUMED =
            new ConsumedSocialSignupToken(TICKET, REMAINING_TTL);

    @Mock
    private EmailVerificationSecretHasher secretHasher;

    @Mock
    private SocialSignupTokenStore socialSignupTokenStore;

    @Mock
    private SignUpPersistenceService signUpPersistenceService;

    @Mock
    private SocialSignUpPersistenceService socialSignUpPersistenceService;

    @Mock
    private TokenIssuer tokenIssuer;

    @Mock
    private RefreshTokenHasher refreshTokenHasher;

    @Mock
    private AuthSessionStore authSessionStore;

    private SocialSignUpService socialSignUpService;

    @BeforeEach
    void setUp() {
        socialSignUpService =
                new SocialSignUpService(
                        secretHasher,
                        socialSignupTokenStore,
                        signUpPersistenceService,
                        socialSignUpPersistenceService,
                        new AuthSessionIssuer(
                                tokenIssuer,
                                refreshTokenHasher,
                                authSessionStore,
                                Clock.fixed(NOW, ZoneOffset.UTC)
                        )
                );
    }

    @Test
    @DisplayName(
            "Token 사전 조회 → 가입 가능 검증 → 닉네임 검증 → Token 소비 → User·SocialAccount 저장 → 세션 발급 순서로 처리한다"
    )
    void signUp_success() {
        // given
        givenValidTokenFound();

        when(socialSignupTokenStore.consume(TOKEN_HASH))
                .thenReturn(Optional.of(CONSUMED));

        when(
                socialSignUpPersistenceService.saveSocialUser(
                        any(User.class),
                        eq(SocialProvider.GOOGLE),
                        eq(PROVIDER_USER_ID)
                )
        ).thenAnswer(invocation -> invocation.getArgument(0));

        givenTokensIssued();

        // when
        SignUpResult result =
                socialSignUpService.signUp(command());

        // then
        assertThat(result.nickname()).isEqualTo(NICKNAME);
        assertThat(result.role()).isEqualTo(UserRole.USER);
        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.accessTokenExpiresIn()).isEqualTo(900);

        InOrder inOrder = inOrder(
                socialSignupTokenStore,
                socialSignUpPersistenceService,
                signUpPersistenceService,
                authSessionStore
        );

        inOrder.verify(socialSignupTokenStore).find(TOKEN_HASH);
        inOrder.verify(socialSignUpPersistenceService).validateSignupAvailable(TICKET);
        inOrder.verify(signUpPersistenceService).validateNicknameNotDuplicated(NICKNAME);
        inOrder.verify(socialSignupTokenStore).consume(TOKEN_HASH);
        inOrder.verify(socialSignUpPersistenceService)
                .saveSocialUser(any(User.class), eq(SocialProvider.GOOGLE), eq(PROVIDER_USER_ID));
        inOrder.verify(authSessionStore).save(any(AuthSession.class));

        // 클라이언트 입력이 아니라 Token에 귀속된 이메일로 비밀번호 없는 소셜 User를 만든다.
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(socialSignUpPersistenceService)
                .saveSocialUser(userCaptor.capture(), any(), anyString());

        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getEncryptedEmail()).isEqualTo(ENCRYPTED_EMAIL);
        assertThat(savedUser.getEmailLookupHash()).isEqualTo(EMAIL_LOOKUP_HASH);
        assertThat(savedUser.hasPassword()).isFalse();
        assertThat(savedUser.getNickname()).isEqualTo(NICKNAME);
    }

    @Test
    @DisplayName("존재하지 않거나 만료된 Token이면 SOCIAL_SIGNUP_TOKEN_INVALID이고 중복 검사를 하지 않는다")
    void signUp_unknownToken() {
        // given
        when(secretHasher.hashSignupToken(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(socialSignupTokenStore.find(TOKEN_HASH)).thenReturn(Optional.empty());

        // when & then
        assertBusinessError(ErrorCode.SOCIAL_SIGNUP_TOKEN_INVALID);

        // 유효한 Token 없이는 닉네임·계정 존재 여부를 조회하지 않는다(정찰 방지).
        verifyNoInteractions(signUpPersistenceService, socialSignUpPersistenceService);
        verify(socialSignupTokenStore, never()).consume(anyString());
    }

    @Test
    @DisplayName("다른 Provider로 발급된 Token이면 SOCIAL_SIGNUP_TOKEN_INVALID이고 Token을 소비하지 않는다")
    void signUp_providerMismatch() {
        // given
        when(secretHasher.hashSignupToken(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(socialSignupTokenStore.find(TOKEN_HASH))
                .thenReturn(Optional.of(new SocialSignupTicket(
                        SocialProvider.KAKAO,
                        PROVIDER_USER_ID,
                        EMAIL_LOOKUP_HASH,
                        ENCRYPTED_EMAIL
                )));

        // when & then
        assertBusinessError(ErrorCode.SOCIAL_SIGNUP_TOKEN_INVALID);

        verify(socialSignupTokenStore, never()).consume(anyString());
        verifyNoInteractions(signUpPersistenceService, socialSignUpPersistenceService);
    }

    @Test
    @DisplayName("닉네임이 중복되면 Token을 소비하지 않아 닉네임만 바꿔 재시도할 수 있다")
    void signUp_duplicateNickname_keepsToken() {
        // given
        givenValidTokenFound();

        doThrow(new BusinessException(ErrorCode.USER_DUPLICATE_NICKNAME))
                .when(signUpPersistenceService)
                .validateNicknameNotDuplicated(NICKNAME);

        // when & then
        assertBusinessError(ErrorCode.USER_DUPLICATE_NICKNAME);

        verify(socialSignupTokenStore, never()).consume(anyString());
        verify(socialSignUpPersistenceService, never()).saveSocialUser(any(), any(), anyString());
    }

    @Test
    @DisplayName("이미 가입된 Google 계정이면 SOCIAL_ACCOUNT_ALREADY_LINKED이고 User를 만들지 않는다")
    void signUp_alreadyLinked() {
        // given
        givenValidTokenFound();

        doThrow(new BusinessException(ErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED))
                .when(socialSignUpPersistenceService)
                .validateSignupAvailable(TICKET);

        // when & then
        assertBusinessError(ErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED);

        verify(socialSignupTokenStore, never()).consume(anyString());
        verify(socialSignUpPersistenceService, never()).saveSocialUser(any(), any(), anyString());
    }

    @Test
    @DisplayName("닉네임 형식이 잘못되면 Token을 소비하기 전에 실패한다")
    void signUp_invalidNickname_keepsToken() {
        // given
        givenValidTokenFound();

        // when & then
        assertThatThrownBy(
                () -> socialSignUpService.signUp(
                        new SocialSignUpCommand(
                                SocialProvider.GOOGLE,
                                RAW_TOKEN,
                                "공 백!",
                                3,
                                LearningLevel.BEGINNER
                        )
                )
        ).isInstanceOf(BusinessException.class);

        verify(socialSignupTokenStore, never()).consume(anyString());
    }

    @Test
    @DisplayName("사전 조회 후 다른 요청이 먼저 Token을 소비했다면 SOCIAL_SIGNUP_TOKEN_INVALID이고 저장하지 않는다")
    void signUp_tokenConsumedByConcurrentRequest() {
        // given
        givenValidTokenFound();

        when(socialSignupTokenStore.consume(TOKEN_HASH))
                .thenReturn(Optional.empty());

        // when & then
        assertBusinessError(ErrorCode.SOCIAL_SIGNUP_TOKEN_INVALID);

        verify(socialSignUpPersistenceService, never()).saveSocialUser(any(), any(), anyString());
        verifyNoInteractions(tokenIssuer, authSessionStore);
    }

    @Test
    @DisplayName("가입 저장 후 Redis 세션 저장에 실패하면 SIGNUP_AUTO_LOGIN_FAILED를 반환한다")
    void signUp_authSessionSaveFails() {
        // given
        givenValidTokenFound();

        when(socialSignupTokenStore.consume(TOKEN_HASH))
                .thenReturn(Optional.of(CONSUMED));

        when(
                socialSignUpPersistenceService.saveSocialUser(
                        any(User.class),
                        eq(SocialProvider.GOOGLE),
                        eq(PROVIDER_USER_ID)
                )
        ).thenAnswer(invocation -> invocation.getArgument(0));

        givenTokensIssued();

        doThrow(new IllegalStateException("redis down"))
                .when(authSessionStore)
                .save(any(AuthSession.class));

        // when & then
        assertBusinessError(ErrorCode.SIGNUP_AUTO_LOGIN_FAILED);
    }

    @Test
    @DisplayName(
            "Token 소비 후 동시 가입 경쟁으로 닉네임 중복이 나면 남은 TTL로 Token을 복구해 닉네임만 바꿔 재시도할 수 있다 (PR #320 리뷰)"
    )
    void signUp_nicknameRaceAfterConsume_restoresToken() {
        // given
        givenValidTokenFound();

        when(socialSignupTokenStore.consume(TOKEN_HASH))
                .thenReturn(Optional.of(CONSUMED));

        when(
                socialSignUpPersistenceService.saveSocialUser(
                        any(User.class),
                        eq(SocialProvider.GOOGLE),
                        eq(PROVIDER_USER_ID)
                )
        ).thenThrow(new BusinessException(ErrorCode.USER_DUPLICATE_NICKNAME));

        // when & then
        assertBusinessError(ErrorCode.USER_DUPLICATE_NICKNAME);

        // 원래 만료 시각을 넘지 않도록 소비 시점의 남은 TTL로만 복구한다.
        verify(socialSignupTokenStore).save(TOKEN_HASH, TICKET, REMAINING_TTL);
        verifyNoInteractions(tokenIssuer, authSessionStore);
    }

    @Test
    @DisplayName("Token 복구에 실패해도 원래 오류(닉네임 중복)를 그대로 반환한다")
    void signUp_nicknameRace_restoreFails_keepsOriginalError() {
        // given
        givenValidTokenFound();

        when(socialSignupTokenStore.consume(TOKEN_HASH))
                .thenReturn(Optional.of(CONSUMED));

        when(
                socialSignUpPersistenceService.saveSocialUser(
                        any(User.class),
                        eq(SocialProvider.GOOGLE),
                        eq(PROVIDER_USER_ID)
                )
        ).thenThrow(new BusinessException(ErrorCode.USER_DUPLICATE_NICKNAME));

        doThrow(new IllegalStateException("redis down"))
                .when(socialSignupTokenStore)
                .save(TOKEN_HASH, TICKET, REMAINING_TTL);

        // when & then
        assertBusinessError(ErrorCode.USER_DUPLICATE_NICKNAME);
    }

    @Test
    @DisplayName("재시도해도 성공할 수 없는 저장 실패(이미 연결된 계정)는 Token을 복구하지 않는다")
    void signUp_alreadyLinkedAfterConsume_doesNotRestoreToken() {
        // given
        givenValidTokenFound();

        when(socialSignupTokenStore.consume(TOKEN_HASH))
                .thenReturn(Optional.of(CONSUMED));

        when(
                socialSignUpPersistenceService.saveSocialUser(
                        any(User.class),
                        eq(SocialProvider.GOOGLE),
                        eq(PROVIDER_USER_ID)
                )
        ).thenThrow(new BusinessException(ErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED));

        // when & then
        assertBusinessError(ErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED);

        verify(socialSignupTokenStore, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("소비 시점에 남은 TTL을 알 수 없으면 Token을 복구하지 않는다")
    void signUp_nicknameRace_noRemainingTtl_doesNotRestore() {
        // given
        givenValidTokenFound();

        when(socialSignupTokenStore.consume(TOKEN_HASH))
                .thenReturn(Optional.of(new ConsumedSocialSignupToken(TICKET, Duration.ZERO)));

        when(
                socialSignUpPersistenceService.saveSocialUser(
                        any(User.class),
                        eq(SocialProvider.GOOGLE),
                        eq(PROVIDER_USER_ID)
                )
        ).thenThrow(new BusinessException(ErrorCode.USER_DUPLICATE_NICKNAME));

        // when & then
        assertBusinessError(ErrorCode.USER_DUPLICATE_NICKNAME);

        verify(socialSignupTokenStore, never()).save(any(), any(), any());
    }

    private void givenValidTokenFound() {
        when(secretHasher.hashSignupToken(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(socialSignupTokenStore.find(TOKEN_HASH)).thenReturn(Optional.of(TICKET));
    }

    private void givenTokensIssued() {
        when(
                tokenIssuer.issueTokens(
                        any(UUID.class),
                        eq(UserRole.USER),
                        any(UUID.class)
                )
        ).thenReturn(
                new IssuedTokens(
                        "access-token",
                        NOW.plusSeconds(900),
                        "refresh-token",
                        NOW.plusSeconds(60L * 60 * 24 * 14)
                )
        );

        when(refreshTokenHasher.hash("refresh-token"))
                .thenReturn("refresh-token-hash");
    }

    private SocialSignUpCommand command() {
        return new SocialSignUpCommand(
                SocialProvider.GOOGLE,
                RAW_TOKEN,
                NICKNAME,
                3,
                LearningLevel.BEGINNER
        );
    }

    private void assertBusinessError(ErrorCode errorCode) {
        assertThatThrownBy(
                () -> socialSignUpService.signUp(command())
        )
                .isInstanceOf(BusinessException.class)
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(errorCode);
    }
}
