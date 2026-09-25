package com.maesamco.user.infrastructure.security.social.google;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Google 소셜 로그인 검증 설정입니다.
 *
 * @param clientId 백엔드가 신뢰할 Google OAuth Client ID
 */
@ConfigurationProperties(
        prefix = "security.social.google"
)
public record GoogleSocialProperties(
        String clientId
) {

    public GoogleSocialProperties {
        if (
                clientId == null
                        || clientId.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "security.social.google.client-id must not be blank"
            );
        }

        clientId = clientId.trim();
    }
}
