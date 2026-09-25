package com.maesamco.user.application.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 회원 탈퇴 본인 확인 수단(현재 비밀번호 또는 Google ID Token)이
 * 정확히 하나만 전달됐는지 검증합니다(#328).
 *
 * <p>여러 필드 간 관계를 검증하므로
 * {@link com.maesamco.user.application.service.WithdrawUserCommand} 타입에 적용합니다.</p>
 */
@Documented
@Constraint(validatedBy = WithdrawCredentialValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidWithdrawCredential {

    String message() default "현재 비밀번호 또는 Google 재인증 정보 중 하나만 입력해야 합니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
