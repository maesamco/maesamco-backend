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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 로그인 사용자 정보 수정 서비스의 단위 테스트입니다.
 */
@ExtendWith(MockitoExtension.class)
class UpdateMyProfileServiceTest {

    private static final UUID USER_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
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
    private UpdateMyProfileService updateMyProfileService;

    @Test
    @DisplayName(
            "정상 요청이면 사용자 프로필을 수정하고 변경 결과를 반환한다"
    )
    void updateMyProfile() {
        // given
        UpdateMyProfileCommand command =
                createCommand("새닉네임");

        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(user));

        when(user.getStatus())
                .thenReturn(UserStatus.ACTIVE);

        when(user.getNickname())
                .thenReturn(
                        "기존닉네임",
                        "새닉네임"
                );

        when(
                userRepository.existsByNicknameIgnoreCase(
                        "새닉네임"
                )
        ).thenReturn(false);

        when(userRepository.save(user))
                .thenReturn(user);

        stubUpdatedUserResult();

        // when
        GetMyProfileResult result =
                updateMyProfileService.updateMyProfile(
                        USER_ID,
                        command
                );

        // then
        verify(user)
                .updateProfile(
                        "새닉네임",
                        6,
                        LearningLevel.BASIC
                );

        verify(userRepository)
                .save(user);

        verify(emailCipher)
                .decrypt(ENCRYPTED_EMAIL);

        assertThat(result.userId())
                .isEqualTo(USER_ID);

        assertThat(result.email())
                .isEqualTo(EMAIL);

        assertThat(result.nickname())
                .isEqualTo("새닉네임");

        assertThat(result.role())
                .isEqualTo(UserRole.USER);

        assertThat(result.status())
                .isEqualTo(UserStatus.ACTIVE);

        assertThat(result.learningLevel())
                .isEqualTo(LearningLevel.BASIC);

        assertThat(result.javaExperienceMonths())
                .isEqualTo(6);

        assertThat(result.createdAt())
                .isEqualTo(CREATED_AT);
    }

    @Test
    @DisplayName(
            "현재 닉네임을 그대로 사용하면 중복 조회 없이 수정할 수 있다"
    )
    void updateMyProfile_allowsCurrentNickname() {
        // given
        UpdateMyProfileCommand command =
                createCommand("현재닉네임");

        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(user));

        when(user.getStatus())
                .thenReturn(UserStatus.ACTIVE);

        when(user.getNickname())
                .thenReturn("현재닉네임");

        when(userRepository.save(user))
                .thenReturn(user);

        stubUpdatedUserResult();

        // when
        GetMyProfileResult result =
                updateMyProfileService.updateMyProfile(
                        USER_ID,
                        command
                );

        // then
        verify(
                userRepository,
                never()
        ).existsByNicknameIgnoreCase(
                "현재닉네임"
        );

        verify(user)
                .updateProfile(
                        "현재닉네임",
                        6,
                        LearningLevel.BASIC
                );

        assertThat(result.nickname())
                .isEqualTo("현재닉네임");
    }

    @Test
    @DisplayName(
            "다른 사용자가 닉네임을 사용 중이면 "
                    + "USER_DUPLICATE_NICKNAME을 반환한다"
    )
    void updateMyProfile_rejectsDuplicateNickname() {
        // given
        UpdateMyProfileCommand command =
                createCommand("중복닉네임");

        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(user));

        when(user.getStatus())
                .thenReturn(UserStatus.ACTIVE);

        when(user.getNickname())
                .thenReturn("기존닉네임");

        when(
                userRepository.existsByNicknameIgnoreCase(
                        "중복닉네임"
                )
        ).thenReturn(true);

        // when & then
        assertThatThrownBy(
                () -> updateMyProfileService.updateMyProfile(
                        USER_ID,
                        command
                )
        )
                .isInstanceOf(BusinessException.class)
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(
                        ErrorCode.USER_DUPLICATE_NICKNAME
                );

        verify(
                user,
                never()
        ).updateProfile(
                command.nickname(),
                command.javaExperienceMonths(),
                command.learningLevel()
        );

        verify(
                userRepository,
                never()
        ).save(user);

        verifyNoInteractions(emailCipher);
    }

    @Test
    @DisplayName(
            "사용자가 존재하지 않으면 USER_NOT_FOUND를 반환한다"
    )
    void updateMyProfile_rejectsMissingUser() {
        // given
        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(
                () -> updateMyProfileService.updateMyProfile(
                        USER_ID,
                        createCommand("새닉네임")
                )
        )
                .isInstanceOf(BusinessException.class)
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verifyNoInteractions(
                user,
                emailCipher
        );
    }

    @Test
    @DisplayName(
            "활성 상태가 아닌 사용자는 USER_NOT_ACTIVE를 반환한다"
    )
    void updateMyProfile_rejectsInactiveUser() {
        // given
        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(user));

        when(user.getStatus())
                .thenReturn(UserStatus.SUSPENDED);

        // when & then
        assertThatThrownBy(
                () -> updateMyProfileService.updateMyProfile(
                        USER_ID,
                        createCommand("새닉네임")
                )
        )
                .isInstanceOf(BusinessException.class)
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(ErrorCode.USER_NOT_ACTIVE);

        verify(
                userRepository,
                never()
        ).existsByNicknameIgnoreCase(
                "새닉네임"
        );

        verify(
                userRepository,
                never()
        ).save(user);

        verifyNoInteractions(emailCipher);
    }

    @Test
    @DisplayName(
            "사용자 식별자가 null이면 수정 작업을 수행하지 않는다"
    )
    void updateMyProfile_rejectsNullUserId() {
        assertThatNullPointerException()
                .isThrownBy(
                        () -> updateMyProfileService
                                .updateMyProfile(
                                        null,
                                        createCommand(
                                                "새닉네임"
                                        )
                                )
                )
                .withMessage(
                        "사용자 식별자는 필수입니다."
                );

        verifyNoInteractions(
                userRepository,
                user,
                emailCipher
        );
    }

    @Test
    @DisplayName(
            "수정 명령이 null이면 수정 작업을 수행하지 않는다"
    )
    void updateMyProfile_rejectsNullCommand() {
        assertThatNullPointerException()
                .isThrownBy(
                        () -> updateMyProfileService
                                .updateMyProfile(
                                        USER_ID,
                                        null
                                )
                )
                .withMessage(
                        "사용자 정보 수정 명령은 필수입니다."
                );

        verifyNoInteractions(
                userRepository,
                user,
                emailCipher
        );
    }

    private UpdateMyProfileCommand createCommand(
            String nickname
    ) {
        return new UpdateMyProfileCommand(
                nickname,
                LearningLevel.BASIC,
                6
        );
    }

    private void stubUpdatedUserResult() {
        when(user.getId())
                .thenReturn(USER_ID);

        when(user.getEncryptedEmail())
                .thenReturn(ENCRYPTED_EMAIL);

        when(emailCipher.decrypt(ENCRYPTED_EMAIL))
                .thenReturn(EMAIL);

        when(user.getRole())
                .thenReturn(UserRole.USER);

        when(user.getLearningLevel())
                .thenReturn(LearningLevel.BASIC);

        when(user.getJavaExperienceMonths())
                .thenReturn(6);

        when(user.getCreatedAt())
                .thenReturn(CREATED_AT);
    }
}
