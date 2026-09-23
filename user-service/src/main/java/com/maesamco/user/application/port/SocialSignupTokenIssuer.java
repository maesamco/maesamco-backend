package com.maesamco.user.application.port;

/**
 * 소셜 인증을 완료한 신규 사용자의
 * 후속 회원가입을 위한 임시 Token 발급 Port입니다.
 */
public interface SocialSignupTokenIssuer {

    /**
     * 검증된 Social Identity를 기반으로
     * 단기 Social Signup Token을 발급합니다.
     *
     * @param identity 검증 완료된 소셜 사용자 정보
     * @return 클라이언트에 전달할 임시 가입 Token
     */
    String issue(
            VerifiedSocialIdentity identity
    );
}
