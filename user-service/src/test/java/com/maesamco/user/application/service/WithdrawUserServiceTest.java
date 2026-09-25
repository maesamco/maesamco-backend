package com.maesamco.user.application.service;

import com.maesamco.user.application.port.AuthSessionLogoutAllStore;
import com.maesamco.user.application.port.PasswordHasher;
import com.maesamco.user.domain.entity.LearningLevel;
import com.maesamco.user.domain.entity.SocialProvider;
import com.maesamco.user.domain.entity.User;
import com.maesamco.user.domain.entity.UserInterestConcept;
import com.maesamco.user.domain.repository.SocialAccountRepository;
import com.maesamco.user.domain.repository.UserInterestConceptRepository;
import com.maesamco.user.domain.repository.UserRepository;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * WithdrawUserService의 회원 탈퇴,
 * 연관 데이터 논리 삭제 및 인증 세션 무효화 정책을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class WithdrawUserServiceTest {

    private static final UUID USER_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    private static final UUID FIRST_CONCEPT_ID =
            UUID.fromString(
                    "22222222-2222-2222-2222-222222222222"
            );

    private static final UUID SECOND_CONCEPT_ID =
            UUID.fromString(
                    "33333333-3333-3333-3333-333333333333"
            );

    private static final String ENCRYPTED_EMAIL =
            "encrypted-email";

    private static final String EMAIL_LOOKUP_HASH =
            "a".repeat(64);

    private static final String CURRENT_PASSWORD =
            "Abcd1234!";

    private static final String CURRENT_PASSWORD_HASH =
            "current-password-hash";

    private static final Instant INVALIDATED_AT =
            Instant.parse(
                    "2026-09-15T08:00:00Z"
            );

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserInterestConceptRepository
            interestConceptRepository;

    @Mock
    private SocialAccountRepository socialAccountRepository;

    @Mock
    private PasswordHasher passwordHasher;

    @Mock
    private SocialReauthenticator socialReauthenticator;

    @Mock
    private AuthSessionLogoutAllStore
            authSessionLogoutAllStore;

    @Mock
    private Clock clock;

    @InjectMocks
    private WithdrawUserService withdrawUserService;

    @Test
    @DisplayName(
            "현재 비밀번호가 일치하면 사용자와 관심 개념을 논리 삭제하고 "
                    + "모든 인증 세션을 무효화한다"
    )
    void withdraw() {
        // given
        User user = createActiveUser();

        WithdrawUserCommand command =
                new WithdrawUserCommand(
                        CURRENT_PASSWORD
                );

        when(userRepository.findByIdForUpdate(USER_ID))
                .thenReturn(Optional.of(user));

        when(passwordHasher.matches(
                CURRENT_PASSWORD,
                CURRENT_PASSWORD_HASH
        )).thenReturn(true);

        when(clock.instant())
                .thenReturn(INVALIDATED_AT);

        when(
                interestConceptRepository.softDeleteAllByUserId(
                        USER_ID,
                        USER_ID,
                        INVALIDATED_AT
                )
        ).thenReturn(2);

        // when & then
        assertThatCode(
                () -> withdrawUserService.withdraw(
                        USER_ID,
                        command
                )
        ).doesNotThrowAnyException();

        assertThat(user.isDeleted()).isTrue();
        assertThat(user.getDeletedBy()).isEqualTo(USER_ID);
        assertThat(user.getDeletedAt()).isEqualTo(INVALIDATED_AT);

        verify(interestConceptRepository)
                .softDeleteAllByUserId(
                        USER_ID,
                        USER_ID,
                        INVALIDATED_AT
                );

        verify(userRepository)
                .save(user);

        verify(authSessionLogoutAllStore)
                .logoutAll(
                        USER_ID,
                        INVALIDATED_AT
                );

        InOrder order =
                inOrder(
                        interestConceptRepository,
                        userRepository,
                        authSessionLogoutAllStore
                );

        order.verify(userRepository)
                .findByIdForUpdate(USER_ID);

        order.verify(interestConceptRepository)
                .softDeleteAllByUserId(
                        USER_ID,
                        USER_ID,
                        INVALIDATED_AT
                );

        order.verify(userRepository)
                .save(user);

        // 비밀번호로 확인했으므로 소셜 재인증은 하지 않는다.
        verifyNoInteractions(socialReauthenticator);

        // 연결된 소셜 계정이 없어도 일괄 논리 삭제를 호출한다(없으면 0건).
        verify(socialAccountRepository)
                .softDeleteAllByUserId(
                        USER_ID,
                        USER_ID,
                        INVALIDATED_AT
                );

        order.verify(authSessionLogoutAllStore)
                .logoutAll(
                        USER_ID,
                        INVALIDATED_AT
                );
    }

    @Test
    @DisplayName(
            "관심 개념이 없어도 사용자를 논리 삭제하고 "
                    + "모든 인증 세션을 무효화한다"
    )
    void withdrawWithoutInterests() {
        // given
        User user = createActiveUser();

        when(userRepository.findByIdForUpdate(USER_ID))
                .thenReturn(Optional.of(user));

        when(passwordHasher.matches(
                CURRENT_PASSWORD,
                CURRENT_PASSWORD_HASH
        )).thenReturn(true);

        when(clock.instant())
                .thenReturn(INVALIDATED_AT);

        when(
                interestConceptRepository.softDeleteAllByUserId(
                        USER_ID,
                        USER_ID,
                        INVALIDATED_AT
                )
        ).thenReturn(0);

        // when & then
        assertThatCode(
                () -> withdrawUserService.withdraw(
                        USER_ID,
                        new WithdrawUserCommand(
                                CURRENT_PASSWORD
                        )
                )
        ).doesNotThrowAnyException();

        assertThat(user.isDeleted()).isTrue();
        assertThat(user.getDeletedAt()).isEqualTo(INVALIDATED_AT);

        verify(interestConceptRepository)
                .softDeleteAllByUserId(
                        USER_ID,
                        USER_ID,
                        INVALIDATED_AT
                );

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
        when(userRepository.findByIdForUpdate(USER_ID))
                .thenReturn(Optional.empty());

        // when & then
        assertBusinessError(
                () -> withdrawUserService.withdraw(
                        USER_ID,
                        new WithdrawUserCommand(
                                CURRENT_PASSWORD
                        )
                ),
                ErrorCode.USER_NOT_FOUND
        );

        verifyNoInteractions(
                interestConceptRepository,
                passwordHasher,
                authSessionLogoutAllStore,
                clock
        );
    }

    @Test
    @DisplayName("정지된 사용자는 탈퇴할 수 없다")
    void suspendedUser() {
        // given
        User user = createActiveUser();
        user.suspend();

        when(userRepository.findByIdForUpdate(USER_ID))
                .thenReturn(Optional.of(user));

        // when & then
        assertBusinessError(
                () -> withdrawUserService.withdraw(
                        USER_ID,
                        new WithdrawUserCommand(
                                CURRENT_PASSWORD
                        )
                ),
                ErrorCode.USER_NOT_ACTIVE
        );

        verify(userRepository, never())
                .save(user);

        verifyNoInteractions(
                interestConceptRepository,
                passwordHasher,
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
        String wrongPassword =
                "WrongPassword1!";

        when(userRepository.findByIdForUpdate(USER_ID))
                .thenReturn(Optional.of(user));

        when(passwordHasher.matches(
                wrongPassword,
                CURRENT_PASSWORD_HASH
        )).thenReturn(false);

        // when & then
        assertBusinessError(
                () -> withdrawUserService.withdraw(
                        USER_ID,
                        new WithdrawUserCommand(
                                wrongPassword
                        )
                ),
                ErrorCode.USER_CURRENT_PASSWORD_MISMATCH
        );

        assertThat(user.isDeleted()).isFalse();

        verify(userRepository, never())
                .save(user);

        verifyNoInteractions(
                interestConceptRepository,
                authSessionLogoutAllStore,
                clock
        );
    }

    @Test
    @DisplayName(
            "사용자 저장에 실패하면 인증 세션을 무효화하지 않는다"
    )
    void databaseFailureDoesNotInvalidateSessions() {
        // given
        User user = createActiveUser();

        when(userRepository.findByIdForUpdate(USER_ID))
                .thenReturn(Optional.of(user));

        when(passwordHasher.matches(
                CURRENT_PASSWORD,
                CURRENT_PASSWORD_HASH
        )).thenReturn(true);


        when(clock.instant())
                .thenReturn(INVALIDATED_AT);

        when(userRepository.save(user))
                .thenThrow(
                        new IllegalStateException(
                                "database failure"
                        )
                );

        // when & then
        assertThatThrownBy(
                () -> withdrawUserService.withdraw(
                        USER_ID,
                        new WithdrawUserCommand(
                                CURRENT_PASSWORD
                        )
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database failure");

        verifyNoInteractions(
                authSessionLogoutAllStore
        );
    }

    @Test
    @DisplayName(
            "인증 세션 무효화에 실패하면 예외를 전파한다"
    )
    void sessionInvalidationFailureIsPropagated() {
        // given
        User user = createActiveUser();

        when(userRepository.findByIdForUpdate(USER_ID))
                .thenReturn(Optional.of(user));

        when(passwordHasher.matches(
                CURRENT_PASSWORD,
                CURRENT_PASSWORD_HASH
        )).thenReturn(true);


        when(clock.instant())
                .thenReturn(INVALIDATED_AT);

        doThrow(
                new IllegalStateException(
                        "session invalidation failure"
                )
        ).when(
                authSessionLogoutAllStore
        ).logoutAll(
                USER_ID,
                INVALIDATED_AT
        );

        // when & then
        assertThatThrownBy(
                () -> withdrawUserService.withdraw(
                        USER_ID,
                        new WithdrawUserCommand(
                                CURRENT_PASSWORD
                        )
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("session invalidation failure");

        verify(userRepository)
                .save(user);
    }

    @Test
    @DisplayName("사용자 식별자가 없으면 탈퇴 작업을 수행하지 않는다")
    void nullUserId() {
        // when & then
        assertThatThrownBy(
                () -> withdrawUserService.withdraw(
                        null,
                        new WithdrawUserCommand(
                                CURRENT_PASSWORD
                        )
                )
        )
                .isInstanceOf(NullPointerException.class)
                .hasMessage(
                        "사용자 식별자는 필수입니다."
                );

        verifyNoInteractions(
                userRepository,
                interestConceptRepository,
                passwordHasher,
                authSessionLogoutAllStore,
                clock
        );
    }

    @Test
    @DisplayName("회원 탈퇴 명령이 없으면 탈퇴 작업을 수행하지 않는다")
    void nullCommand() {
        // when & then
        assertThatThrownBy(
                () -> withdrawUserService.withdraw(
                        USER_ID,
                        null
                )
        )
                .isInstanceOf(NullPointerException.class)
                .hasMessage(
                        "회원 탈퇴 명령은 필수입니다."
                );

        verifyNoInteractions(
                userRepository,
                interestConceptRepository,
                passwordHasher,
                authSessionLogoutAllStore,
                clock
        );
    }

    @Test
    @DisplayName("비밀번호가 없는 소셜 계정이 비밀번호로 탈퇴를 요청하면 USER_PASSWORD_NOT_SET (Google 재인증으로 탈퇴해야 한다)")
    void withdraw_socialUserWithoutPassword() {
        // given
        User socialUser =
                User.createSocial(
                        ENCRYPTED_EMAIL,
                        EMAIL_LOOKUP_HASH,
                        "구글유저",
                        3,
                        LearningLevel.BEGINNER
                );

        when(userRepository.findByIdForUpdate(USER_ID))
                .thenReturn(Optional.of(socialUser));

        // when & then
        assertThatThrownBy(
                () -> withdrawUserService.withdraw(
                        USER_ID,
                        new WithdrawUserCommand(CURRENT_PASSWORD)
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

    @Test
    @DisplayName(
            "소셜 회원은 Google 재인증 후 사용자·관심 개념·소셜 계정을 논리 삭제하고 모든 세션을 무효화한다 (#328)"
    )
    void withdraw_socialUserWithGoogleReauth() {
        // given
        User socialUser =
                User.createSocial(
                        ENCRYPTED_EMAIL,
                        EMAIL_LOOKUP_HASH,
                        "구글유저",
                        3,
                        LearningLevel.BEGINNER
                );

        when(userRepository.findByIdForUpdate(USER_ID))
                .thenReturn(Optional.of(socialUser));

        when(clock.instant())
                .thenReturn(INVALIDATED_AT);

        // when
        withdrawUserService.withdraw(
                USER_ID,
                new WithdrawUserCommand(null, "google-id-token")
        );

        // then
        assertThat(socialUser.isDeleted()).isTrue();

        // 외부 Provider 검증은 사용자 행 잠금 전에 끝낸다.
        InOrder order =
                inOrder(
                        socialReauthenticator,
                        userRepository,
                        socialAccountRepository,
                        authSessionLogoutAllStore
                );

        order.verify(socialReauthenticator)
                .verifyOwnership(USER_ID, SocialProvider.GOOGLE, "google-id-token");

        order.verify(userRepository)
                .findByIdForUpdate(USER_ID);

        order.verify(socialAccountRepository)
                .softDeleteAllByUserId(USER_ID, USER_ID, INVALIDATED_AT);

        order.verify(userRepository)
                .save(socialUser);

        order.verify(authSessionLogoutAllStore)
                .logoutAll(USER_ID, INVALIDATED_AT);

        verifyNoInteractions(passwordHasher);
    }

    @Test
    @DisplayName(
            "Google 재인증에 실패하면 사용자 행을 잠그지 않고 아무것도 삭제하지 않는다 (#328)"
    )
    void withdraw_googleReauthFails_changesNothing() {
        // given
        doThrow(new BusinessException(ErrorCode.SOCIAL_REAUTH_ACCOUNT_MISMATCH))
                .when(socialReauthenticator)
                .verifyOwnership(USER_ID, SocialProvider.GOOGLE, "other-google-id-token");

        // when & then
        assertThatThrownBy(
                () -> withdrawUserService.withdraw(
                        USER_ID,
                        new WithdrawUserCommand(null, "other-google-id-token")
                )
        )
                .isInstanceOf(BusinessException.class)
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(ErrorCode.SOCIAL_REAUTH_ACCOUNT_MISMATCH);

        verifyNoInteractions(
                userRepository,
                interestConceptRepository,
                socialAccountRepository,
                authSessionLogoutAllStore
        );
    }

    private User createActiveUser() {
        return User.create(
                ENCRYPTED_EMAIL,
                EMAIL_LOOKUP_HASH,
                CURRENT_PASSWORD_HASH,
                "learner123",
                3,
                LearningLevel.BEGINNER
        );
    }

    private void assertBusinessError(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
            ErrorCode expectedErrorCode
    ) {
        assertThatThrownBy(callable)
                .isInstanceOf(BusinessException.class)
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(expectedErrorCode);
    }
}
