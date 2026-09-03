package com.maesamco.user.infrastructure.security.email;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 이메일 암호화와 조회 해시에 사용할 비밀 키 설정입니다.
 *
 * <p>두 키는 서로 다른 목적으로 사용하므로 반드시 별도로 관리하며,
 * Base64로 인코딩된 값을 환경변수에서 전달받습니다.</p>
 *
 * @param encryptionKey AES-256-GCM 암호화 키
 * @param lookupHmacKey HMAC-SHA256 조회 해시 키
 */
@Validated
@ConfigurationProperties(prefix = "security.email")
public record EmailSecurityProperties(
        @NotBlank String encryptionKey,
        @NotBlank String lookupHmacKey
) {

    /**
     * 설정 객체가 로그에 출력되더라도 비밀 키 원문을 노출하지 않습니다.
     */
    @Override
    public String toString() {
        return "EmailSecurityProperties["
                + "encryptionKey=******, "
                + "lookupHmacKey=******"
                + "]";
    }
}
