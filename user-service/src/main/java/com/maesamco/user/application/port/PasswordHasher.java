package com.maesamco.user.application.port;

/**
 * 비밀번호 단방향 해시와 검증 기능을 정의하는 애플리케이션 포트입니다.
 *
 * <p>애플리케이션 계층이 Argon2id와 같은 구체적인 해시 기술에
 * 직접 의존하지 않도록 필요한 기능만 추상화합니다.</p>
 */
public interface PasswordHasher {

    /**
     * 평문 비밀번호를 단방향 해시로 변환합니다.
     *
     * @param rawPassword 평문 비밀번호
     * @return 저장할 비밀번호 해시
     */
    String hash(String rawPassword);

    /**
     * 평문 비밀번호와 저장된 비밀번호 해시가 일치하는지 확인합니다.
     *
     * @param rawPassword 확인할 평문 비밀번호
     * @param passwordHash 저장된 비밀번호 해시
     * @return 일치하면 true
     */
    boolean matches(String rawPassword, String passwordHash);
}
