package com.maesamco.content.global.security.authorization;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ADMIN 권한이 있어야만 호출할 수 있는 API에 붙인다(이슈 #311).
 *
 * <p>기존에는 이 검사를 {@code @PreAuthorize("hasRole('ADMIN')")}(Spring Security
 * 메서드 보안, AOP)로 했는데, {@code @Valid @RequestBody}의 인자 바인딩·검증이
 * 컨트롤러 메서드 호출(=AOP 어드바이스 진입) 전에 먼저 일어나서, 검증에 실패하는
 * 바디를 보내면 권한이 없는 사용자도 403보다 400을 먼저 받아 그 API가 요구하는
 * 필드 스키마를 정찰할 수 있었다.</p>
 *
 * <p>이 애노테이션은 {@link AdminAuthorizationInterceptor}(HandlerInterceptor)가
 * 검사한다 — 인터셉터의 {@code preHandle()}은 Spring MVC가 핸들러 인자를 바인딩·검증하기
 * *전에* 실행되므로, 권한 없는 요청은 바디를 들여다보기도 전에 항상 403으로 먼저
 * 막힌다(게이트웨이 및 인증 보안 설계 7절 원칙 3과 동일한 논리 — 정보량이 더 적은
 * 응답이 항상 먼저 나가도록 판단 순서를 고정한다).</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireAdmin {
}
