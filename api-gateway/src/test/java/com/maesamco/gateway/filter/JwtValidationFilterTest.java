package com.maesamco.gateway.filter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtValidationFilterTest {

    @Test
    void isWhitelisted_allowsOnlyConfiguredPublicPaths() {
        List<String> publicPaths = List.of(
            "/api/v1/auth/signup",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/email-verifications",
            "/api/v1/auth/email-verifications/confirm",
            "/api/v1/auth/password-reset/request",
            "/api/v1/auth/password-reset/confirm",
            "/swagger-ui",
            "/swagger-ui/index.html",
            "/user-service/v3/api-docs",
            "/content-service/v3/api-docs",
            "/judge-service/v3/api-docs",
            "/coaching-service/v3/api-docs",
            "/actuator/health",
            "/actuator/prometheus"
        );

        publicPaths.forEach(path -> assertTrue(
                JwtValidationFilter.isWhitelisted(path),
                () -> "공개 경로가 차단됨: " + path
        ));
    }

    @Test
    void isWhitelisted_rejectsSimilarButUnconfiguredPaths() {
        List<String> protectedPaths = List.of(
            "/api/v1/auth/login-malicious",
            "/api/v1/auth/login/",
            "/api/v1/auth/email-verifications/extra",
            "/api/v1/coaching/session/v3/api-docs",
            "/content-service/v3/api-docs/extra",
            "/swagger-ui-malicious",
            "/actuator",
            "/actuator-malicious"
        );

        protectedPaths.forEach(path -> assertFalse(
                JwtValidationFilter.isWhitelisted(path),
                () -> "유사 경로가 공개됨: " + path
        ));
    }
}
