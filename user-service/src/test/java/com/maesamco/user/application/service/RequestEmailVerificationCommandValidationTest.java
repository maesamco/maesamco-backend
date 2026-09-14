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
 * {@link RequestEmailVerificationCommand}의
 * Bean Validation 규칙과 입력 전처리를 검증합니다.
 */
class RequestEmailVerificationCommandValidationTest {

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
    @DisplayName("올바른 이메일 인증 요청이면 Validation 오류가 없다")
    void validCommand() {
        // given
        RequestEmailVerificationCommand command =
                new RequestEmailVerificationCommand(
                        "learner@example.com"
                );

        // when
        Set<ConstraintViolation<RequestEmailVerificationCommand>>
                violations =
                validator.validate(command);

        // then
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("이메일의 앞뒤 공백은 제거한다")
    void trimEmail() {
        // when
        RequestEmailVerificationCommand command =
                new RequestEmailVerificationCommand(
                        "  Learner@Example.com  "
                );

        // then
        assertThat(command.email())
                .isEqualTo("Learner@Example.com");

        assertThat(
                validator.validate(command)
        ).isEmpty();
    }

    @Test
    @DisplayName("이메일이 비어 있으면 검증에 실패한다")
    void blankEmail() {
        // given
        RequestEmailVerificationCommand command =
                new RequestEmailVerificationCommand(
                        " "
                );

        // when
        Set<ConstraintViolation<RequestEmailVerificationCommand>>
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
    @DisplayName("이메일이 null이면 검증에 실패한다")
    void nullEmail() {
        // given
        RequestEmailVerificationCommand command =
                new RequestEmailVerificationCommand(
                        null
                );

        // when
        Set<ConstraintViolation<RequestEmailVerificationCommand>>
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
        RequestEmailVerificationCommand command =
                new RequestEmailVerificationCommand(
                        "invalid-email"
                );

        // when
        Set<ConstraintViolation<RequestEmailVerificationCommand>>
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

        RequestEmailVerificationCommand command =
                new RequestEmailVerificationCommand(
                        longEmail
                );

        // when
        Set<ConstraintViolation<RequestEmailVerificationCommand>>
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

    /**
     * 특정 필드에 Validation 오류가 존재하는지 확인합니다.
     */
    private boolean hasViolation(
            Set<ConstraintViolation<RequestEmailVerificationCommand>>
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
