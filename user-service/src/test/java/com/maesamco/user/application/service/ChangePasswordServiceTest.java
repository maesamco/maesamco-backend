package com.maesamco.user.application.service;

import com.maesamco.user.application.port.AuthSessionLogoutAllStore;
import com.maesamco.user.application.port.EmailCipher;
import com.maesamco.user.application.port.PasswordHasher;
import com.maesamco.user.domain.entity.LearningLevel;
import com.maesamco.user.domain.entity.User;
import com.maesamco.user.domain.repository.UserRepository;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * ChangePasswordService의 비밀번호 변경 및
 * 인증 세션 무효화 정책을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class ChangePasswordServiceTest {

    private static final UUID USER_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    private static final String ENCRYPTED_EMAIL =
            "encrypted-email";

    private static final String EMAIL =
            "learner@example.com";

    private static final String EMAIL_LOOKUP_HASH =
            "a".repeat(64);

    private static final String NICKNAME =
            "learner123";

    private static final String CURRENT_PASSWORD =
            "Abcd1234!";

    private static final String CURRENT_PASSWORD_HASH =
            "current-password-hash";

    private static final String NEW_PASSWORD =
            "NewAbcd1234!";

    private static final String NEW_PASSWORD_HASH =
            "new-password-hash";

    private static final Instant INVALIDATED_AT =
            Instant.parse(
                    "2026-09-15T02:00:00Z"
            );

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordHasher passwordHasher;

    @Mock
    private EmailCipher emailCipher;

    @Mock
    private AuthSessionLogoutAllStore authSessionLogoutAllStore;

    @Mock
    private Clock clock;

    @InjectMocks
    private ChangePasswordService changePasswordService;

    @Test
    @DisplayName(
            "현재 비밀번호가 일치하면 새 비밀번호 해시를 저장하고 "
                    + "모든 인증 세션을 무효화한다"
    )
    void changePassword() {
        // given
        User user = createActiveUser();

        ChangePasswordCommand command =
                new ChangePasswordCommand(
                        CURRENT_PASSWORD,
                        NEW_PASSWORD
                );

        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(user));

        when(passwordHasher.matches(
                CURRENT_PASSWORD,
                CURRENT_PASSWORD_HASH
        )).thenReturn(true);

        when(passwordHasher.matches(
                NEW_PASSWORD,
                CURRENT_PASSWORD_HASH
        )).thenReturn(false);

        when(emailCipher.decrypt(ENCRYPTED_EMAIL))
                .thenReturn(EMAIL);

        when(passwordHasher.hash(NEW_PASSWORD))
                .thenReturn(NEW_PASSWORD_HASH);

        when(clock.instant())
                .thenReturn(INVALIDATED_AT);

        // when & then
        assertThatCode(
                () -> changePasswordService.changePassword(
                        USER_ID,
                        command
                )
        ).doesNotThrowAnyException();

        assertThat(user.getPasswordHash())
                .isEqualTo(NEW_PASSWORD_HASH);

        verify(userRepository)
                .save(user);

        verify(authSessionLogoutAllStore)
                .logoutAll(
                        USER_ID,
                        INVALIDATED_AT
                );
    }

    @Test
    @DisplayName("사용자가 존재하지 않으면 USER_NOT_FOUND를 반환한다")
    void userNotFound() {
        // given
        ChangePasswordCommand command =
                new ChangePasswordCommand(
                        CURRENT_PASSWORD,
                        NEW_PASSWORD
                );

        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(
                () -> changePasswordService.changePassword(
                        USER_ID,
                        command
                )
        )
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(
                                        exception.getErrorCode()
                                ).isEqualTo(
                                        ErrorCode.USER_NOT_FOUND
                                )
                );

        verifyNoInteractions(
                passwordHasher,
                emailCipher,
                authSessionLogoutAllStore,
                clock
        );
    }

    @Test
    @DisplayName("정지된 사용자는 비밀번호를 변경할 수 없다")
    void suspendedUser() {
        // given
        User user = createActiveUser();
        user.suspend();

        ChangePasswordCommand command =
                new ChangePasswordCommand(
                        CURRENT_PASSWORD,
                        NEW_PASSWORD
                );

        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(user));

        // when & then
        assertThatThrownBy(
                () -> changePasswordService.changePassword(
                        USER_ID,
                        command
                )
        )
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(
                                        exception.getErrorCode()
                                ).isEqualTo(
                                        ErrorCode.USER_NOT_ACTIVE
                                )
                );

        verify(userRepository, never())
                .save(user);

        verifyNoInteractions(
                passwordHasher,
                emailCipher,
                authSessionLogoutAllStore,
                clock
        );
    }

    @Test
    @DisplayName(
            "현재 비밀번호가 일치하지 않으면 "
                    + "USER_CURRENT_PASSWORD_MISMATCH를 반환한다"
    )
    void currentPasswordMismatch() {
        // given
        User user = createActiveUser();

        ChangePasswordCommand command =
                new ChangePasswordCommand(
                        "WrongPassword1!",
                        NEW_PASSWORD
                );

        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(user));

        when(passwordHasher.matches(
                "WrongPassword1!",
                CURRENT_PASSWORD_HASH
        )).thenReturn(false);

        // when & then
        assertThatThrownBy(
                () -> changePasswordService.changePassword(
                        USER_ID,
                        command
                )
        )
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(
                                        exception.getErrorCode()
                                ).isEqualTo(
                                        ErrorCode
                                                .USER_CURRENT_PASSWORD_MISMATCH
                                )
                );

        verify(userRepository, never())
                .save(user);

        verifyNoInteractions(
                emailCipher,
                authSessionLogoutAllStore,
                clock
        );
    }

    @Test
    @DisplayName("현재 비밀번호와 같은 새 비밀번호는 사용할 수 없다")
    void sameAsCurrentPassword() {
        // given
        User user = createActiveUser();

        ChangePasswordCommand command =
                new ChangePasswordCommand(
                        CURRENT_PASSWORD,
                        CURRENT_PASSWORD
                );

        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(user));

        when(passwordHasher.matches(
                CURRENT_PASSWORD,
                CURRENT_PASSWORD_HASH
        )).thenReturn(true);

        // when & then
        assertThatThrownBy(
                () -> changePasswordService.changePassword(
                        USER_ID,
                        command
                )
        )
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(
                                        exception.getErrorCode()
                                ).isEqualTo(
                                        ErrorCode
                                                .USER_PASSWORD_POLICY_VIOLATION
                                )
                );

        verify(userRepository, never())
                .save(user);

        verifyNoInteractions(
                emailCipher,
                authSessionLogoutAllStore,
                clock
        );
    }

    @Test
    @DisplayName("이메일과 같은 새 비밀번호는 사용할 수 없다")
    void sameAsEmail() {
        // given
        User user = createActiveUser();

        ChangePasswordCommand command =
                new ChangePasswordCommand(
                        CURRENT_PASSWORD,
                        EMAIL.toUpperCase()
                );

        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(user));

        when(passwordHasher.matches(
                CURRENT_PASSWORD,
                CURRENT_PASSWORD_HASH
        )).thenReturn(true);

        when(passwordHasher.matches(
                EMAIL.toUpperCase(),
                CURRENT_PASSWORD_HASH
        )).thenReturn(false);

        when(emailCipher.decrypt(ENCRYPTED_EMAIL))
                .thenReturn(EMAIL);

        // when & then
        assertThatThrownBy(
                () -> changePasswordService.changePassword(
                        USER_ID,
                        command
                )
        )
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(
                                        exception.getErrorCode()
                                ).isEqualTo(
                                        ErrorCode
                                                .USER_PASSWORD_POLICY_VIOLATION
                                )
                );

        verify(passwordHasher, never())
                .hash(EMAIL.toUpperCase());

        verify(userRepository, never())
                .save(user);

        verifyNoInteractions(
                authSessionLogoutAllStore,
                clock
        );
    }

    @Test
    @DisplayName("닉네임과 같은 새 비밀번호는 사용할 수 없다")
    void sameAsNickname() {
        // given
        User user = createActiveUser();

        ChangePasswordCommand command =
                new ChangePasswordCommand(
                        CURRENT_PASSWORD,
                        NICKNAME.toUpperCase()
                );

        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(user));

        when(passwordHasher.matches(
                CURRENT_PASSWORD,
                CURRENT_PASSWORD_HASH
        )).thenReturn(true);

        when(passwordHasher.matches(
                NICKNAME.toUpperCase(),
                CURRENT_PASSWORD_HASH
        )).thenReturn(false);

        when(emailCipher.decrypt(ENCRYPTED_EMAIL))
                .thenReturn(EMAIL);

        // when & then
        assertThatThrownBy(
                () -> changePasswordService.changePassword(
                        USER_ID,
                        command
                )
        )
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(
                                        exception.getErrorCode()
                                ).isEqualTo(
                                        ErrorCode
                                                .USER_PASSWORD_POLICY_VIOLATION
                                )
                );

        verify(passwordHasher, never())
                .hash(NICKNAME.toUpperCase());

        verify(userRepository, never())
                .save(user);

        verifyNoInteractions(
                authSessionLogoutAllStore,
                clock
        );
    }

    @Test
    @DisplayName("사용자 식별자가 없으면 비밀번호를 변경하지 않는다")
    void nullUserId() {
        // given
        ChangePasswordCommand command =
                new ChangePasswordCommand(
                        CURRENT_PASSWORD,
                        NEW_PASSWORD
                );

        // when & then
        assertThatThrownBy(
                () -> changePasswordService.changePassword(
                        null,
                        command
                )
        )
                .isInstanceOf(
                        NullPointerException.class
                )
                .hasMessage(
                        "사용자 식별자는 필수입니다."
                );

        verifyNoInteractions(
                userRepository,
                passwordHasher,
                emailCipher,
                authSessionLogoutAllStore,
                clock
        );
    }

    @Test
    @DisplayName("비밀번호 변경 명령이 없으면 비밀번호를 변경하지 않는다")
    void nullCommand() {
        // when & then
        assertThatThrownBy(
                () -> changePasswordService.changePassword(
                        USER_ID,
                        null
                )
        )
                .isInstanceOf(
                        NullPointerException.class
                )
                .hasMessage(
                        "비밀번호 변경 명령은 필수입니다."
                );

        verifyNoInteractions(
                userRepository,
                passwordHasher,
                emailCipher,
                authSessionLogoutAllStore,
                clock
        );
    }

    @Test
    @DisplayName("비밀번호가 없는 소셜 계정은 비밀번호를 변경할 수 없다 (#308)")
    void changePassword_socialUserWithoutPassword() {
        // given
        User socialUser =
                User.createSocial(
                        ENCRYPTED_EMAIL,
                        EMAIL_LOOKUP_HASH,
                        "구글유저",
                        3,
                        LearningLevel.BEGINNER
                );

        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(socialUser));

        // when & then
        assertThatThrownBy(
                () -> changePasswordService.changePassword(
                        USER_ID,
                        new ChangePasswordCommand(CURRENT_PASSWORD, NEW_PASSWORD)
                )
        )
                .isInstanceOf(BusinessException.class)
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(ErrorCode.USER_PASSWORD_NOT_SET);

        verifyNoInteractions(passwordHasher, authSessionLogoutAllStore);

        verify(userRepository, never())
                .save(socialUser);
    }

    private User createActiveUser() {
        return User.create(
                ENCRYPTED_EMAIL,
                EMAIL_LOOKUP_HASH,
                CURRENT_PASSWORD_HASH,
                NICKNAME,
                3,
                LearningLevel.BEGINNER
        );
    }
}
