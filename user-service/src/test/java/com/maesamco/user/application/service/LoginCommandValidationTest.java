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
 * LoginCommand의 API 입력 Validation 정책을 검증합니다.
 *
 * <p>로그인에서는 회원가입과 달리 비밀번호 복잡도나
 * 최소 길이를 다시 검증하지 않습니다.</p>
 *
 * <p>다만 과도하게 긴 비밀번호 입력으로 인한 불필요한
 * 비밀번호 해시 비교 비용을 제한하기 위해 최대 64자까지만 허용합니다.</p>
 */
class LoginCommandValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory =
                Validation.buildDefaultValidatorFactory();

        validator =
                validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    @DisplayName("올바른 로그인 요청이면 Validation 오류가 없다")
    void validCommand() {
        // given
        LoginCommand command = new LoginCommand(
                "learner@example.com",
                "Abcd1234!"
        );

        // when
        Set<ConstraintViolation<LoginCommand>> violations =
                validator.validate(command);

        // then
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("이메일의 앞뒤 공백은 제거한다")
    void trimEmail() {
        // when
        LoginCommand command = new LoginCommand(
                "  Learner@Example.com  ",
                "Abcd1234!"
        );

        // then
        assertThat(command.email())
                .isEqualTo("Learner@Example.com");

        assertThat(
                validator.validate(command)
        ).isEmpty();
    }

    @Test
    @DisplayName("비밀번호가 64자이면 로그인 Validation을 통과한다")
    void passwordLength64() {
        // given
        LoginCommand command = new LoginCommand(
                "learner@example.com",
                "a".repeat(64)
        );

        // when
        Set<ConstraintViolation<LoginCommand>> violations =
                validator.validate(command);

        // then
        assertThat(
                hasViolation(
                        violations,
                        "password"
                )
        ).isFalse();
    }

    @Test
    @DisplayName("비밀번호가 64자를 초과하면 Validation에 실패한다")
    void passwordTooLong() {
        // given
        LoginCommand command = new LoginCommand(
                "learner@example.com",
                "a".repeat(65)
        );

        // when
        Set<ConstraintViolation<LoginCommand>> violations =
                validator.validate(command);

        // then
        assertThat(
                hasViolationWithMessage(
                        violations,
                        "password",
                        "비밀번호는 64자 이하여야 합니다."
                )
        ).isTrue();
    }

    @Test
    @DisplayName("로그인에서는 비밀번호 최소 길이와 복잡도를 재검증하지 않는다")
    void shortPasswordIsAllowedForLogin() {
        // given
        LoginCommand command = new LoginCommand(
                "learner@example.com",
                "a"
        );

        // when
        Set<ConstraintViolation<LoginCommand>> violations =
                validator.validate(command);

        // then
        assertThat(
                hasViolation(
                        violations,
                        "password"
                )
        ).isFalse();
    }

    @Test
    @DisplayName("비밀번호가 공백이면 Validation에 실패한다")
    void blankPassword() {
        // given
        LoginCommand command = new LoginCommand(
                "learner@example.com",
                "   "
        );

        // when
        Set<ConstraintViolation<LoginCommand>> violations =
                validator.validate(command);

        // then
        assertThat(
                hasViolation(
                        violations,
                        "password"
                )
        ).isTrue();
    }

    private boolean hasViolation(
            Set<ConstraintViolation<LoginCommand>> violations,
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
            Set<ConstraintViolation<LoginCommand>> violations,
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
