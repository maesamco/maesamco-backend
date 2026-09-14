package com.maesamco.user.infrastructure.security.email;

import com.maesamco.user.application.port.EmailVerificationSecretHasher;
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
 * HMAC-SHA256을 이용해 이메일 인증 비밀값을 단방향 해시로 변환합니다.
 *
 * <p>인증 코드와 회원가입 인증 토큰에는 서로 다른 도메인 값을
 * HMAC 입력에 포함해 같은 원문이라도 서로 다른 해시가 생성되도록 합니다.</p>
 */
@Component
public class HmacSha256EmailVerificationSecretHasher
        implements EmailVerificationSecretHasher {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int HMAC_KEY_LENGTH_BYTES = 32;

    private static final String VERIFICATION_CODE_DOMAIN =
            "email-verification-code";

    private static final String SIGNUP_TOKEN_DOMAIN =
            "email-verification-signup-token";

    private final SecretKey verificationHmacKey;

    /**
     * 설정된 Base64 키를 이메일 인증용 HMAC-SHA256 비밀 키로 변환합니다.
     *
     * @param properties 이메일 보안 키 설정
     */
    public HmacSha256EmailVerificationSecretHasher(
            EmailSecurityProperties properties
    ) {
        this.verificationHmacKey = createSecretKey(
                properties.verificationHmacKey()
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String hashVerificationCode(String verificationCode) {
        requireSecret(
                verificationCode,
                "해시를 생성할 이메일 인증 코드는 필수입니다."
        );

        return hash(
                VERIFICATION_CODE_DOMAIN,
                verificationCode
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String hashSignupToken(String signupToken) {
        requireSecret(
                signupToken,
                "해시를 생성할 회원가입 인증 토큰은 필수입니다."
        );

        return hash(
                SIGNUP_TOKEN_DOMAIN,
                signupToken
        );
    }

    /**
     * 용도 구분값과 비밀값을 HMAC 입력으로 사용해 해시를 생성합니다.
     */
    private String hash(
            String domain,
            String secret
    ) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(verificationHmacKey);

            mac.update(
                    domain.getBytes(StandardCharsets.UTF_8)
            );
            mac.update((byte) 0);

            byte[] hash = mac.doFinal(
                    secret.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "이메일 인증 비밀값 해시 생성에 실패했습니다.",
                    exception
            );
        }
    }

    /**
     * 비밀값이 존재하는지 검증합니다.
     */
    private static void requireSecret(
            String secret,
            String message
    ) {
        if (secret == null || secret.isBlank()) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    message
            );
        }
    }

    /**
     * Base64로 인코딩된 설정값을 HMAC-SHA256 키로 변환합니다.
     */
    private static SecretKey createSecretKey(String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank()) {
            throw new IllegalStateException(
                    "이메일 인증 해시 키 설정이 필요합니다."
            );
        }

        byte[] decodedKey;

        try {
            decodedKey = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "이메일 인증 해시 키는 올바른 Base64 값이어야 합니다.",
                    exception
            );
        }

        try {
            if (decodedKey.length != HMAC_KEY_LENGTH_BYTES) {
                throw new IllegalStateException(
                        "이메일 인증 해시 키는 32바이트여야 합니다."
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
