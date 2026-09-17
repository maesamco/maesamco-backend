package com.maesamco.user.application.service;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WithdrawUserCommand의 입력 Validation과
 * 민감정보 보호 정책을 검증합니다.
 */
class WithdrawUserCommandValidationTest {

    private static final String CURRENT_PASSWORD =
            "Abcd1234!";

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
    @DisplayName("올바른 회원 탈퇴 요청은 Validation을 통과한다")
    void validCommand() {
        // given
        WithdrawUserCommand command =
                new WithdrawUserCommand(
                        CURRENT_PASSWORD
                );

        // when
        Set<ConstraintViolation<WithdrawUserCommand>> violations =
                validator.validate(command);

        // then
        assertThat(violations).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "   "
    })
    @DisplayName("현재 비밀번호가 없거나 공백이면 Validation에 실패한다")
    void missingCurrentPassword(
            String currentPassword
    ) {
        // given
        WithdrawUserCommand command =
                new WithdrawUserCommand(
                        currentPassword
                );

        // when
        Set<ConstraintViolation<WithdrawUserCommand>> violations =
                validator.validate(command);

        // then
        assertThat(
                hasViolation(
                        violations,
                        "currentPassword"
                )
        ).isTrue();
    }

    @Test
    @DisplayName("현재 비밀번호가 64자를 초과하면 Validation에 실패한다")
    void currentPasswordTooLong() {
        // given
        WithdrawUserCommand command =
                new WithdrawUserCommand(
                        "a".repeat(65)
                );

        // when
        Set<ConstraintViolation<WithdrawUserCommand>> violations =
                validator.validate(command);

        // then
        assertThat(
                hasViolationWithMessage(
                        violations,
                        "currentPassword",
                        "현재 비밀번호는 64자 이하여야 합니다."
                )
        ).isTrue();
    }

    @Test
    @DisplayName("현재 비밀번호에는 신규 비밀번호 복잡도 정책을 적용하지 않는다")
    void currentPasswordDoesNotApplyNewPasswordPolicy() {
        // given
        WithdrawUserCommand command =
                new WithdrawUserCommand(
                        "a"
                );

        // when
        Set<ConstraintViolation<WithdrawUserCommand>> violations =
                validator.validate(command);

        // then
        assertThat(
                hasViolation(
                        violations,
                        "currentPassword"
                )
        ).isFalse();
    }

    @Test
    @DisplayName("toString은 현재 비밀번호를 노출하지 않는다")
    void toStringMasksPassword() {
        // given
        WithdrawUserCommand command =
                new WithdrawUserCommand(
                        CURRENT_PASSWORD
                );

        // when
        String value = command.toString();

        // then
        assertThat(value)
                .contains("[PROTECTED]")
                .doesNotContain(CURRENT_PASSWORD);
    }

    private boolean hasViolation(
            Set<ConstraintViolation<WithdrawUserCommand>> violations,
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
            Set<ConstraintViolation<WithdrawUserCommand>> violations,
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
