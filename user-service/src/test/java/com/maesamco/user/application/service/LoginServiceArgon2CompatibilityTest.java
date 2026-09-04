package com.maesamco.user.application.service;

import com.maesamco.user.application.port.AuthSessionStore;
import com.maesamco.user.application.port.EmailLookupHasher;
import com.maesamco.user.application.port.PasswordHasher;
import com.maesamco.user.application.port.RefreshTokenHasher;
import com.maesamco.user.application.port.TokenIssuer;
import com.maesamco.user.domain.repository.UserRepository;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import com.maesamco.user.infrastructure.security.password.Argon2idPasswordHasher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * LoginService에서 사용하는 더미 Argon2id 해시가
 * 실제 Spring Security Argon2 구현체와 호환되는지 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class LoginServiceArgon2CompatibilityTest {

    @Mock
    private EmailNormalizer emailNormalizer;

    @Mock
    private EmailLookupHasher emailLookupHasher;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenIssuer tokenIssuer;

    @Mock
    private RefreshTokenHasher refreshTokenHasher;

    @Mock
    private AuthSessionStore authSessionStore;

    @Mock
    private Clock clock;

    private SimpleMeterRegistry meterRegistry;

    private LoginService loginService;

    @BeforeEach
    void setUp() {
        Argon2PasswordEncoder passwordEncoder =
                Argon2PasswordEncoder
                        .defaultsForSpringSecurity_v5_8();

        PasswordHasher passwordHasher =
                new Argon2idPasswordHasher(
                        passwordEncoder
                );

        meterRegistry =
                new SimpleMeterRegistry();

        loginService =
                new LoginService(
                        emailNormalizer,
                        emailLookupHasher,
                        passwordHasher,
                        userRepository,
                        tokenIssuer,
                        refreshTokenHasher,
                        authSessionStore,
                        meterRegistry,
                        clock
                );
    }

    @AfterEach
    void tearDown() {
        meterRegistry.close();
    }

    @Test
    @DisplayName(
            "존재하지 않는 사용자 로그인에서도 "
                    + "더미 Argon2id 해시를 실제 인코더가 정상 처리한다"
    )
    void login_userNotFound_dummyHashIsCompatibleWithRealArgon2() {
        // given
        String rawEmail =
                "learner@example.com";

        String normalizedEmail =
                "learner@example.com";

        String emailLookupHash =
                "a".repeat(64);

        LoginCommand command =
                new LoginCommand(
                        rawEmail,
                        "Abcd1234!"
                );

        when(
                emailNormalizer.normalize(
                        rawEmail
                )
        ).thenReturn(
                normalizedEmail
        );

        when(
                emailLookupHasher.hash(
                        normalizedEmail
                )
        ).thenReturn(
                emailLookupHash
        );

        when(
                userRepository
                        .findByEmailLookupHash(
                                emailLookupHash
                        )
        ).thenReturn(
                Optional.empty()
        );

        // when & then
        assertThatThrownBy(
                () -> loginService.login(command)
        )
                .isInstanceOf(
                        BusinessException.class
                )
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(
                        ErrorCode.INVALID_CREDENTIALS
                );
    }
}
