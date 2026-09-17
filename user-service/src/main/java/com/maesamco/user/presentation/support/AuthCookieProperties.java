package com.maesamco.user.presentation.support;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Locale;

/**
 * Refresh Token Cookie 정책을 외부 설정에서 바인딩합니다.
 *
 * @param secure HTTPS에서만 Cookie를 전송할지 여부
 * @param sameSite 교차 사이트 요청에 대한 Cookie 전송 정책
 * @param path Cookie가 전송될 요청 경로
 */
@ConfigurationProperties(prefix = "security.auth-cookie")
public record AuthCookieProperties(
        @DefaultValue("true")
        boolean secure,
        @DefaultValue("Lax")
        String sameSite,
        @DefaultValue("/api/v1/auth")
        String path
) {

    public AuthCookieProperties {
        sameSite = normalizeSameSite(
                sameSite
        );
        path = normalizePath(
                path
        );

        if (!secure && "None".equals(sameSite)) {
            throw new IllegalArgumentException(
                    "SameSite=None requires a Secure cookie"
            );
        }
    }

    private static String normalizeSameSite(
            String sameSite
    ) {
        if (sameSite == null || sameSite.isBlank()) {
            throw new IllegalArgumentException(
                    "security.auth-cookie.same-site must not be blank"
            );
        }

        return switch (
                sameSite.trim().toLowerCase(Locale.ROOT)
        ) {
            case "strict" -> "Strict";
            case "lax" -> "Lax";
            case "none" -> "None";
            default -> throw new IllegalArgumentException(
                    "security.auth-cookie.same-site must be "
                            + "Strict, Lax, or None"
            );
        };
    }

    private static String normalizePath(
            String path
    ) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException(
                    "security.auth-cookie.path must not be blank"
            );
        }

        String normalizedPath = path.trim();
        if (!normalizedPath.startsWith("/")) {
            throw new IllegalArgumentException(
                    "security.auth-cookie.path must start with '/'"
            );
        }

        return normalizedPath;
    }
}