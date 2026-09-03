package com.maesamco.user.infrastructure.security.jwt;

import com.maesamco.user.application.port.RefreshTokenHasher;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Refresh Token을 SHA-256으로 해시하고 검증합니다.
 *
 * <p>Refresh Token은 충분한 엔트로피를 가진 서버 발급 토큰이므로
 * 비밀번호용 느린 해시가 아닌 SHA-256 단방향 해시를 사용합니다.</p>
 */
@Component
public class Sha256RefreshTokenHasher implements RefreshTokenHasher {

    private static final String HASH_ALGORITHM = "SHA-256";

    /**
     * {@inheritDoc}
     */
    @Override
    public String hash(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException(
                    "해시할 Refresh Token은 필수입니다."
            );
        }

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(digest(refreshToken));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(
            String refreshToken,
            String refreshTokenHash
    ) {
        if (refreshToken == null
                || refreshToken.isBlank()
                || refreshTokenHash == null
                || refreshTokenHash.isBlank()) {
            return false;
        }

        try {
            byte[] expectedHash = Base64.getUrlDecoder()
                    .decode(refreshTokenHash);

            return MessageDigest.isEqual(
                    digest(refreshToken),
                    expectedHash
            );
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /**
     * 문자열의 SHA-256 해시 바이트를 생성합니다.
     */
    private byte[] digest(String value) {
        try {
            MessageDigest messageDigest =
                    MessageDigest.getInstance(HASH_ALGORITHM);

            return messageDigest.digest(
                    value.getBytes(StandardCharsets.UTF_8)
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 해시 알고리즘을 사용할 수 없습니다.",
                    exception
            );
        }
    }
}
