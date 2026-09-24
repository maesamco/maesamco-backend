package com.maesamco.content.support;

import io.jsonwebtoken.Jwts;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/** 실제 {@code JwtAuthenticationFilter}가 검증할 수 있는 테스트용 RSA 서명 Access Token을 만든다. */
public final class TestJwt {

    private static final KeyPair KEY_PAIR = generateKeyPair();

    private TestJwt() {
    }

    /** {@code jwt.public-key} 프로퍼티에 넣을 PEM 공개키 */
    public static String publicKeyPem() {
        String encoded = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
                .encodeToString(KEY_PAIR.getPublic().getEncoded());

        return "-----BEGIN PUBLIC KEY-----\n" + encoded + "\n-----END PUBLIC KEY-----\n";
    }

    public static String accessToken(String role) {
        long now = System.currentTimeMillis();

        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("role", role)
                .claim("tokenType", "ACCESS")
                .issuedAt(new Date(now))
                .expiration(new Date(now + 60 * 60 * 1000))
                .signWith(KEY_PAIR.getPrivate())
                .compact();
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException("테스트 RSA 키 생성에 실패했습니다.", exception);
        }
    }
}
