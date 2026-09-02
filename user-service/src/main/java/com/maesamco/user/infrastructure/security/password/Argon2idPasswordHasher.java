package com.maesamco.user.infrastructure.security.password;

import com.maesamco.user.application.port.PasswordHasher;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Spring Security의 Argon2id 구현을 이용해 비밀번호를 해시하고 검증합니다.
 *
 * <p>비밀번호 해시마다 새로운 Salt가 생성되며, 평문 비밀번호는
 * 저장하거나 로그에 기록하지 않습니다.</p>
 *
 * <p>비밀번호는 이메일과 달리 앞뒤 공백 제거 또는 대소문자 변환을
 * 수행하지 않고 전달받은 원문 그대로 해시합니다.</p>
 */
@Component
public class Argon2idPasswordHasher implements PasswordHasher {

    private final PasswordEncoder passwordEncoder;

    /**
     * 설정에 등록된 Argon2id 비밀번호 인코더를 주입받습니다.
     *
     * @param passwordEncoder 비밀번호 해시와 검증을 담당하는 인코더
     */
    public Argon2idPasswordHasher(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String hash(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "해시할 비밀번호는 필수입니다."
            );
        }

        return passwordEncoder.encode(rawPassword);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(
            String rawPassword,
            String passwordHash
    ) {
        if (rawPassword == null
                || passwordHash == null
                || passwordHash.isBlank()) {
            return false;
        }

        return passwordEncoder.matches(
                rawPassword,
                passwordHash
        );
    }
}
