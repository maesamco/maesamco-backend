package com.maesamco.coaching.global.security.hmac;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * /internal/v1/** 전용 필터. API Gateway가 이 경로를 외부 라우팅 대상에서
 * 제외하는 것과는 별개의 방어선이다 — Gateway 설정 실수나 네트워크 격리가
 * 깨지는 상황을 대비한 두 번째 방어선(게이트웨이 및 인증 보안 설계 6절).
 *
 * 이 필터는 /internal/v1/** 경로에만 등록할 것 (SecurityConfig의 필터 체인과
 * 별도로, WebMvcConfigurer의 인터셉터 또는 별도 FilterRegistrationBean으로
 * urlPatterns="/internal/v1/*" 지정 권장).
 *
 * ⚠️ 예전엔 서명 대상이 "serviceName:timestamp"뿐이라, 유효한 서명 헤더 하나만
 * 손에 넣으면 완전히 다른 경로/메서드/바디로 바꿔치기해도 통과했다(리뷰로 발견).
 * method+path+쿼리스트링+bodyHash를 서명 검증에 포함시키고, nonce 재사용 여부를
 * Redis로 확인해 재전송(같은 요청 반복)까지 막는다.
 *
 * ⚠️ P0 리뷰로 발견 — 바디를 여러 번 읽을 수 있게 감싸는 데 ContentCachingRequestWrapper를
 * 쓰면 안 된다(재현 테스트로 확인: getInputStream()을 두 번째 호출하면 항상 빈 바디를
 * 반환해 컨트롤러가 @RequestBody를 못 읽는다). 대신 CachedBodyHttpServletRequest로
 * 바디를 byte[]로 직접 저장해 몇 번을 읽어도 항상 처음부터 다시 읽을 수 있게 한다.
 */
@Slf4j
@RequiredArgsConstructor
public class HmacVerificationFilter extends OncePerRequestFilter {

    private static final long ALLOWED_CLOCK_SKEW_MILLIS = 300_000L; // 300초
    private static final Duration NONCE_TTL = Duration.ofMillis(ALLOWED_CLOCK_SKEW_MILLIS * 2);

    private final InternalServiceKeyProperties keyProperties;
    private final StringRedisTemplate redisTemplate;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String callerService = request.getHeader(InternalCallHeaders.SERVICE);
        String timestampHeader = request.getHeader(InternalCallHeaders.TIMESTAMP);
        String nonce = request.getHeader(InternalCallHeaders.NONCE);
        String signature = request.getHeader(InternalCallHeaders.SIGNATURE);

        if (isBlank(callerService) || isBlank(timestampHeader) || isBlank(nonce) || isBlank(signature)) {
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

        // 바디를 여러 번 읽을 수 있게 감싼다 — CachedBodyHttpServletRequest는 byte[]로
        // 직접 저장해두므로, 여기서 해시를 위해 읽어도 이후 필터 체인/컨트롤러가
        // 다시 처음부터 읽을 수 있다(ContentCachingRequestWrapper와 달리).
        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request);
        String bodyHash = HmacSignatureUtil.hashBody(wrappedRequest.getCachedBody());

        String method = request.getMethod();
        String path = request.getRequestURI();
        String normalizedQuery = HmacSignatureUtil.normalizeQuery(request.getQueryString());

        boolean valid = HmacSignatureUtil.verify(
                callerService, method, path, normalizedQuery, bodyHash, nonce, timestamp, expectedKey, signature);
        if (!valid) {
            log.warn("내부 호출 서명 불일치: caller={}", callerService);
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "서명이 유효하지 않음");
            return;
        }

        // 서명 검증을 통과한 뒤에만 nonce 재사용 여부를 확인한다 — 유효하지도 않은
        // 서명으로 nonce 저장소를 채워 정상 요청을 막는(DoS성) 상황을 방지하기 위함.
        //
        // ⚠️ P2 리뷰 반영 — 이 필터는 원래 Redis와 무관했는데 nonce 검증을 위해 처음
        // Redis 하드 의존이 생겼다. Redis 장애 시 예외가 그대로 튀어나가면 401이 아닌
        // 형식이 다른 500으로 빠지고, /internal/v1/** 전체(서비스 간 모든 내부 호출)에
        // 영향을 준다. 여기서는 fail-closed(거부)로 결정했다 — Redis가 응답 못 하면
        // "재전송 여부를 확인할 수 없으니 안전하게 차단"한다. fail-open(통과)이 낫다고
        // 판단되면 팀 논의 후 바꿀 것(가용성 우선이면 fail-open, 보안 우선이면 유지).
        Boolean firstUse;
        try {
            String nonceKey = "hmac-nonce:" + callerService + ":" + nonce;
            firstUse = redisTemplate.opsForValue().setIfAbsent(nonceKey, "1", NONCE_TTL);
        } catch (Exception e) {
            log.error("Redis 장애로 nonce 재사용 검증 실패 — fail-closed로 요청을 거부합니다. caller={}",
                    callerService, e);
            reject(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "내부 인증 인프라 일시 장애");
            return;
        }
        if (Boolean.FALSE.equals(firstUse)) {
            log.warn("내부 호출 재전송 의심(nonce 재사용): caller={}, nonce={}", callerService, nonce);
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "이미 사용된 요청(재전송 의심)");
            return;
        }

        filterChain.doFilter(wrappedRequest, response);
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