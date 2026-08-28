package com.maesamco.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * application.yml의 jwt.public-key(PEM 문자열)를 실제 PublicKey 객체로 파싱해
 * Bean으로 등록한다. JwtValidationFilter가 이 Bean을 생성자로 주입받아
 * JWT 서명을 검증한다(서비스 쪽 SecurityConfig.jwtPublicKey()와 동일한 로직).
 */
@Configuration
public class JwtKeyConfig {

    @Bean
    public PublicKey jwtPublicKey(@Value("${jwt.public-key}") String publicKeyPem) throws Exception {
        String pem = publicKeyPem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(pem);
        // 서명 알고리즘은 User Service의 키 발급 방식과 일치해야 한다(예: RSA/RS256).
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(new X509EncodedKeySpec(decoded));
    }
}
