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
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ChangePasswordCommand의 입력 Validation과
 * 민감정보 보호 정책을 검증합니다.
 */
class ChangePasswordCommandValidationTest {

    private static final String CURRENT_PASSWORD =
            "Abcd1234!";

    private static final String NEW_PASSWORD =
            "NewAbcd1234!";

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
    @DisplayName("올바른 비밀번호 변경 요청은 Validation을 통과한다")
    void validCommand() {
        // given
        ChangePasswordCommand command =
                new ChangePasswordCommand(
                        CURRENT_PASSWORD,
                        NEW_PASSWORD
                );

        // when
        Set<ConstraintViolation<ChangePasswordCommand>> violations =
                validator.validate(command);

        // then
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("현재 비밀번호가 없으면 Validation에 실패한다")
    void missingCurrentPassword() {
        // given
        ChangePasswordCommand command =
                new ChangePasswordCommand(
                        null,
                        NEW_PASSWORD
                );

        // when
        Set<ConstraintViolation<ChangePasswordCommand>> violations =
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
        ChangePasswordCommand command =
                new ChangePasswordCommand(
                        "a".repeat(65),
                        NEW_PASSWORD
                );

        // when
        Set<ConstraintViolation<ChangePasswordCommand>> violations =
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
        ChangePasswordCommand command =
                new ChangePasswordCommand(
                        "a",
                        NEW_PASSWORD
                );

        // when
        Set<ConstraintViolation<ChangePasswordCommand>> violations =
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
    @DisplayName("새 비밀번호가 없으면 Validation에 실패한다")
    void missingNewPassword() {
        // given
        ChangePasswordCommand command =
                new ChangePasswordCommand(
                        CURRENT_PASSWORD,
                        null
                );

        // when
        Set<ConstraintViolation<ChangePasswordCommand>> violations =
                validator.validate(command);

        // then
        assertThat(
                hasViolation(
                        violations,
                        "newPassword"
                )
        ).isTrue();
    }

    @Test
    @DisplayName("새 비밀번호가 8자 미만이면 Validation에 실패한다")
    void newPasswordTooShort() {
        // given
        ChangePasswordCommand command =
                new ChangePasswordCommand(
                        CURRENT_PASSWORD,
                        "Abc12!"
                );

        // when
        Set<ConstraintViolation<ChangePasswordCommand>> violations =
                validator.validate(command);

        // then
        assertThat(
                hasViolationWithMessage(
                        violations,
                        "newPassword",
                        "새 비밀번호는 8자 이상 64자 이하여야 합니다."
                )
        ).isTrue();
    }

    @Test
    @DisplayName("새 비밀번호가 64자를 초과하면 Validation에 실패한다")
    void newPasswordTooLong() {
        // given
        ChangePasswordCommand command =
                new ChangePasswordCommand(
                        CURRENT_PASSWORD,
                        "A1!" + "a".repeat(62)
                );

        // when
        Set<ConstraintViolation<ChangePasswordCommand>> violations =
                validator.validate(command);

        // then
        assertThat(
                hasViolationWithMessage(
                        violations,
                        "newPassword",
                        "새 비밀번호는 8자 이상 64자 이하여야 합니다."
                )
        ).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "abcd1234!",
            "ABCD1234!",
            "Abcdefgh!",
            "Abcd1234"
    })
    @DisplayName("새 비밀번호가 복잡도 정책을 만족하지 않으면 Validation에 실패한다")
    void invalidNewPasswordComplexity(
            String invalidPassword
    ) {
        // given
        ChangePasswordCommand command =
                new ChangePasswordCommand(
                        CURRENT_PASSWORD,
                        invalidPassword
                );

        // when
        Set<ConstraintViolation<ChangePasswordCommand>> violations =
                validator.validate(command);

        // then
        assertThat(
                hasViolationWithMessage(
                        violations,
                        "newPassword",
                        "새 비밀번호는 영문 대문자, 소문자, 숫자, 특수문자를 각각 1개 이상 포함해야 합니다."
                )
        ).isTrue();
    }

    @Test
    @DisplayName("toString은 현재 비밀번호와 새 비밀번호를 노출하지 않는다")
    void toStringMasksPasswords() {
        // given
        ChangePasswordCommand command =
                new ChangePasswordCommand(
                        CURRENT_PASSWORD,
                        NEW_PASSWORD
                );

        // when
        String value = command.toString();

        // then
        assertThat(value)
                .contains("[PROTECTED]")
                .doesNotContain(
                        CURRENT_PASSWORD,
                        NEW_PASSWORD
                );
    }

    private boolean hasViolation(
            Set<ConstraintViolation<ChangePasswordCommand>> violations,
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
            Set<ConstraintViolation<ChangePasswordCommand>> violations,
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
