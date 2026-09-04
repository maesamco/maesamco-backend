package com.maesamco.user.application.port;

/**
 * 정규화된 이메일의 조회용 해시 생성 기능을 정의하는
 * 애플리케이션 포트입니다.
 *
 * <p>동일한 이메일에는 항상 같은 결과를 생성하여
 * 암호화된 이메일을 복호화하지 않고도 조회할 수 있게 합니다.</p>
 */
public interface EmailLookupHasher {

    /**
     * 정규화된 이메일의 조회용 해시를 생성합니다.
     *
     * @param normalizedEmail 정규화된 이메일
     * @return 이메일 조회용 해시
     */
    String hash(String normalizedEmail);
}
