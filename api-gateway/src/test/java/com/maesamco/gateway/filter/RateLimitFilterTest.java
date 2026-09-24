package com.maesamco.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * RateLimitFilter의 룰 선택(prefix + findFirst) 우선순위를 검증한다.
 *
 * 룰 매칭이 선언 순서에 의존하므로, 구체적인 경로 룰이 포괄 prefix 룰보다
 * 먼저 적용되는지를 고정해 순서 회귀를 막는다(PR #320 리뷰).
 * Redis 호출 없이 룰 선택만 검증한다.
 */
class RateLimitFilterTest {

    private final RateLimitFilter filter =
            new RateLimitFilter(null, 30, List.of());

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
}
