package com.maesamco.user.application.validation;

import com.maesamco.user.application.service.WithdrawUserCommand;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * 회원 탈퇴 본인 확인 수단이 정확히 하나인지 검증합니다(#328).
 *
 * <ul>
 *     <li>둘 다 없으면 {@code currentPassword}에 위반을 기록합니다 (기존 비밀번호 필수 검증과 같은 필드).</li>
 *     <li>둘 다 있으면 {@code googleIdToken}에 위반을 기록합니다.</li>
 * </ul>
 */
public class WithdrawCredentialValidator
        implements ConstraintValidator<ValidWithdrawCredential, WithdrawUserCommand> {

    @Override
    public boolean isValid(
            WithdrawUserCommand command,
            ConstraintValidatorContext context
    ) {
        if (command == null) {
            return true;
        }

        boolean hasPassword = hasText(command.currentPassword());
        boolean hasGoogleIdToken = hasText(command.googleIdToken());

        if (hasPassword != hasGoogleIdToken) {
            return true;
        }

        context.disableDefaultConstraintViolation();

        if (!hasPassword) {
            context.buildConstraintViolationWithTemplate(
                            "현재 비밀번호 또는 Google 재인증 정보가 필요합니다."
                    )
                    .addPropertyNode("currentPassword")
                    .addConstraintViolation();
        } else {
            context.buildConstraintViolationWithTemplate(
                            "현재 비밀번호와 Google 재인증 정보는 함께 보낼 수 없습니다."
                    )
                    .addPropertyNode("googleIdToken")
                    .addConstraintViolation();
        }

        return false;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
