package com.maesamco.user.application.service;

import com.maesamco.user.application.port.AuthSession;
import com.maesamco.user.application.port.AuthSessionStore;
import com.maesamco.user.application.port.EmailCipher;
import com.maesamco.user.application.port.EmailLookupHasher;
import com.maesamco.user.application.port.IssuedTokens;
import com.maesamco.user.application.port.PasswordHasher;
import com.maesamco.user.application.port.RefreshTokenHasher;
import com.maesamco.user.application.port.TokenIssuer;
import com.maesamco.user.domain.entity.LearningLevel;
import com.maesamco.user.domain.entity.User;
import com.maesamco.user.domain.entity.UserGamificationState;
import com.maesamco.user.domain.entity.UserRole;
import com.maesamco.user.domain.entity.UserStatus;
import com.maesamco.user.domain.repository.UserGamificationStateRepository;
import com.maesamco.user.domain.repository.UserRepository;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SignUpService의 회원가입 오케스트레이션을 검증하는 단위 테스트입니다.
 *
 * <p>사용자 생성, 초기 게이미피케이션 상태 생성,
 * 인증 토큰 발급 및 Redis 인증 세션 저장까지의 흐름을 검증합니다.</p>
 */
@ExtendWith(MockitoExtension.class)
class SignUpServiceTest {

    @Mock
    private EmailNormalizer emailNormalizer;

    @Mock
    private EmailCipher emailCipher;

    @Mock
    private EmailLookupHasher emailLookupHasher;

    @Mock
    private PasswordHasher passwordHasher;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserGamificationStateRepository gamificationStateRepository;

    @Mock
    private TokenIssuer tokenIssuer;

    @Mock
    private RefreshTokenHasher refreshTokenHasher;

    @Mock
    private AuthSessionStore authSessionStore;

    @Mock
    private Clock clock;

    @InjectMocks
    private SignUpService signUpService;

    @Test
    @DisplayName("회원가입하면 사용자와 초기 게이미피케이션 상태 및 인증 세션을 생성한다")
    void signUp() {
        // given
        String rawEmail = " Learner@Example.com ";
        String trimmedEmail = "Learner@Example.com";
        String normalizedEmail = "learner@example.com";
        String emailLookupHash = "a".repeat(64);
        String encryptedEmail = "encrypted-email";
        String rawPassword = "Abcd1234!";
        String passwordHash = "argon2-password-hash";
        String refreshTokenHash = "refresh-token-hash";

        Instant now =
                Instant.parse("2026-09-02T11:00:00Z");

        Instant accessTokenExpiresAt =
                now.plusSeconds(900);

        Instant refreshTokenExpiresAt =
                now.plusSeconds(60L * 60 * 24 * 7);

        IssuedTokens issuedTokens = new IssuedTokens(
                "access-token",
                accessTokenExpiresAt,
                "refresh-token",
                refreshTokenExpiresAt
        );

        SignUpCommand command = new SignUpCommand(
                rawEmail,
                rawPassword,
                " 김티암 ",
                3,
                LearningLevel.BEGINNER
        );

        when(emailNormalizer.normalize(trimmedEmail))
                .thenReturn(normalizedEmail);

        when(emailLookupHasher.hash(normalizedEmail))
                .thenReturn(emailLookupHash);

        when(userRepository.existsByEmailLookupHash(emailLookupHash))
                .thenReturn(false);

        when(userRepository.existsByNicknameIgnoreCase("김티암"))
                .thenReturn(false);

        when(emailCipher.encrypt(normalizedEmail))
                .thenReturn(encryptedEmail);

        when(passwordHasher.hash(rawPassword))
                .thenReturn(passwordHash);

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        when(
                tokenIssuer.issueTokens(
                        any(UUID.class),
                        eq(UserRole.USER),
                        any(UUID.class)
                )
        ).thenReturn(issuedTokens);

        when(refreshTokenHasher.hash("refresh-token"))
                .thenReturn(refreshTokenHash);

        when(clock.instant())
                .thenReturn(now);

        // when
        SignUpResult result =
                signUpService.signUp(command);

        // then
        verify(emailNormalizer)
                .normalize(trimmedEmail);

        ArgumentCaptor<User> userCaptor =
                ArgumentCaptor.forClass(User.class);

        verify(userRepository)
                .save(userCaptor.capture());

        User savedUser = userCaptor.getValue();

        assertThat(savedUser.getId())
                .isNotNull();
        assertThat(savedUser.getEncryptedEmail())
                .isEqualTo(encryptedEmail);
        assertThat(savedUser.getEmailLookupHash())
                .isEqualTo(emailLookupHash);
        assertThat(savedUser.getPasswordHash())
                .isEqualTo(passwordHash);
        assertThat(savedUser.getNickname())
                .isEqualTo("김티암");
        assertThat(savedUser.getRole())
                .isEqualTo(UserRole.USER);
        assertThat(savedUser.getStatus())
                .isEqualTo(UserStatus.ACTIVE);
        assertThat(savedUser.getJavaExperienceMonths())
                .isEqualTo(3);
        assertThat(savedUser.getLearningLevel())
                .isEqualTo(LearningLevel.BEGINNER);

        ArgumentCaptor<UserGamificationState> gamificationCaptor =
                ArgumentCaptor.forClass(
                        UserGamificationState.class
                );

        verify(gamificationStateRepository)
                .save(gamificationCaptor.capture());

        UserGamificationState gamificationState =
                gamificationCaptor.getValue();

        assertThat(gamificationState.getUserId())
                .isEqualTo(savedUser.getId());
        assertThat(gamificationState.getTotalXp())
                .isZero();
        assertThat(gamificationState.getLevel())
                .isEqualTo(1);
        assertThat(gamificationState.getCurrentStreak())
                .isZero();
        assertThat(gamificationState.getLongestStreak())
                .isZero();
        assertThat(gamificationState.getLastActivityDate())
                .isNull();

        ArgumentCaptor<UUID> sessionIdCaptor =
                ArgumentCaptor.forClass(UUID.class);

        verify(tokenIssuer)
                .issueTokens(
                        eq(savedUser.getId()),
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
                .isNotNull();
        assertThat(authSession.userId())
                .isEqualTo(savedUser.getId());
        assertThat(authSession.refreshTokenHash())
                .isEqualTo(refreshTokenHash);
        assertThat(authSession.createdAt())
                .isEqualTo(now);
        assertThat(authSession.expiresAt())
                .isEqualTo(refreshTokenExpiresAt);

        assertThat(result.userId())
                .isEqualTo(savedUser.getId());
        assertThat(result.issuedTokens())
                .isSameAs(issuedTokens);
    }

    @Test
    @DisplayName("이미 사용 중인 이메일이면 회원가입을 거부한다")
    void signUp_duplicateEmail() {
        // given
        String rawEmail = " Learner@Example.com ";
        String trimmedEmail = "Learner@Example.com";
        String normalizedEmail = "learner@example.com";
        String emailLookupHash = "a".repeat(64);

        SignUpCommand command = new SignUpCommand(
                rawEmail,
                "Abcd1234!",
                "김티암",
                3,
                LearningLevel.BEGINNER
        );

        when(emailNormalizer.normalize(trimmedEmail))
                .thenReturn(normalizedEmail);

        when(emailLookupHasher.hash(normalizedEmail))
                .thenReturn(emailLookupHash);

        when(userRepository.existsByEmailLookupHash(emailLookupHash))
                .thenReturn(true);

        // when & then
        assertThatThrownBy(() -> signUpService.signUp(command))
                .isInstanceOf(BusinessException.class)
                .extracting(
                        exception ->
                                ((BusinessException) exception).getErrorCode()
                )
                .isEqualTo(ErrorCode.USER_DUPLICATE_EMAIL);

        verify(emailNormalizer)
                .normalize(trimmedEmail);

        verify(
                userRepository,
                never()
        ).existsByNicknameIgnoreCase(any());

        verify(
                emailCipher,
                never()
        ).encrypt(any());

        verify(
                passwordHasher,
                never()
        ).hash(any());

        verify(
                userRepository,
                never()
        ).save(any(User.class));

        verify(
                gamificationStateRepository,
                never()
        ).save(any(UserGamificationState.class));

        verify(
                tokenIssuer,
                never()
        ).issueTokens(any(), any(), any());

        verify(
                authSessionStore,
                never()
        ).save(any(AuthSession.class));
    }

    @Test
    @DisplayName("DB 트랜잭션이 롤백되면 생성한 Redis 인증 세션을 삭제한다")
    void signUp_deletesAuthSessionWhenTransactionRollsBack() {
        // given
        String trimmedEmail = "Learner@Example.com";
        String normalizedEmail = "learner@example.com";
        String emailLookupHash = "b".repeat(64);

        Instant now =
                Instant.parse("2026-09-02T11:00:00Z");

        Instant accessTokenExpiresAt =
                now.plusSeconds(900);

        Instant refreshTokenExpiresAt =
                now.plusSeconds(60L * 60 * 24 * 7);

        IssuedTokens issuedTokens = new IssuedTokens(
                "access-token",
                accessTokenExpiresAt,
                "refresh-token",
                refreshTokenExpiresAt
        );

        SignUpCommand command = new SignUpCommand(
                " Learner@Example.com ",
                "Abcd1234!",
                "김티암",
                3,
                LearningLevel.BEGINNER
        );

        when(emailNormalizer.normalize(trimmedEmail))
                .thenReturn(normalizedEmail);

        when(emailLookupHasher.hash(normalizedEmail))
                .thenReturn(emailLookupHash);

        when(userRepository.existsByEmailLookupHash(emailLookupHash))
                .thenReturn(false);

        when(userRepository.existsByNicknameIgnoreCase("김티암"))
                .thenReturn(false);

        when(emailCipher.encrypt(normalizedEmail))
                .thenReturn("encrypted-email");

        when(passwordHasher.hash("Abcd1234!"))
                .thenReturn("argon2-password-hash");

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        when(
                tokenIssuer.issueTokens(
                        any(UUID.class),
                        eq(UserRole.USER),
                        any(UUID.class)
                )
        ).thenReturn(issuedTokens);

        when(refreshTokenHasher.hash("refresh-token"))
                .thenReturn("refresh-token-hash");

        when(clock.instant())
                .thenReturn(now);

        TransactionSynchronizationManager
                .initSynchronization();

        try {
            signUpService.signUp(command);

            ArgumentCaptor<AuthSession> authSessionCaptor =
                    ArgumentCaptor.forClass(AuthSession.class);

            verify(authSessionStore)
                    .save(authSessionCaptor.capture());

            UUID sessionId =
                    authSessionCaptor.getValue().sessionId();

            assertThat(
                    TransactionSynchronizationManager
                            .getSynchronizations()
            ).hasSize(1);

            TransactionSynchronization synchronization =
                    TransactionSynchronizationManager
                            .getSynchronizations()
                            .get(0);

            // when
            synchronization.afterCompletion(
                    TransactionSynchronization.STATUS_ROLLED_BACK
            );

            // then
            verify(authSessionStore)
                    .deleteBySessionId(sessionId);
        } finally {
            if (TransactionSynchronizationManager
                    .isSynchronizationActive()) {
                TransactionSynchronizationManager
                        .clearSynchronization();
            }
        }
    }
}
