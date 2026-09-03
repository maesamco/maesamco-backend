package com.maesamco.gateway.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * 로그인/비밀번호 재설정/코드 제출 API에 고정 윈도(fixed window) Rate Limit을 적용한다
 * (게이트웨이 및 인증 보안 설계 8절). 계정 단위 로그인 실패 잠금은 별개로 User Service가
 * 담당한다 — 이 필터는 "요청 빈도" 자체를 제한하는 1차 방어선이다.
 *
 * Redis 자료구조: INCR + 최초 요청 시에만 EXPIRE — Lua로 원자 처리해 레이스 컨디션 방지.
 * (resources/scripts/rate_limit.lua 참고)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter implements GlobalFilter, Ordered {

    private record RuleMatch(String prefix, int limit, Duration window) {
    }

    // 실제 임계값은 부하 테스트/운영 데이터로 조정할 것 — 여기 숫자는 초기값 예시.
    // TODO(#72): /api/v1/coaching/submissions/{submissionId}/hints(POST, 힌트 생성)에도
    // 룰이 필요하다 — LLM 호출이 있는 비용 있는 액션인데 지금은 룰이 전혀 없음. 초안:
    // new RuleMatch("/api/v1/coaching/submissions", 10, Duration.ofMinutes(1))
    private static final List<RuleMatch> RULES = List.of(
            new RuleMatch("/api/v1/auth/login", 10, Duration.ofMinutes(1)),
            new RuleMatch("/api/v1/auth/password-reset", 5, Duration.ofMinutes(10)),
            new RuleMatch("/api/v1/submissions", 30, Duration.ofMinutes(1))
    );

    private final ReactiveRedisTemplate<String, Long> redisTemplate;
    private final RedisScript<Long> rateLimitScript = RedisScript.of(
            new ClassPathResource("scripts/rate_limit.lua"), Long.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        RuleMatch rule = RULES.stream()
                .filter(r -> path.startsWith(r.prefix()))
                .findFirst()
                .orElse(null);

        if (rule == null) {
            return chain.filter(exchange);
        }

        String identifier = resolveIdentifier(request);
        String key = "rate-limit:" + rule.prefix() + ":" + identifier;

        return redisTemplate.execute(rateLimitScript,
                        List.of(key),
                        List.of(String.valueOf(rule.window().getSeconds())))
                .next()
                .defaultIfEmpty(0L)
                .flatMap(count -> {
                    if (count > rule.limit()) {
                        return onRateLimited(exchange);
                    }
                    return chain.filter(exchange);
                });
    }

    /** 로그인 전이라 사용자 식별이 안 되는 구간이 많으므로 IP를 우선 식별자로 쓴다. */
    private String resolveIdentifier(ServerHttpRequest request) {
        String forwardedFor = request.getHeaders().getFirst("Forwarded");
        if (forwardedFor != null) {
            return forwardedFor;
        }
        return Objects.requireNonNull(request.getRemoteAddress()).getAddress().getHostAddress();
    }

    private Mono<Void> onRateLimited(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -3; // JwtValidationFilter(-2)보다 먼저 — 인증 실패 요청도 빈도 제한 대상이어야 함
    }
}
