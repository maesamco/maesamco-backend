package com.maesamco.content.global.security.authorization;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

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

        boolean requiresAdmin = handlerMethod.hasMethodAnnotation(RequireAdmin.class)
                || handlerMethod.getBeanType().isAnnotationPresent(RequireAdmin.class);

        if (!requiresAdmin) {
            return true;
        }

        if (!isAdmin()) {
            throw new AccessDeniedException("ADMIN 권한이 필요합니다.");
        }

        return true;
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
