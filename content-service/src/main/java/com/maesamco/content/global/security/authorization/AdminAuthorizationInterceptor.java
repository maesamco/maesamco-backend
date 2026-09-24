package com.maesamco.content.global.security.authorization;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.lang.reflect.Method;

/**
 * {@link RequireAdmin}이 붙은 핸들러를 ADMIN 권한 없이 호출하면 컨트롤러 진입 전에
 * 막는다(이슈 #311).
 *
 * <p>{@code preHandle()}은 {@code DispatcherServlet}이 핸들러 메서드의 인자를 바인딩·검증하기
 * 전에 실행되므로, 여기서 걸리면 {@code @Valid @RequestBody} 검증은 아예 시도되지 않는다.
 * {@link AccessDeniedException}을 던져서 기존 {@code @PreAuthorize} 실패 시와 동일하게
 * {@code GlobalExceptionHandler.handleAccessDenied()}가 처리하도록 한다 — 응답 형식이
 * 기존과 완전히 동일하게 유지된다.</p>
 */
public class AdminAuthorizationInterceptor implements HandlerInterceptor {

    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        if (!requiresAdmin(handlerMethod)) {
            return true;
        }

        if (!isAdmin()) {
            throw new AccessDeniedException("ADMIN 권한이 필요합니다.");
        }

        return true;
    }

    /**
     * 프록시 방식(JDK 동적 프록시/CGLIB)과 무관하게 실제 대상 클래스 기준으로 {@link RequireAdmin}을 찾는다.
     *
     * <p>JDK 동적 프록시로 감싼 컨트롤러는 {@code HandlerMethod}가 인터페이스 메서드를 가리켜서
     * 구현체 메서드에 붙은 애노테이션이 보이지 않는다. 이 경우 검사가 조용히 빠져 USER가
     * 관리자 API를 통과하므로(fail-open), 프록시를 벗겨낸 대상 클래스와 그 구현 메서드까지 확인한다.
     * {@code SecurityConfig}의 {@code proxyTargetClass = true}에 보안이 기대지 않게 하기 위한
     * 이중 안전장치다.</p>
     */
    private boolean requiresAdmin(HandlerMethod handlerMethod) {
        Object bean = handlerMethod.getBean();
        Class<?> targetClass = bean instanceof String
                ? handlerMethod.getBeanType()
                : AopProxyUtils.ultimateTargetClass(bean);
        Method targetMethod = AopUtils.getMostSpecificMethod(handlerMethod.getMethod(), targetClass);

        return AnnotatedElementUtils.hasAnnotation(handlerMethod.getMethod(), RequireAdmin.class)
                || AnnotatedElementUtils.hasAnnotation(targetMethod, RequireAdmin.class)
                || AnnotatedElementUtils.hasAnnotation(targetClass, RequireAdmin.class)
                || AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), RequireAdmin.class);
    }

    private boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (ADMIN_AUTHORITY.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
