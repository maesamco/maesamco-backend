package com.maesamco.user.application.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 회원가입 시 비밀번호가 이메일 또는 닉네임과 동일하지 않은지 검증합니다.
 *
 * <p>단일 필드가 아닌 여러 필드 간 관계를 검증해야 하므로
 * {@link com.maesamco.user.application.service.SignUpCommand} 타입에 적용합니다.</p>
 */
@Documented
@Constraint(validatedBy = SignUpPasswordValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidSignUpPassword {

    String message() default "비밀번호는 이메일 또는 닉네임과 동일할 수 없습니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
