package com.maesamco.user.application.port;

/**
 * 이메일 암호화와 복호화 기능을 정의하는 애플리케이션 포트입니다.
 *
 * <p>애플리케이션 계층이 AES-GCM과 같은 구체적인 암호화 기술에
 * 직접 의존하지 않도록 필요한 기능만 추상화합니다.</p>
 */
public interface EmailCipher {

    /**
     * 정규화된 이메일을 암호화합니다.
     *
     * @param normalizedEmail 정규화된 이메일
     * @return 저장할 이메일 암호문
     */
    String encrypt(String normalizedEmail);

    /**
     * 저장된 이메일 암호문을 복호화합니다.
     *
     * @param encryptedEmail 이메일 암호문
     * @return 정규화된 이메일
     */
    String decrypt(String encryptedEmail);
}
