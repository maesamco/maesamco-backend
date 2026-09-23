package com.maesamco.user.infrastructure.security.social;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * 소셜 회원가입 임시 Token 정책입니다.
 *
 * @param tokenTtl 소셜 인증 후 회원가입 완료까지 허용하는 시간
 */
@ConfigurationProperties(
        prefix = "security.social.signup"
)
public record SocialSignupProperties(
        @DefaultValue("10m")
        Duration tokenTtl
) {

    public SocialSignupProperties {
        if (
                tokenTtl == null
                        || tokenTtl.isZero()
                        || tokenTtl.isNegative()
        ) {
            throw new IllegalArgumentException(
                    "security.social.signup.token-ttl must be positive"
            );
        }
    }
}
