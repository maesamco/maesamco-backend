package com.maesamco.coaching.global.security.hmac;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * /internal/v1/** 전용 필터. API Gateway가 이 경로를 외부 라우팅 대상에서
 * 제외하는 것과는 별개의 방어선이다 — Gateway 설정 실수나 네트워크 격리가
 * 깨지는 상황을 대비한 두 번째 방어선(게이트웨이 및 인증 보안 설계 6절).
 *
 * 이 필터는 /internal/v1/** 경로에만 등록할 것 (SecurityConfig의 필터 체인과
 * 별도로, WebMvcConfigurer의 인터셉터 또는 별도 FilterRegistrationBean으로
 * urlPatterns="/internal/v1/*" 지정 권장).
 */
@Slf4j
@RequiredArgsConstructor
public class HmacVerificationFilter extends OncePerRequestFilter {

    private static final long ALLOWED_CLOCK_SKEW_MILLIS = 300_000L; // 300초

    private final InternalServiceKeyProperties keyProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String callerService = request.getHeader(InternalCallHeaders.SERVICE);
        String timestampHeader = request.getHeader(InternalCallHeaders.TIMESTAMP);
        String signature = request.getHeader(InternalCallHeaders.SIGNATURE);

        if (isBlank(callerService) || isBlank(timestampHeader) || isBlank(signature)) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "내부 호출 서명 헤더 누락");
            return;
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader);
        } catch (NumberFormatException e) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "타임스탬프 형식 오류");
            return;
        }

        long now = System.currentTimeMillis();
        if (Math.abs(now - timestamp) > ALLOWED_CLOCK_SKEW_MILLIS) {
            log.warn("내부 호출 재전송 의심: caller={}, skewMillis={}", callerService, now - timestamp);
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "요청 시각이 허용 범위를 벗어남(재전송 의심)");
            return;
        }

        String expectedKey;
        try {
            expectedKey = keyProperties.keyFor(callerService);
        } catch (IllegalStateException e) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "등록되지 않은 호출 서비스");
            return;
        }

        boolean valid = HmacSignatureUtil.verify(callerService, timestamp, expectedKey, signature);
        if (!valid) {
            log.warn("내부 호출 서명 불일치: caller={}", callerService);
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "서명이 유효하지 않음");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void reject(HttpServletResponse response, int status, String reason) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("""
                {"success":false,"error":{"code":"INTERNAL_CALL_SIGNATURE_INVALID","message":"%s"}}
                """.formatted(reason));
    }
}
