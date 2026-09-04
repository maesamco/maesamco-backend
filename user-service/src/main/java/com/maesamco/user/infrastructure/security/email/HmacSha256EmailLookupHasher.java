package com.maesamco.user.infrastructure.security.email;

import com.maesamco.user.application.port.EmailLookupHasher;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

/**
 * HMAC-SHA256을 이용해 이메일 조회용 해시를 생성합니다.
 *
 * <p>동일하게 정규화된 이메일과 동일한 비밀 키에는 항상 같은
 * 64자의 소문자 16진수 문자열을 생성합니다.</p>
 */
@Component
public class HmacSha256EmailLookupHasher
        implements EmailLookupHasher {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int HMAC_KEY_LENGTH_BYTES = 32;

    private final SecretKey lookupHmacKey;

    /**
     * 설정된 Base64 키를 HMAC-SHA256 비밀 키로 변환합니다.
     *
     * @param properties 이메일 보안 키 설정
     */
    public HmacSha256EmailLookupHasher(
            EmailSecurityProperties properties
    ) {
        this.lookupHmacKey = createSecretKey(
                properties.lookupHmacKey()
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String hash(String normalizedEmail) {
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "조회 해시를 생성할 이메일은 필수입니다."
            );
        }

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(lookupHmacKey);

            byte[] hash = mac.doFinal(
                    normalizedEmail.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "이메일 조회 해시 생성에 실패했습니다.",
                    exception
            );
        }
    }

    /**
     * Base64로 인코딩된 설정값을 HMAC-SHA256 키로 변환합니다.
     */
    private static SecretKey createSecretKey(String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank()) {
            throw new IllegalStateException(
                    "이메일 조회 해시 키 설정이 필요합니다."
            );
        }

        byte[] decodedKey;

        try {
            decodedKey = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "이메일 조회 해시 키는 올바른 Base64 값이어야 합니다.",
                    exception
            );
        }

        try {
            if (decodedKey.length != HMAC_KEY_LENGTH_BYTES) {
                throw new IllegalStateException(
                        "이메일 조회 해시 키는 32바이트여야 합니다."
                );
            }

            return new SecretKeySpec(
                    decodedKey,
                    HMAC_ALGORITHM
            );
        } finally {
            Arrays.fill(decodedKey, (byte) 0);
        }
    }
}
