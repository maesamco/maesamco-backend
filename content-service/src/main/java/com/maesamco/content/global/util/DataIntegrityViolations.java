package com.maesamco.content.global.util;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Spring이 변환한 DB 무결성 예외에서 실제 제약 위반 종류를 판별합니다.
 */
public final class DataIntegrityViolations {

    private DataIntegrityViolations() {
    }

    public static boolean isUniqueViolation(DataIntegrityViolationException exception) {
        return exception.getCause() instanceof ConstraintViolationException violation
                && violation.getKind() == ConstraintViolationException.ConstraintKind.UNIQUE;
    }
}
