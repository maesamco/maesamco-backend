package com.maesamco.user.application.port;

import java.time.Duration;

/**
 * 소셜 인증 후 회원가입에 사용하는 일회성 Token 상태를 저장합니다.
 *
 * <p>Token 원문은 저장하지 않고 단방향 해시만 Key로 사용합니다.</p>
 */
public interface SocialSignupTokenStore {

    /**
     * 소셜 회원가입 Token과 인증된 이메일의 귀속 관계를 저장합니다.
     *
     * @param tokenHash 소셜 회원가입 Token 해시
     * @param emailLookupHash 인증된 이메일 조회 해시
     * @param ttl Token 유효시간
     */
    void save(
            String tokenHash,
            String emailLookupHash,
            Duration ttl
    );

    /**
     * Token이 해당 이메일에 발급된 것인지 확인하고 한 번만 소비합니다.
     *
     * @param tokenHash 소셜 회원가입 Token 해시
     * @param emailLookupHash 인증된 이메일 조회 해시
     * @return 소비에 성공하면 true
     */
    boolean consume(
            String tokenHash,
            String emailLookupHash
    );
}
