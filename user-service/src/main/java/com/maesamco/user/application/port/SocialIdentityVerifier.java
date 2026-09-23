package com.maesamco.user.application.port;

import com.maesamco.user.domain.entity.SocialProvider;

/**
 * 외부 Social Provider의 인증 정보를 검증하는 Port입니다.
 *
 * <p>애플리케이션 계층이 Google, Kakao, Naver SDK에
 * 직접 의존하지 않도록 검증 기능을 추상화합니다.</p>
 */
public interface SocialIdentityVerifier {

    /**
     * 이 Verifier가 담당하는 Provider입니다.
     */
    SocialProvider provider();

    /**
     * Provider에서 전달받은 Credential을 검증합니다.
     *
     * @param credential Provider 인증 Credential
     * @return 검증 완료된 사용자 정보
     */
    VerifiedSocialIdentity verify(String credential);
}
