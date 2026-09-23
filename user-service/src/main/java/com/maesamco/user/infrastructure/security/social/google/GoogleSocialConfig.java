package com.maesamco.user.infrastructure.security.social.google;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

/**
 * Google ID Token 검증에 필요한 객체를 구성합니다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(
        GoogleSocialProperties.class
)
public class GoogleSocialConfig {

    /**
     * 애플리케이션 전체에서 공유할 Google ID Token Verifier를 생성합니다.
     *
     * <p>Google 공개키는 Verifier 내부의 PublicKeysManager를 통해
     * 캐시되므로 요청마다 Verifier를 새로 만들지 않습니다.</p>
     */
    @Bean
    public GoogleIdTokenVerifier googleIdTokenVerifier(
            GoogleSocialProperties properties
    ) throws GeneralSecurityException, IOException {

        return new GoogleIdTokenVerifier.Builder(
                GoogleNetHttpTransport
                        .newTrustedTransport(),
                GsonFactory.getDefaultInstance()
        )
                .setAudience(
                        List.of(
                                properties.clientId()
                        )
                )
                .build();
    }
}
