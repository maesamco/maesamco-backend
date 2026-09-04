package com.maesamco.user.global.util;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 데이터 무결성 예외가 특정 UNIQUE 제약 위반인지 판별합니다.
 */
public final class DataIntegrityViolations {

    private DataIntegrityViolations() {
    }

    /**
     * 예외가 지정한 이름의 UNIQUE 제약 위반인지 확인합니다.
     *
     * @param exception 데이터 무결성 예외
     * @param constraintName 확인할 제약 이름
     * @return 지정한 UNIQUE 제약 위반이면 true
     */
    public static boolean isUniqueViolation(
            DataIntegrityViolationException exception,
            String constraintName
    ) {
        return exception.getCause()
                instanceof ConstraintViolationException violation
                && violation.getKind()
                == ConstraintViolationException.ConstraintKind.UNIQUE
                && constraintName.equals(violation.getConstraintName());
    }
}
