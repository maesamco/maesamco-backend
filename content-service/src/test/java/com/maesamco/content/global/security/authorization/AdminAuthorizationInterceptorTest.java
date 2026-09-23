package com.maesamco.content.global.security.authorization;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * {@link AdminAuthorizationInterceptor}가 {@link RequireAdmin} 유무와 인증 권한에 따라
 * 컨트롤러 진입 전에 올바르게 통과/차단하는지 검증한다(이슈 #311).
 */
class AdminAuthorizationInterceptorTest {

    private final AdminAuthorizationInterceptor interceptor = new AdminAuthorizationInterceptor();
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    static class MethodLevelController {
        @RequireAdmin
        public void adminOnlyMethod() {
        }

        public void publicMethod() {
        }
    }

    @RequireAdmin
    static class ClassLevelController {
        public void anyMethod() {
        }
    }

    @Test
    @DisplayName("HandlerMethod가 아니면(정적 리소스 등) 그대로 통과시킨다")
    void preHandle_notHandlerMethod_returnsTrue() throws Exception {
        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("@RequireAdmin이 없는 메서드는 인증 여부와 무관하게 통과시킨다")
    void preHandle_noRequireAdminAnnotation_returnsTrue() throws Exception {
        HandlerMethod handlerMethod = new HandlerMethod(
                new MethodLevelController(), MethodLevelController.class.getMethod("publicMethod")
        );

        boolean result = interceptor.preHandle(request, response, handlerMethod);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("메서드 레벨 @RequireAdmin — ADMIN 권한이면 통과시킨다")
    void preHandle_methodLevelRequireAdmin_withAdminAuthority_returnsTrue() throws Exception {
        setAuthentication("ROLE_ADMIN");

        HandlerMethod handlerMethod = new HandlerMethod(
                new MethodLevelController(), MethodLevelController.class.getMethod("adminOnlyMethod")
        );

        boolean result = interceptor.preHandle(request, response, handlerMethod);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("메서드 레벨 @RequireAdmin — ADMIN이 아니면 컨트롤러 진입 전에 AccessDeniedException을 던진다")
    void preHandle_methodLevelRequireAdmin_withoutAdminAuthority_throwsAccessDenied() throws Exception {
        setAuthentication("ROLE_USER");

        HandlerMethod handlerMethod = new HandlerMethod(
                new MethodLevelController(), MethodLevelController.class.getMethod("adminOnlyMethod")
        );

        assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("인증 정보가 전혀 없으면(SecurityContext 비어있음) AccessDeniedException을 던진다")
    void preHandle_noAuthentication_throwsAccessDenied() throws Exception {
        SecurityContextHolder.clearContext();

        HandlerMethod handlerMethod = new HandlerMethod(
                new MethodLevelController(), MethodLevelController.class.getMethod("adminOnlyMethod")
        );

        assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("클래스 레벨 @RequireAdmin도 동일하게 강제한다")
    void preHandle_classLevelRequireAdmin_withoutAdminAuthority_throwsAccessDenied() throws Exception {
        setAuthentication("ROLE_USER");

        HandlerMethod handlerMethod = new HandlerMethod(
                new ClassLevelController(), ClassLevelController.class.getMethod("anyMethod")
        );

        assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
                .isInstanceOf(AccessDeniedException.class);
    }

    private void setAuthentication(String authority) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "test-user", null, List.of(new SimpleGrantedAuthority(authority))
                )
        );
    }
}
