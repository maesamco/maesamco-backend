package com.maesamco.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RateLimitFilter의 룰 선택(prefix + findFirst) 우선순위를 검증한다.
 *
 * 룰 매칭이 선언 순서에 의존하므로, 구체적인 경로 룰이 포괄 prefix 룰보다
 * 먼저 적용되는지를 고정해 순서 회귀를 막는다(PR #320 리뷰).
 * Redis 호출 없이 룰 선택만 검증한다.
 */
class RateLimitFilterTest {

    private final RateLimitFilter filter =
            new RateLimitFilter(null, 30, 300, List.of());

    @Test
    void socialSignup_usesSignupRule_notSocialLoginPrefixRule() {
        RateLimitFilter.RuleMatch rule =
                filter.findRule("/api/v1/auth/social/google/signup", HttpMethod.POST);

        assertNotNull(rule, "소셜 가입 경로에 룰이 적용되지 않음");
        assertEquals("/api/v1/auth/social/google/signup", rule.prefix());
        assertEquals(5, rule.limit());
        assertEquals(Duration.ofMinutes(10), rule.window());
    }

    @Test
    void socialLogin_usesSocialPrefixRule() {
        RateLimitFilter.RuleMatch rule =
                filter.findRule("/api/v1/auth/social/google", HttpMethod.POST);

        assertNotNull(rule, "소셜 로그인 경로에 룰이 적용되지 않음");
        assertEquals("/api/v1/auth/social", rule.prefix());
        assertEquals(10, rule.limit());
        assertEquals(Duration.ofMinutes(1), rule.window());
    }

    @Test
    void existingAuthRules_areUnchanged() {
        RateLimitFilter.RuleMatch signup =
                filter.findRule("/api/v1/auth/signup", HttpMethod.POST);
        RateLimitFilter.RuleMatch login =
                filter.findRule("/api/v1/auth/login", HttpMethod.POST);

        assertEquals(5, signup.limit());
        assertEquals(Duration.ofMinutes(10), signup.window());
        assertEquals(10, login.limit());
        assertEquals(Duration.ofMinutes(1), login.window());
    }

    @Test
    void methodSpecificRule_skipsOtherMethods() {
        // 코칭 제출 룰은 POST만 제한하고 GET 조회는 제외한다.
        assertNotNull(filter.findRule("/api/v1/coaching/submissions", HttpMethod.POST));
        assertNull(filter.findRule("/api/v1/coaching/submissions", HttpMethod.GET));
    }

    @Test
    void submissionCreate_isLimitedSeparatelyFromReads() {
        // 제출(POST)은 분당 30회 남용 방어 한도를 유지한다.
        RateLimitFilter.RuleMatch create = filter.findRule("/api/v1/submissions", HttpMethod.POST);

        assertNotNull(create);
        assertEquals(HttpMethod.POST, create.method());
        assertEquals(30, create.limit());
        assertEquals(Duration.ofMinutes(1), create.window());
    }

    @Test
    void submissionReads_useSeparateGenerousRule_soPollingDoesNotBlockNewSubmissions() {
        // 채점 결과 조회(폴링)와 이력 조회는 제출과 다른 룰(다른 카운터 키)이어야 하고 한도가 더 넉넉해야 한다(#349).
        RateLimitFilter.RuleMatch create = filter.findRule("/api/v1/submissions", HttpMethod.POST);
        RateLimitFilter.RuleMatch result = filter.findRule(
                "/api/v1/submissions/694cb77d-3dbc-49cd-82e3-01473afcc42a", HttpMethod.GET);
        RateLimitFilter.RuleMatch history = filter.findRule("/api/v1/submissions/me", HttpMethod.GET);

        assertNotNull(result);
        assertNotNull(history);
        assertEquals(HttpMethod.GET, result.method());
        assertEquals(HttpMethod.GET, history.method());
        assertEquals(300, result.limit());
        assertEquals(Duration.ofMinutes(1), result.window());
        // 카운터 키는 "rate-limit:{메서드}:{prefix}:{식별자}"이므로 메서드가 다르면 카운터가 분리된다.
        assertNotEquals(create.method(), result.method());
    }

    @Test
    void submissionRules_doNotAffectOtherMethods() {
        assertNull(filter.findRule("/api/v1/submissions/some-id", HttpMethod.DELETE));
    }

    @Test
    void head_isNormalizedToGetReadRule_soItCannotBypassTheReadLimit() {
        // HEAD는 Spring MVC가 GET 매핑에 자동 지원하므로 GET과 같은 조회 룰(같은 bucket)이 적용돼야 한다.
        RateLimitFilter.RuleMatch head = filter.findRule("/api/v1/submissions/some-id", HttpMethod.HEAD);
        RateLimitFilter.RuleMatch headMe = filter.findRule("/api/v1/submissions/me", HttpMethod.HEAD);
        RateLimitFilter.RuleMatch get = filter.findRule("/api/v1/submissions/some-id", HttpMethod.GET);

        assertNotNull(head, "HEAD /{id}가 조회 룰을 우회함");
        assertNotNull(headMe, "HEAD /me가 조회 룰을 우회함");
        assertEquals(get, head);
        assertEquals(HttpMethod.GET, head.method());
        assertEquals(RateLimitFilter.buildKey(get, "1.2.3.4"), RateLimitFilter.buildKey(head, "1.2.3.4"));
    }

    @Test
    void head_doesNotMatchPostOnlyRules() {
        // 정규화는 GET 룰에만 영향을 준다 — POST 전용 룰(제출, 코칭)에는 HEAD가 걸리지 않는다.
        assertNull(filter.findRule("/api/v1/coaching/submissions", HttpMethod.HEAD));
        assertNull(filter.findRule("/api/v1/contents/problems", HttpMethod.HEAD));
    }

    @Test
    void counterKeys_areSeparatedByMethodForTheSameClientIp() {
        RateLimitFilter.RuleMatch post = filter.findRule("/api/v1/submissions", HttpMethod.POST);
        RateLimitFilter.RuleMatch get = filter.findRule("/api/v1/submissions/some-id", HttpMethod.GET);

        assertEquals("rate-limit:POST:/api/v1/submissions:1.2.3.4", RateLimitFilter.buildKey(post, "1.2.3.4"));
        assertEquals("rate-limit:GET:/api/v1/submissions:1.2.3.4", RateLimitFilter.buildKey(get, "1.2.3.4"));
        assertNotEquals(RateLimitFilter.buildKey(post, "1.2.3.4"), RateLimitFilter.buildKey(get, "1.2.3.4"));
        // 메서드 무관 룰은 ALL로 표기된다.
        assertEquals("rate-limit:ALL:/api/v1/auth/login:1.2.3.4",
                RateLimitFilter.buildKey(filter.findRule("/api/v1/auth/login", HttpMethod.POST), "1.2.3.4"));
    }

    /** Redis 대신 키별 카운터를 메모리에 두는 가짜 템플릿 — 실제 filter() 경로에서 사용되는 키와 카운트를 검증한다. */
    @SuppressWarnings("unchecked")
    private RateLimitFilter filterWithInMemoryCounters(Map<String, Long> counters, List<String> usedKeys,
                                                       int postLimit, int readLimit) {
        ReactiveRedisTemplate<String, Long> redis = mock(ReactiveRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), anyList())).thenAnswer(invocation -> {
            String key = ((List<String>) invocation.getArgument(1)).get(0);
            usedKeys.add(key);
            return Flux.just(counters.merge(key, 1L, Long::sum));
        });
        return new RateLimitFilter(redis, postLimit, readLimit, List.of());
    }

    private HttpStatus send(RateLimitFilter f, HttpMethod method, String path) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(method, path).remoteAddress(new InetSocketAddress("1.2.3.4", 5000)));
        GatewayFilterChain chain = e -> Mono.empty();
        f.filter(exchange, chain).block();
        return exchange.getResponse().getStatusCode() == null ? HttpStatus.OK
                : HttpStatus.valueOf(exchange.getResponse().getStatusCode().value());
    }

    @Test
    void filter_postAndGetCountersDoNotInfluenceEachOther() {
        Map<String, Long> counters = new HashMap<>();
        List<String> usedKeys = new ArrayList<>();
        RateLimitFilter f = filterWithInMemoryCounters(counters, usedKeys, 30, 300);

        // 제출(POST) 30회는 통과하고 31번째는 429.
        for (int i = 0; i < 30; i++) {
            assertEquals(HttpStatus.OK, send(f, HttpMethod.POST, "/api/v1/submissions"));
        }
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, send(f, HttpMethod.POST, "/api/v1/submissions"));

        // 제출 한도가 소진돼도 같은 IP의 조회(GET)는 별도 카운터라 계속 통과한다.
        for (int i = 0; i < 300; i++) {
            assertEquals(HttpStatus.OK, send(f, HttpMethod.GET, "/api/v1/submissions/some-id"));
        }
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, send(f, HttpMethod.GET, "/api/v1/submissions/some-id"));

        assertEquals(31L, counters.get("rate-limit:POST:/api/v1/submissions:1.2.3.4"));
        assertEquals(301L, counters.get("rate-limit:GET:/api/v1/submissions:1.2.3.4"));
    }

    @Test
    void filter_headRequestsShareTheGetCounter_andCannotBypassTheLimit() {
        Map<String, Long> counters = new HashMap<>();
        List<String> usedKeys = new ArrayList<>();
        RateLimitFilter f = filterWithInMemoryCounters(counters, usedKeys, 30, 3);

        assertEquals(HttpStatus.OK, send(f, HttpMethod.GET, "/api/v1/submissions/some-id"));
        assertEquals(HttpStatus.OK, send(f, HttpMethod.HEAD, "/api/v1/submissions/some-id"));
        assertEquals(HttpStatus.OK, send(f, HttpMethod.HEAD, "/api/v1/submissions/me"));
        // GET과 HEAD가 같은 카운터를 써서 4번째 요청(HEAD)이 429다.
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, send(f, HttpMethod.HEAD, "/api/v1/submissions/some-id"));

        assertEquals(List.of("rate-limit:GET:/api/v1/submissions:1.2.3.4"),
                usedKeys.stream().distinct().toList());
    }
}
