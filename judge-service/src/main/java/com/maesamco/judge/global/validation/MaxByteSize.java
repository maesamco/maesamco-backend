package com.maesamco.judge.global.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MaxByteSizeValidator.class)
public @interface MaxByteSize {
    int value();
    String message() default "허용된 크기를 초과했습니다.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
