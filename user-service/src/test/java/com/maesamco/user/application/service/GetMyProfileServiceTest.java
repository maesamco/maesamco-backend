package com.maesamco.user.application.service;

import com.maesamco.user.application.port.EmailCipher;
import com.maesamco.user.domain.entity.LearningLevel;
import com.maesamco.user.domain.entity.User;
import com.maesamco.user.domain.entity.UserRole;
import com.maesamco.user.domain.entity.UserStatus;
import com.maesamco.user.domain.repository.UserRepository;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 로그인 사용자 정보 조회 서비스의 단위 테스트입니다.
 */
@ExtendWith(MockitoExtension.class)
class GetMyProfileServiceTest {

    private static final UUID USER_ID =
            UUID.fromString(
                    "12345678-1234-5678-1234-123456789123"
            );

    private static final String ENCRYPTED_EMAIL =
            "encrypted-email";

    private static final String EMAIL =
            "learner@example.com";

    private static final Instant CREATED_AT =
            Instant.parse("2026-09-15T01:00:00Z");

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailCipher emailCipher;

    @Mock
    private User user;

    @InjectMocks
    private GetMyProfileService getMyProfileService;

    @Test
    @DisplayName(
            "사용자가 존재하면 이메일을 복호화하고 기본 정보를 반환한다"
    )
    void getMyProfile_returnsUserProfile() {
        // given
        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(user));

        when(user.getId())
                .thenReturn(USER_ID);

        when(user.getEncryptedEmail())
                .thenReturn(ENCRYPTED_EMAIL);

        when(emailCipher.decrypt(ENCRYPTED_EMAIL))
                .thenReturn(EMAIL);

        when(user.getNickname())
                .thenReturn("김티암");

        when(user.getRole())
                .thenReturn(UserRole.USER);

        when(user.getStatus())
                .thenReturn(UserStatus.ACTIVE);

        when(user.getLearningLevel())
                .thenReturn(LearningLevel.BEGINNER);

        when(user.getJavaExperienceMonths())
                .thenReturn(3);

        when(user.getCreatedAt())
                .thenReturn(CREATED_AT);

        // when
        GetMyProfileResult result =
                getMyProfileService.getMyProfile(USER_ID);

        // then
        assertThat(result.userId())
                .isEqualTo(USER_ID);

        assertThat(result.email())
                .isEqualTo(EMAIL);

        assertThat(result.nickname())
                .isEqualTo("김티암");

        assertThat(result.role())
                .isEqualTo(UserRole.USER);

        assertThat(result.status())
                .isEqualTo(UserStatus.ACTIVE);

        assertThat(result.learningLevel())
                .isEqualTo(LearningLevel.BEGINNER);

        assertThat(result.javaExperienceMonths())
                .isEqualTo(3);

        assertThat(result.createdAt())
                .isEqualTo(CREATED_AT);

        verify(userRepository)
                .findById(USER_ID);

        verify(emailCipher)
                .decrypt(ENCRYPTED_EMAIL);
    }

    @Test
    @DisplayName(
            "사용자가 존재하지 않으면 USER_NOT_FOUND를 반환한다"
    )
    void getMyProfile_throwsWhenUserDoesNotExist() {
        // given
        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(
                () -> getMyProfileService.getMyProfile(USER_ID)
        )
                .isInstanceOf(BusinessException.class)
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verifyNoInteractions(emailCipher);
    }

    @Test
    @DisplayName(
            "사용자 식별자가 null이면 조회를 수행하지 않는다"
    )
    void getMyProfile_rejectsNullUserId() {
        assertThatNullPointerException()
                .isThrownBy(
                        () -> getMyProfileService
                                .getMyProfile(null)
                )
                .withMessage(
                        "사용자 식별자는 필수입니다."
                );

        verifyNoInteractions(
                userRepository,
                emailCipher
        );
    }

    @Test
    @DisplayName(
            "SUSPENDED 사용자도 자신의 프로필과 정지 상태를 조회할 수 있다"
    )
    void getMyProfile_returnsSuspendedUserProfile() {
        // given
        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(user));

        when(user.getId())
                .thenReturn(USER_ID);

        when(user.getEncryptedEmail())
                .thenReturn(ENCRYPTED_EMAIL);

        when(emailCipher.decrypt(ENCRYPTED_EMAIL))
                .thenReturn(EMAIL);

        when(user.getNickname())
                .thenReturn("김티암");

        when(user.getRole())
                .thenReturn(UserRole.USER);

        when(user.getStatus())
                .thenReturn(UserStatus.SUSPENDED);

        when(user.getLearningLevel())
                .thenReturn(LearningLevel.BEGINNER);

        when(user.getJavaExperienceMonths())
                .thenReturn(3);

        when(user.getCreatedAt())
                .thenReturn(CREATED_AT);

        // when
        GetMyProfileResult result =
                getMyProfileService.getMyProfile(USER_ID);

        // then
        assertThat(result.status())
                .isEqualTo(UserStatus.SUSPENDED);

        assertThat(result.email())
                .isEqualTo(EMAIL);

        verify(userRepository)
                .findById(USER_ID);

        verify(emailCipher)
                .decrypt(ENCRYPTED_EMAIL);
    }

    @Test
    @DisplayName(
            "이메일 복호화에 실패하면 예외를 그대로 전파한다"
    )
    void getMyProfile_propagatesEmailDecryptionFailure() {
        // given
        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(user));

        when(user.getEncryptedEmail())
                .thenReturn(ENCRYPTED_EMAIL);

        when(emailCipher.decrypt(ENCRYPTED_EMAIL))
                .thenThrow(
                        new IllegalStateException(
                                "이메일 복호화에 실패했습니다."
                        )
                );

        // when & then
        assertThatThrownBy(
                () -> getMyProfileService.getMyProfile(USER_ID)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "이메일 복호화에 실패했습니다."
                );

        verify(userRepository)
                .findById(USER_ID);

        verify(emailCipher)
                .decrypt(ENCRYPTED_EMAIL);
    }
}
