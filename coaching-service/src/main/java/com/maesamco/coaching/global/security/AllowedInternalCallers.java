package com.maesamco.coaching.global.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 특정 발신 서비스 전용으로 설계된 내부 API(/internal/v1/**)에 붙인다.
 * HmacVerificationFilter가 "유효하게 서명된 내부 호출인가"(인증)만 확인하고
 * "이 서비스가 이 API를 호출해도 되는가"(인가)는 확인하지 않는 문제를 보완한다
 * (이슈 #138, 게이트웨이 및 인증 보안 설계 6절).
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AllowedInternalCallers {
    String[] value();
}