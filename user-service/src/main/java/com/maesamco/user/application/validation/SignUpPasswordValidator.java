package com.maesamco.user.application.validation;

import com.maesamco.user.application.service.SignUpCommand;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * 회원가입 비밀번호가 이메일 또는 닉네임과 동일하지 않은지 검증합니다.
 */
public class SignUpPasswordValidator
        implements ConstraintValidator<ValidSignUpPassword, SignUpCommand> {

    @Override
    public boolean isValid(
            SignUpCommand command,
            ConstraintValidatorContext context
    ) {
        if (command == null) {
            return true;
        }

        String password = command.password();

        if (password == null) {
            return true;
        }

        boolean sameAsEmail =
                command.email() != null
                        && password.equalsIgnoreCase(command.email().trim());

        boolean sameAsNickname =
                command.nickname() != null
                        && password.equals(command.nickname());

        if (!sameAsEmail && !sameAsNickname) {
            return true;
        }

        context.disableDefaultConstraintViolation();

        context.buildConstraintViolationWithTemplate(
                        "비밀번호는 이메일 또는 닉네임과 동일할 수 없습니다."
                )
                .addPropertyNode("password")
                .addConstraintViolation();

        return false;
    }
}
