package com.maesamco.user.infrastructure.security.email;

import com.maesamco.user.application.port.EmailCipher;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256-GCM을 이용해 이메일을 암호화하고 복호화합니다.
 *
 * <p>암호화마다 새로운 12바이트 IV를 생성하며, 인증 태그를 통해
 * 암호문의 변조 여부도 함께 검증합니다.</p>
 */
@Component
public class AesGcmEmailCipher implements EmailCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final String VERSION_PREFIX = "v1:";
    private static final int AES_256_KEY_LENGTH_BYTES = 32;
    private static final int IV_LENGTH_BYTES = 12;
    private static final int AUTH_TAG_LENGTH_BITS = 128;
    private static final byte[] ADDITIONAL_AUTHENTICATED_DATA =
            "maesamco:user-service:email:v1"
                    .getBytes(StandardCharsets.UTF_8);

    private final SecretKey encryptionKey;
    private final SecureRandom secureRandom;

    /**
     * 설정된 Base64 키를 AES-256 비밀 키로 변환합니다.
     *
     * @param properties 이메일 보안 키 설정
     */
    public AesGcmEmailCipher(EmailSecurityProperties properties) {
        this.encryptionKey = createSecretKey(
                properties.encryptionKey()
        );
        this.secureRandom = new SecureRandom();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String encrypt(String normalizedEmail) {
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "암호화할 이메일은 필수입니다."
            );
        }

        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    encryptionKey,
                    new GCMParameterSpec(AUTH_TAG_LENGTH_BITS, iv)
            );
            cipher.updateAAD(ADDITIONAL_AUTHENTICATED_DATA);

            byte[] encryptedEmail = cipher.doFinal(
                    normalizedEmail.getBytes(StandardCharsets.UTF_8)
            );
            byte[] payload = ByteBuffer
                    .allocate(iv.length + encryptedEmail.length)
                    .put(iv)
                    .put(encryptedEmail)
                    .array();

            return VERSION_PREFIX
                    + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "이메일 암호화에 실패했습니다.",
                    exception
            );
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String decrypt(String encryptedEmail) {
        if (encryptedEmail == null
                || !encryptedEmail.startsWith(VERSION_PREFIX)) {
            throw new IllegalStateException(
                    "지원하지 않는 이메일 암호문 형식입니다."
            );
        }

        try {
            byte[] payload = Base64.getDecoder().decode(
                    encryptedEmail.substring(VERSION_PREFIX.length())
            );

            validatePayloadLength(payload);

            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[IV_LENGTH_BYTES];
            buffer.get(iv);

            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    encryptionKey,
                    new GCMParameterSpec(AUTH_TAG_LENGTH_BITS, iv)
            );
            cipher.updateAAD(ADDITIONAL_AUTHENTICATED_DATA);

            byte[] decryptedEmail = cipher.doFinal(cipherText);
            return new String(
                    decryptedEmail,
                    StandardCharsets.UTF_8
            );
        } catch (GeneralSecurityException
                 | IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "이메일 복호화에 실패했습니다.",
                    exception
            );
        }
    }

    /**
     * Base64로 인코딩된 설정값을 AES-256 키로 변환합니다.
     */
    private static SecretKey createSecretKey(String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank()) {
            throw new IllegalStateException(
                    "이메일 암호화 키 설정이 필요합니다."
            );
        }

        byte[] decodedKey;

        try {
            decodedKey = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "이메일 암호화 키는 올바른 Base64 값이어야 합니다.",
                    exception
            );
        }

        try {
            if (decodedKey.length != AES_256_KEY_LENGTH_BYTES) {
                throw new IllegalStateException(
                        "이메일 암호화 키는 32바이트여야 합니다."
                );
            }

            return new SecretKeySpec(decodedKey, KEY_ALGORITHM);
        } finally {
            Arrays.fill(decodedKey, (byte) 0);
        }
    }

    /**
     * 암호문이 IV와 인증 태그를 포함할 수 있는 길이인지 검증합니다.
     */
    private static void validatePayloadLength(byte[] payload) {
        int minimumPayloadLength =
                IV_LENGTH_BYTES
                        + AUTH_TAG_LENGTH_BITS / Byte.SIZE;

        if (payload.length <= minimumPayloadLength) {
            throw new IllegalArgumentException(
                    "이메일 암호문 길이가 올바르지 않습니다."
            );
        }
    }
}
