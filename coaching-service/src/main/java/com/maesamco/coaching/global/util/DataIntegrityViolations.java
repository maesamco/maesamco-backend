package com.maesamco.coaching.global.util;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Repository saveAndFlush()에서 잡은 DataIntegrityViolationException이 실제로 UNIQUE
 * 위반인지 판별한다.
 *
 * Flyway가 실제 FK 제약을 걸기 전에는 이 프로젝트의 Coaching 자식 엔티티들이 UNIQUE 위반만
 * 겪을 수 있어서 DataIntegrityViolationException을 잡으면 곧 UNIQUE 위반이었다. 실제 FK가
 * 걸린 지금은 존재하지 않는 부모 ID로 저장해도 같은 예외가 나기 때문에, 무조건 "이미
 * 존재함"(409)으로 변환하면 원인이 다른데도 같은 응답이 나간다(PR #8 리뷰). Hibernate가
 * 던지는 ConstraintViolationException.getKind()로 실제 제약 종류를 구분한다.
 */
public final class DataIntegrityViolations {

    private DataIntegrityViolations() {
    }

    public static boolean isUniqueViolation(DataIntegrityViolationException e) {
        return e.getCause() instanceof ConstraintViolationException cve
                && cve.getKind() == ConstraintViolationException.ConstraintKind.UNIQUE;
    }
}
