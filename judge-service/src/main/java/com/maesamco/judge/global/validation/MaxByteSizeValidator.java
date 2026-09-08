package com.maesamco.judge.global.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class MaxByteSizeValidator implements ConstraintValidator<MaxByteSize, String> {
    private int max;

    @Override
    public void initialize(MaxByteSize annotation) {
        this.max = annotation.value();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true; //null체크는 @NotBlank에 맡김
        return value.getBytes(StandardCharsets.UTF_8).length <= this.max;
    }
}
