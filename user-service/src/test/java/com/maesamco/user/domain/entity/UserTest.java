package com.maesamco.user.domain.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;

/**
 * User 도메인의 생성 규칙과 상태 변경을 검증하는 단위 테스트입니다.
 */
class UserTest {

    private static final String ENCRYPTED_EMAIL =
            "encrypted-email-value";

    private static final String EMAIL_LOOKUP_HASH =
            "a".repeat(64);

    private static final String PASSWORD_HASH =
            "argon2id-password-hash";

    @Test
    @DisplayName("일반 사용자를 기본 권한과 활성 상태로 생성한다")
    void createUserWithDefaultRoleAndStatus() {
        // given
        String nickname = "매삼코";
        int javaExperienceMonths = 3;
        LearningLevel learningLevel = LearningLevel.BEGINNER;

        // when
        User user = User.create(
                ENCRYPTED_EMAIL,
                EMAIL_LOOKUP_HASH,
                PASSWORD_HASH,
                nickname,
                javaExperienceMonths,
                learningLevel
        );

        // then
        assertThat(user.getId()).isNotNull();
        assertThat(user.getEncryptedEmail())
                .isEqualTo(ENCRYPTED_EMAIL);
        assertThat(user.getEmailLookupHash())
                .isEqualTo(EMAIL_LOOKUP_HASH);
        assertThat(user.getPasswordHash())
                .isEqualTo(PASSWORD_HASH);
        assertThat(user.getNickname()).isEqualTo(nickname);
        assertThat(user.getRole()).isEqualTo(UserRole.USER);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getJavaExperienceMonths())
                .isEqualTo(javaExperienceMonths);
        assertThat(user.getLearningLevel())
                .isEqualTo(learningLevel);
    }

    @Test
    @DisplayName("이메일 조회 해시가 64자가 아니면 생성할 수 없다")
    void rejectInvalidEmailLookupHash() {
        // given
        String invalidEmailLookupHash = "a".repeat(63);

        // when & then
        assertThatThrownBy(() -> User.create(
                ENCRYPTED_EMAIL,
                invalidEmailLookupHash,
                PASSWORD_HASH,
                "매삼코",
                3,
                LearningLevel.BEGINNER
        ))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> {
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
                            assertThat(exception.getMessage())
                                    .isEqualTo(
                                            "이메일 조회 해시는 64자여야 합니다."
                                    );
                        }
                );
    }

    @Test
    @DisplayName("Java 경험 개월 수가 음수이면 생성할 수 없다")
    void rejectNegativeJavaExperienceMonths() {
        // when & then
        assertThatThrownBy(() -> User.create(
                ENCRYPTED_EMAIL,
                EMAIL_LOOKUP_HASH,
                PASSWORD_HASH,
                "매삼코",
                -1,
                LearningLevel.BEGINNER
        ))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> {
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
                            assertThat(exception.getMessage())
                                    .isEqualTo(
                                            "Java 학습 개월 수는 0 이상이어야 합니다."
                                    );
                        }
                );
    }

    @Test
    @DisplayName("닉네임이 50자를 초과하면 사용자를 생성할 수 없다")
    void rejectTooLongNicknameOnCreate() {
        // given
        String tooLongNickname = "가".repeat(51);

        // when & then
        assertThatThrownBy(() -> User.create(
                ENCRYPTED_EMAIL,
                EMAIL_LOOKUP_HASH,
                PASSWORD_HASH,
                tooLongNickname,
                3,
                LearningLevel.BEGINNER
        ))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> {
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
                            assertThat(exception.getMessage())
                                    .isEqualTo("닉네임은 50자 이하여야 합니다.");
                        }
                );
    }

    @Test
    @DisplayName("닉네임이 50자를 초과하면 프로필을 변경할 수 없다")
    void rejectTooLongNicknameOnUpdate() {
        // given
        User user = createDefaultUser();
        String originalNickname = user.getNickname();
        String tooLongNickname = "가".repeat(51);

        // when & then
        assertThatThrownBy(() -> user.updateProfile(
                tooLongNickname,
                12,
                LearningLevel.BASIC
        ))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> {
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
                            assertThat(exception.getMessage())
                                    .isEqualTo("닉네임은 50자 이하여야 합니다.");
                        }
                );
        assertThat(user.getNickname()).isEqualTo(originalNickname);
    }

    @Test
    @DisplayName("닉네임과 Java 경험 개월 수 및 학습 수준을 변경한다")
    void updateProfile() {
        // given
        User user = createDefaultUser();

        // when
        user.updateProfile(
                "새로운닉네임",
                12,
                LearningLevel.BASIC
        );

        // then
        assertThat(user.getNickname())
                .isEqualTo("새로운닉네임");
        assertThat(user.getJavaExperienceMonths())
                .isEqualTo(12);
        assertThat(user.getLearningLevel())
                .isEqualTo(LearningLevel.BASIC);
    }

    @Test
    @DisplayName("비밀번호 해시를 변경한다")
    void changePasswordHash() {
        // given
        User user = createDefaultUser();
        String newPasswordHash = "new-argon2id-password-hash";

        // when
        user.changePasswordHash(newPasswordHash);

        // then
        assertThat(user.getPasswordHash())
                .isEqualTo(newPasswordHash);
    }

    @Test
    @DisplayName("계정을 정지하고 다시 활성화한다")
    void suspendAndActivateUser() {
        // given
        User user = createDefaultUser();

        // when
        user.suspend();

        // then
        assertThat(user.getStatus())
                .isEqualTo(UserStatus.SUSPENDED);

        // when
        user.activate();

        // then
        assertThat(user.getStatus())
                .isEqualTo(UserStatus.ACTIVE);
    }

    /**
     * 테스트에서 공통으로 사용할 기본 사용자를 생성합니다.
     */
    private User createDefaultUser() {
        return User.create(
                ENCRYPTED_EMAIL,
                EMAIL_LOOKUP_HASH,
                PASSWORD_HASH,
                "매삼코",
                3,
                LearningLevel.BEGINNER
        );
    }

    @Test
    @DisplayName("학습 수준이 null이면 사용자를 생성할 수 없다")
    void rejectNullLearningLevel() {
        // when & then
        assertThatThrownBy(() -> User.create(
                ENCRYPTED_EMAIL,
                EMAIL_LOOKUP_HASH,
                PASSWORD_HASH,
                "매삼코",
                3,
                null
        ))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> {
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(
                                            ErrorCode.INVALID_INPUT_VALUE
                                    );
                            assertThat(exception.getMessage())
                                    .isEqualTo(
                                            "학습 수준은 필수입니다."
                                    );
                        }
                );
    }
}
