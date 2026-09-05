package com.maesamco.user.application.port;

/**
 * 이메일 암호화와 복호화 기능을 정의하는 애플리케이션 포트입니다.
 *
 * <p>애플리케이션 계층이 AES-GCM과 같은 구체적인 암호화 기술에
 * 직접 의존하지 않도록 필요한 기능만 추상화합니다.</p>
 *
 * <p>암호화 또는 복호화 과정에서 암호화 인프라 오류가 발생한 경우
 * 구현체는 {@link IllegalStateException}을 발생시킬 수 있습니다.</p>
 */
public interface EmailCipher {

    /**
     * 정규화된 이메일을 암호화합니다.
     *
     * @param normalizedEmail 정규화된 이메일
     * @return 저장할 이메일 암호문
     * @throws IllegalStateException 암호화 처리 중 오류가 발생한 경우
     */
    String encrypt(String normalizedEmail);

    /**
     * 저장된 이메일 암호문을 복호화합니다.
     *
     * <p>지원하지 않는 암호문 형식, Base64 디코딩 실패,
     * 암호문 변조 또는 AES-GCM 인증 태그 검증 실패 등
     * 복호화를 완료할 수 없는 경우 예외가 발생할 수 있습니다.</p>
     *
     * @param encryptedEmail 이메일 암호문
     * @return 정규화된 이메일
     * @throws IllegalStateException 복호화 처리 중 오류가 발생한 경우
     */
    String decrypt(String encryptedEmail);
}
