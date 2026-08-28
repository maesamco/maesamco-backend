package com.maesamco.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.PublicKey;
import java.time.Instant;
import java.util.List;

/**
 * Gateway는 "로그인이 유효한가"만 확인한다 — 서명, 만료, 사용자 단위 즉시무효화
 * (탈퇴/정지/비밀번호 재설정), 세션 단위 블랙리스트(개별 기기 로그아웃).
 * 역할 기반 세부 권한/리소스 소유권은 각 서비스 몫이다(게이트웨이 및 인증 보안 설계 1절).
 *
 * ⚠️ 원본 JWT를 그대로 릴레이한다(JWT 릴레이 채택 — 커스텀 헤더로 가공하지 않음).
 *    클라이언트가 흉내 낼 수 있는 예약 헤더(X-Internal-*)는 반드시 제거해서 전달한다 —
 *    /internal/v1/** 는 Gateway 라우팅에서 아예 제외되지만, 방어 심층화 차원에서
 *    이 필터가 두 번째 방어선으로 한 번 더 제거한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtValidationFilter implements GlobalFilter, Ordered {

    private static final List<String> WHITELIST = List.of(
            "/api/v1/auth/signup",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/password-reset/request",
            "/api/v1/auth/password-reset/confirm",
            "/actuator/"
    );

    private static final String RESERVED_HEADER_PREFIX = "X-Internal-";

    private final PublicKey jwtPublicKey;
    private final ReactiveStringRedisTemplate redisTemplate;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 내부 전용 경로는 라우팅 규칙에서도 제외하지만, 여기서도 한 번 더 차단한다.
        if (path.startsWith("/internal/")) {
            return onForbidden(exchange);
        }

        ServerWebExchange sanitized = stripReservedHeaders(exchange);

        if (isWhitelisted(path)) {
            return chain.filter(sanitized);
        }

        String token = resolveToken(sanitized.getRequest());
        if (token == null) {
            return onUnauthorized(sanitized);
        }

        Claims claims;
        try {
            claims = Jwts.parser().verifyWith(jwtPublicKey).build()
                    .parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT 검증 실패 [{}]: {}", path, e.getMessage());
            return onUnauthorized(sanitized);
        }

        String userId = claims.getSubject();
        String sessionId = claims.get("sessionId", String.class);
        long issuedAtEpochMillis = claims.getIssuedAt().getTime();

        return Mono.zip(
                        isUserInvalidated(userId, issuedAtEpochMillis),
                        isSessionBlacklisted(sessionId)
                )
                .flatMap(tuple -> {
                    boolean userInvalidated = tuple.getT1();
                    boolean sessionBlacklisted = tuple.getT2();
                    if (userInvalidated || sessionBlacklisted) {
                        log.warn("무효화된 토큰 거부 [{}]: userId={}, session={}", path, userId, sessionId);
                        return onUnauthorized(sanitized);
                    }
                    // JWT 원본을 그대로 릴레이 — Authorization 헤더는 손대지 않는다.
                    return chain.filter(sanitized);
                })
                .onErrorResume(e -> {
                    log.error("무효화 상태 조회 실패(Redis) [{}]", path, e);
                    return onServiceUnavailable(sanitized);
                });
    }

    /** 탈퇴/정지/비밀번호 재설정 — 사용자 단위 즉시 무효화. */
    private Mono<Boolean> isUserInvalidated(String userId, long issuedAtEpochMillis) {
        return redisTemplate.opsForValue().get("user:" + userId + ":invalidatedAt")
                .map(invalidatedAt -> issuedAtEpochMillis < Long.parseLong(invalidatedAt))
                .defaultIfEmpty(false);
    }

    /** 개별 기기(세션) 로그아웃. */
    private Mono<Boolean> isSessionBlacklisted(String sessionId) {
        if (sessionId == null) {
            return Mono.just(false);
        }
        return redisTemplate.hasKey("session:" + sessionId + ":blacklisted");
    }

    private ServerWebExchange stripReservedHeaders(ServerWebExchange exchange) {
        return exchange.mutate()
                .request(request -> request.headers(headers ->
                        headers.headerNames().removeIf(name -> name.startsWith(RESERVED_HEADER_PREFIX))))
                .build();
    }

    private boolean isWhitelisted(String path) {
        if (path.startsWith("/swagger-ui") || path.contains("/v3/api-docs")) {
            return true;
        }
        return WHITELIST.stream().anyMatch(path::startsWith);
    }

    private String resolveToken(ServerHttpRequest request) {
        String bearer = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (bearer != null && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }

    private Mono<Void> onUnauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    private Mono<Void> onForbidden(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        return exchange.getResponse().setComplete();
    }

    private Mono<Void> onServiceUnavailable(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -2; // Rate Limit(-1)보다 먼저? → 아래 RateLimitFilter 주석 참고, 순서는 팀 협의로 확정
    }
}
