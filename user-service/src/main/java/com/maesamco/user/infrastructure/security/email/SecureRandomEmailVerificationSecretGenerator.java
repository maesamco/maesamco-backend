package com.maesamco.user.infrastructure.security.email;

import com.maesamco.user.application.port.EmailVerificationSecretGenerator;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;

/**
 * {@link SecureRandom}을 이용해 이메일 인증 과정에서 사용하는
 * 인증 코드와 회원가입 인증 토큰을 생성하는 Adapter입니다.
 *
 * <p>인증 코드는 사용자가 직접 입력할 수 있도록 6자리 숫자로 생성하고,
 * 회원가입 인증 토큰은 추측이 어렵도록 256비트 보안 난수로 생성합니다.</p>
 */
@Component
public class SecureRandomEmailVerificationSecretGenerator
        implements EmailVerificationSecretGenerator {

    private static final int VERIFICATION_CODE_BOUND =
            1_000_000;

    private static final int SIGNUP_TOKEN_BYTE_LENGTH =
            32;

    private final SecureRandom secureRandom =
            new SecureRandom();

    /**
     * 000000부터 999999까지의 6자리 숫자 인증 코드를 생성합니다.
     *
     * @return 6자리 숫자 인증 코드
     */
    @Override
    public String generateVerificationCode() {
        int code =
                secureRandom.nextInt(
                        VERIFICATION_CODE_BOUND
                );

        return String.format(
                Locale.ROOT,
                "%06d",
                code
        );
    }

    /**
     * 회원가입 인증에 사용할 256비트 일회성 토큰을 생성합니다.
     *
     * <p>URL-safe Base64 형식으로 인코딩하며
     * 불필요한 padding 문자는 포함하지 않습니다.</p>
     *
     * @return URL-safe Base64 회원가입 인증 토큰
     */
    @Override
    public String generateSignupToken() {
        byte[] randomBytes =
                new byte[SIGNUP_TOKEN_BYTE_LENGTH];

        try {
            secureRandom.nextBytes(randomBytes);

            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(randomBytes);
        } finally {
            Arrays.fill(
                    randomBytes,
                    (byte) 0
            );
        }
    }
}
