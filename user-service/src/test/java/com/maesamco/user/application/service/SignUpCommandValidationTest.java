package com.maesamco.user.application.service;

import com.maesamco.user.domain.entity.LearningLevel;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SignUpCommandValidationTest {

    private static final String SAME_PASSWORD_MESSAGE =
            "비밀번호는 이메일 또는 닉네임과 동일할 수 없습니다.";

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    @DisplayName("올바른 회원가입 요청이면 Validation 오류가 없다")
    void validCommand() {
        // given
        SignUpCommand command = new SignUpCommand(
                "learner@example.com",
                "Abcd1234!",
                "김티암",
                3,
                LearningLevel.BEGINNER
        );

        // when
        Set<ConstraintViolation<SignUpCommand>> violations =
                validator.validate(command);

        // then
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("이메일의 앞뒤 공백은 제거한다")
    void trimEmail() {
        // when
        SignUpCommand command = new SignUpCommand(
                "  Learner@Example.com  ",
                "Abcd1234!",
                "김티암",
                3,
                LearningLevel.BEGINNER
        );

        // then
        assertThat(command.email())
                .isEqualTo("Learner@Example.com");

        assertThat(validator.validate(command))
                .isEmpty();
    }

    @Test
    @DisplayName("닉네임의 앞뒤 공백은 제거한다")
    void trimNickname() {
        // when
        SignUpCommand command = new SignUpCommand(
                "learner@example.com",
                "Abcd1234!",
                "  김티암  ",
                3,
                LearningLevel.BEGINNER
        );

        // then
        assertThat(command.nickname()).isEqualTo("김티암");
        assertThat(validator.validate(command)).isEmpty();
    }

    @Test
    @DisplayName("이메일 형식이 올바르지 않으면 검증에 실패한다")
    void invalidEmail() {
        // given
        SignUpCommand command = new SignUpCommand(
                "invalid-email",
                "Abcd1234!",
                "김티암",
                3,
                LearningLevel.BEGINNER
        );

        // when
        Set<ConstraintViolation<SignUpCommand>> violations =
                validator.validate(command);

        // then
        assertThat(hasViolation(violations, "email")).isTrue();
    }

    @Test
    @DisplayName("이메일이 255자를 초과하면 검증에 실패한다")
    void emailTooLong() {
        // given
        String longEmail = "a".repeat(244) + "@example.com";

        SignUpCommand command = new SignUpCommand(
                longEmail,
                "Abcd1234!",
                "김티암",
                3,
                LearningLevel.BEGINNER
        );

        // when
        Set<ConstraintViolation<SignUpCommand>> violations =
                validator.validate(command);

        // then
        assertThat(hasViolation(violations, "email")).isTrue();
    }

    @Test
    @DisplayName("비밀번호가 8자 미만이면 검증에 실패한다")
    void passwordTooShort() {
        // given
        SignUpCommand command = new SignUpCommand(
                "learner@example.com",
                "Ab1!",
                "김티암",
                3,
                LearningLevel.BEGINNER
        );

        // when
        Set<ConstraintViolation<SignUpCommand>> violations =
                validator.validate(command);

        // then
        assertThat(hasViolation(violations, "password")).isTrue();
    }

    @Test
    @DisplayName("비밀번호가 64자를 초과하면 검증에 실패한다")
    void passwordTooLong() {
        // given
        String password = "Aa1!" + "a".repeat(61);

        SignUpCommand command = new SignUpCommand(
                "learner@example.com",
                password,
                "김티암",
                3,
                LearningLevel.BEGINNER
        );

        // when
        Set<ConstraintViolation<SignUpCommand>> violations =
                validator.validate(command);

        // then
        assertThat(hasViolation(violations, "password")).isTrue();
    }

    @Test
    @DisplayName("비밀번호에 대문자가 없으면 검증에 실패한다")
    void passwordWithoutUppercase() {
        assertPasswordInvalid("abcd1234!");
    }

    @Test
    @DisplayName("비밀번호에 소문자가 없으면 검증에 실패한다")
    void passwordWithoutLowercase() {
        assertPasswordInvalid("ABCD1234!");
    }

    @Test
    @DisplayName("비밀번호에 숫자가 없으면 검증에 실패한다")
    void passwordWithoutDigit() {
        assertPasswordInvalid("Abcdefgh!");
    }

    @Test
    @DisplayName("비밀번호에 특수문자가 없으면 검증에 실패한다")
    void passwordWithoutSpecialCharacter() {
        assertPasswordInvalid("Abcd1234");
    }

    @Test
    @DisplayName("비밀번호가 이메일과 동일하면 검증에 실패한다")
    void passwordSameAsEmail() {
        // given
        String email = "Abcd1!@example.com";

        SignUpCommand command = new SignUpCommand(
                email,
                email,
                "김티암",
                3,
                LearningLevel.BEGINNER
        );

        // when
        Set<ConstraintViolation<SignUpCommand>> violations =
                validator.validate(command);

        // then
        assertThat(
                hasViolationWithMessage(
                        violations,
                        "password",
                        SAME_PASSWORD_MESSAGE
                )
        ).isTrue();
    }

    @Test
    @DisplayName("비밀번호가 이메일과 대소문자만 다르더라도 검증에 실패한다")
    void passwordSameAsEmailIgnoringCase() {
        // given
        SignUpCommand command = new SignUpCommand(
                "Abcd1!@example.com",
                "aBCD1!@EXAMPLE.COM",
                "김티암",
                3,
                LearningLevel.BEGINNER
        );

        // when
        Set<ConstraintViolation<SignUpCommand>> violations =
                validator.validate(command);

        // then
        assertThat(
                hasViolationWithMessage(
                        violations,
                        "password",
                        SAME_PASSWORD_MESSAGE
                )
        ).isTrue();
    }

    @Test
    @DisplayName("비밀번호가 닉네임과 동일하면 커스텀 검증에 실패한다")
    void passwordSameAsNickname() {
        // given
        SignUpCommand command = new SignUpCommand(
                "learner@example.com",
                "김티암",
                "김티암",
                3,
                LearningLevel.BEGINNER
        );

        // when
        Set<ConstraintViolation<SignUpCommand>> violations =
                validator.validate(command);

        // then
        assertThat(
                hasViolationWithMessage(
                        violations,
                        "password",
                        SAME_PASSWORD_MESSAGE
                )
        ).isTrue();
    }

    @Test
    @DisplayName("닉네임이 2자 미만이면 검증에 실패한다")
    void nicknameTooShort() {
        // given
        SignUpCommand command = new SignUpCommand(
                "learner@example.com",
                "Abcd1234!",
                "김",
                3,
                LearningLevel.BEGINNER
        );

        // when
        Set<ConstraintViolation<SignUpCommand>> violations =
                validator.validate(command);

        // then
        assertThat(hasViolation(violations, "nickname")).isTrue();
    }

    @Test
    @DisplayName("닉네임이 20자를 초과하면 검증에 실패한다")
    void nicknameTooLong() {
        // given
        SignUpCommand command = new SignUpCommand(
                "learner@example.com",
                "Abcd1234!",
                "가".repeat(21),
                3,
                LearningLevel.BEGINNER
        );

        // when
        Set<ConstraintViolation<SignUpCommand>> violations =
                validator.validate(command);

        // then
        assertThat(hasViolation(violations, "nickname")).isTrue();
    }

    @Test
    @DisplayName("닉네임에 허용되지 않은 문자가 포함되면 검증에 실패한다")
    void nicknameContainsInvalidCharacter() {
        // given
        SignUpCommand command = new SignUpCommand(
                "learner@example.com",
                "Abcd1234!",
                "김티암!",
                3,
                LearningLevel.BEGINNER
        );

        // when
        Set<ConstraintViolation<SignUpCommand>> violations =
                validator.validate(command);

        // then
        assertThat(hasViolation(violations, "nickname")).isTrue();
    }

    @Test
    @DisplayName("Java 경험 개월 수가 음수이면 검증에 실패한다")
    void negativeJavaExperienceMonths() {
        // given
        SignUpCommand command = new SignUpCommand(
                "learner@example.com",
                "Abcd1234!",
                "김티암",
                -1,
                LearningLevel.BEGINNER
        );

        // when
        Set<ConstraintViolation<SignUpCommand>> violations =
                validator.validate(command);

        // then
        assertThat(
                hasViolation(
                        violations,
                        "javaExperienceMonths"
                )
        ).isTrue();
    }

    @Test
    @DisplayName("학습 수준이 null이면 검증에 실패한다")
    void nullLearningLevel() {
        // given
        SignUpCommand command = new SignUpCommand(
                "learner@example.com",
                "Abcd1234!",
                "김티암",
                3,
                null
        );

        // when
        Set<ConstraintViolation<SignUpCommand>> violations =
                validator.validate(command);

        // then
        assertThat(
                hasViolation(
                        violations,
                        "learningLevel"
                )
        ).isTrue();
    }

    private void assertPasswordInvalid(String password) {
        SignUpCommand command = new SignUpCommand(
                "learner@example.com",
                password,
                "김티암",
                3,
                LearningLevel.BEGINNER
        );

        Set<ConstraintViolation<SignUpCommand>> violations =
                validator.validate(command);

        assertThat(hasViolation(violations, "password")).isTrue();
    }

    private boolean hasViolation(
            Set<ConstraintViolation<SignUpCommand>> violations,
            String propertyName
    ) {
        return violations.stream()
                .anyMatch(
                        violation ->
                                violation.getPropertyPath()
                                        .toString()
                                        .equals(propertyName)
                );
    }

    private boolean hasViolationWithMessage(
            Set<ConstraintViolation<SignUpCommand>> violations,
            String propertyName,
            String message
    ) {
        return violations.stream()
                .anyMatch(
                        violation ->
                                violation.getPropertyPath()
                                        .toString()
                                        .equals(propertyName)
                                        && violation.getMessage()
                                        .equals(message)
                );
    }
}
