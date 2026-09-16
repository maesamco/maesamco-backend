package com.maesamco.content.global.security.hmac;

import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

/**
 * {@link AllowedInternalCallers}가 붙은 컨트롤러/메서드에 대해, X-Internal-Service
 * 헤더 값이 허용된 발신 서비스 목록에 있는지 확인한다(이슈 #138).
 */
public class InternalCallerAuthorizationInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        AllowedInternalCallers annotation = handlerMethod.getMethodAnnotation(AllowedInternalCallers.class);
        if (annotation == null) {
            annotation = handlerMethod.getBeanType().getAnnotation(AllowedInternalCallers.class);
        }
        if (annotation == null) {
            return true;
        }

        String callerService = request.getHeader(InternalCallHeaders.SERVICE);
        boolean allowed = Arrays.asList(annotation.value()).contains(callerService);
        if (!allowed) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_CALLER_NOT_ALLOWED,
                    "이 내부 API를 호출할 권한이 없는 서비스입니다: " + callerService
            );
        }

        return true;
    }
}