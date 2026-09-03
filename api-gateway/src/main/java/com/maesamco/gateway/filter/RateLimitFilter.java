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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * 로그인/비밀번호 재설정/코드 제출/코칭 힌트 생성 API에 고정 윈도(fixed window)
 * Rate Limit을 적용한다(게이트웨이 및 인증 보안 설계 8절). 계정 단위 로그인 실패
 * 잠금은 별개로 User Service가 담당한다 — 이 필터는 "요청 빈도" 자체를 제한하는
 * 1차 방어선이다.
 *
 * Redis 자료구조: INCR + 최초 요청 시에만 EXPIRE — Lua로 원자 처리해 레이스 컨디션 방지.
 * (resources/scripts/rate_limit.lua 참고)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter implements GlobalFilter, Ordered {

    /**
     * method가 null이면 모든 HTTP 메서드에 적용(기존 3개 룰과 동일한 동작).
     * 특정 메서드만 지정하면 그 메서드만 매칭 — 예: 코칭 힌트 생성(POST)만 제한하고
     * 같은 경로 하위의 GET(목록/상세 조회)은 별도로 두고 싶을 때 사용(PR #70 리뷰 반영).
     */
    private record RuleMatch(HttpMethod method, String prefix, int limit, Duration window) {
    }

    // 실제 임계값은 부하 테스트/운영 데이터로 조정할 것 — 여기 숫자는 초기값 예시.
    private static final List<RuleMatch> RULES = List.of(
            new RuleMatch(null, "/api/v1/auth/login", 10, Duration.ofMinutes(1)),
            new RuleMatch(null, "/api/v1/auth/password-reset", 5, Duration.ofMinutes(10)),
            new RuleMatch(null, "/api/v1/submissions", 30, Duration.ofMinutes(1)),
            // 힌트 생성은 LLM 호출 비용이 있는 액션이라 로그인과 같은 급으로 취급.
            // GET(목록/상세 조회)은 LLM 비용이 없어 이 룰에서 의도적으로 제외(method=POST만 매칭).
            // prefix가 "/api/v1/coaching/submissions"라 startsWith로 매칭되는
            // POST /api/v1/coaching/submissions/{id}/explanations(60초 설명 등록, 마찬가지로
            // LLM 호출 비용 있는 액션)도 이미 이 룰로 함께 보호된다(PR #88 리뷰 재검토) —
            // 별도 룰을 새로 추가하지 않는다.
            new RuleMatch(HttpMethod.POST, "/api/v1/coaching/submissions", 10, Duration.ofMinutes(1))
    );

    private final ReactiveRedisTemplate<String, Long> redisTemplate;
    private final RedisScript<Long> rateLimitScript = RedisScript.of(
            new ClassPathResource("scripts/rate_limit.lua"), Long.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();

        RuleMatch rule = RULES.stream()
                .filter(r -> path.startsWith(r.prefix()))
                .filter(r -> r.method() == null || r.method() == method)
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