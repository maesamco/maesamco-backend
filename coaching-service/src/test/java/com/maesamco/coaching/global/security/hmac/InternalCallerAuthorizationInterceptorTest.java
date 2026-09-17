package com.maesamco.coaching.global.security.hmac;

import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link InternalCallerAuthorizationInterceptor}가 {@link AllowedInternalCallers}를
 * 정확히 강제하는지 검증한다(이슈 #138 — "이 메커니즘 자체를 검증하는 테스트" 요구사항).
 *
 * 실제 컨트롤러 대신, 애노테이션이 붙은/안 붙은 더미 핸들러 메서드를 이 테스트
 * 클래스 안에 만들어 HandlerMethod로 감싸 검증한다 — 실제 스프링 컨텍스트를
 * 띄우지 않는 순수 단위 테스트.
 */
class InternalCallerAuthorizationInterceptorTest {

    private final InternalCallerAuthorizationInterceptor interceptor =
            new InternalCallerAuthorizationInterceptor();

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    void 허용된_호출자면_통과한다() throws NoSuchMethodException {
        request.addHeader(InternalCallHeaders.SERVICE, "content-service");
        HandlerMethod handlerMethod = handlerMethodFor("allowedForContentServiceOnly");

        boolean result = interceptor.preHandle(request, response, handlerMethod);

        assertThat(result).isTrue();
    }

    @Test
    void 허용되지_않은_호출자면_BusinessException을_던진다() throws NoSuchMethodException {
        request.addHeader(InternalCallHeaders.SERVICE, "judge-service");
        HandlerMethod handlerMethod = handlerMethodFor("allowedForContentServiceOnly");

        assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INTERNAL_CALLER_NOT_ALLOWED);
    }

    @Test
    void X_Internal_Service_헤더가_없으면_거부한다() throws NoSuchMethodException {
        // 헤더 자체를 안 넣음 — HmacVerificationFilter를 정상 통과했다면 이 상황 자체가
        // 없어야 하지만, 이 인터셉터 단독으로도 안전하게 거부하는지 확인한다.
        HandlerMethod handlerMethod = handlerMethodFor("allowedForContentServiceOnly");

        assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INTERNAL_CALLER_NOT_ALLOWED);
    }

    @Test
    void 애노테이션이_없으면_아무나_통과한다() throws NoSuchMethodException {
        // 헤더가 없어도, 애노테이션 자체가 없는 엔드포인트는 이 메커니즘의 적용 대상이
        // 아니므로 그냥 통과해야 한다(HmacVerificationFilter의 인증만으로 충분한 경우).
        HandlerMethod handlerMethod = handlerMethodFor("noRestrictionAnnotated");

        boolean result = interceptor.preHandle(request, response, handlerMethod);

        assertThat(result).isTrue();
    }

    @Test
    void HandlerMethod가_아니면_그냥_통과한다() {
        Object staticResourceHandler = new Object();

        boolean result = interceptor.preHandle(request, response, staticResourceHandler);

        assertThat(result).isTrue();
    }

    private HandlerMethod handlerMethodFor(String methodName) throws NoSuchMethodException {
        return new HandlerMethod(new DummyController(), DummyController.class.getMethod(methodName));
    }

    /** 테스트 전용 더미 컨트롤러 — 실제 라우팅에 등록되지 않는다. */
    static class DummyController {

        @AllowedInternalCallers("content-service")
        public void allowedForContentServiceOnly() {
        }

        public void noRestrictionAnnotated() {
        }
    }
}