package com.maesamco.user.application.service;

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

/**
 * {@link ConfirmEmailVerificationCommand}의
 * Bean Validation 규칙과 입력 전처리를 검증합니다.
 */
class ConfirmEmailVerificationCommandValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    /**
     * 테스트 전체에서 사용할 Validator를 생성합니다.
     */
    @BeforeAll
    static void setUpValidator() {
        validatorFactory =
                Validation.buildDefaultValidatorFactory();

        validator =
                validatorFactory.getValidator();
    }

    /**
     * 테스트 종료 후 ValidatorFactory 자원을 정리합니다.
     */
    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    @DisplayName("올바른 이메일과 인증 코드이면 Validation 오류가 없다")
    void validCommand() {
        // given
        ConfirmEmailVerificationCommand command =
                new ConfirmEmailVerificationCommand(
                        "learner@example.com",
                        "123456"
                );

        // when
        Set<ConstraintViolation<ConfirmEmailVerificationCommand>>
                violations =
                validator.validate(command);

        // then
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("이메일과 인증 코드의 앞뒤 공백은 제거한다")
    void trimsEmailAndVerificationCode() {
        // when
        ConfirmEmailVerificationCommand command =
                new ConfirmEmailVerificationCommand(
                        "  Learner@Example.com  ",
                        "  001234  "
                );

        // then
        assertThat(command.email())
                .isEqualTo("Learner@Example.com");

        assertThat(command.verificationCode())
                .isEqualTo("001234");

        assertThat(
                validator.validate(command)
        ).isEmpty();
    }

    @Test
    @DisplayName("이메일이 비어 있으면 검증에 실패한다")
    void blankEmail() {
        // given
        ConfirmEmailVerificationCommand command =
                new ConfirmEmailVerificationCommand(
                        " ",
                        "123456"
                );

        // when
        Set<ConstraintViolation<ConfirmEmailVerificationCommand>>
                violations =
                validator.validate(command);

        // then
        assertThat(
                hasViolation(
                        violations,
                        "email"
                )
        ).isTrue();
    }

    @Test
    @DisplayName("이메일 형식이 올바르지 않으면 검증에 실패한다")
    void invalidEmail() {
        // given
        ConfirmEmailVerificationCommand command =
                new ConfirmEmailVerificationCommand(
                        "invalid-email",
                        "123456"
                );

        // when
        Set<ConstraintViolation<ConfirmEmailVerificationCommand>>
                violations =
                validator.validate(command);

        // then
        assertThat(
                hasViolation(
                        violations,
                        "email"
                )
        ).isTrue();
    }

    @Test
    @DisplayName("이메일이 255자를 초과하면 검증에 실패한다")
    void emailTooLong() {
        // given
        String longEmail =
                "a".repeat(244) + "@example.com";

        ConfirmEmailVerificationCommand command =
                new ConfirmEmailVerificationCommand(
                        longEmail,
                        "123456"
                );

        // when
        Set<ConstraintViolation<ConfirmEmailVerificationCommand>>
                violations =
                validator.validate(command);

        // then
        assertThat(
                hasViolation(
                        violations,
                        "email"
                )
        ).isTrue();
    }

    @Test
    @DisplayName("인증 코드가 비어 있으면 검증에 실패한다")
    void blankVerificationCode() {
        // given
        ConfirmEmailVerificationCommand command =
                new ConfirmEmailVerificationCommand(
                        "learner@example.com",
                        " "
                );

        // when
        Set<ConstraintViolation<ConfirmEmailVerificationCommand>>
                violations =
                validator.validate(command);

        // then
        assertThat(
                hasViolation(
                        violations,
                        "verificationCode"
                )
        ).isTrue();
    }

    @Test
    @DisplayName("인증 코드가 6자리보다 짧으면 검증에 실패한다")
    void verificationCodeTooShort() {
        // given
        ConfirmEmailVerificationCommand command =
                new ConfirmEmailVerificationCommand(
                        "learner@example.com",
                        "12345"
                );

        // when
        Set<ConstraintViolation<ConfirmEmailVerificationCommand>>
                violations =
                validator.validate(command);

        // then
        assertThat(
                hasViolation(
                        violations,
                        "verificationCode"
                )
        ).isTrue();
    }

    @Test
    @DisplayName("인증 코드가 6자리를 초과하면 검증에 실패한다")
    void verificationCodeTooLong() {
        // given
        ConfirmEmailVerificationCommand command =
                new ConfirmEmailVerificationCommand(
                        "learner@example.com",
                        "1234567"
                );

        // when
        Set<ConstraintViolation<ConfirmEmailVerificationCommand>>
                violations =
                validator.validate(command);

        // then
        assertThat(
                hasViolation(
                        violations,
                        "verificationCode"
                )
        ).isTrue();
    }

    @Test
    @DisplayName("인증 코드에 숫자가 아닌 문자가 포함되면 검증에 실패한다")
    void verificationCodeContainsNonDigit() {
        // given
        ConfirmEmailVerificationCommand command =
                new ConfirmEmailVerificationCommand(
                        "learner@example.com",
                        "12A456"
                );

        // when
        Set<ConstraintViolation<ConfirmEmailVerificationCommand>>
                violations =
                validator.validate(command);

        // then
        assertThat(
                hasViolation(
                        violations,
                        "verificationCode"
                )
        ).isTrue();
    }

    /**
     * 특정 필드에 Validation 오류가 존재하는지 확인합니다.
     */
    private boolean hasViolation(
            Set<ConstraintViolation<ConfirmEmailVerificationCommand>>
                    violations,
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
}
