package com.maesamco.user.application.service;

import com.maesamco.user.application.port.IssuedTokens;

import java.util.Objects;
import java.util.UUID;

/**
 * 새로 발급한 인증 세션의 결과입니다(#339).
 *
 * <p>각 로그인·회원가입 서비스는 이 값으로 자기 응답 객체를 만듭니다.</p>
 *
 * @param sessionId 저장된 인증 세션 식별자
 * @param issuedTokens 발급된 Access Token·Refresh Token 정보
 * @param accessTokenExpiresIn Access Token 만료까지 남은 시간(초)
 */
public record IssuedAuthSession(
        UUID sessionId,
        IssuedTokens issuedTokens,
        long accessTokenExpiresIn
) {

    public IssuedAuthSession {
        Objects.requireNonNull(
                sessionId,
                "인증 세션 식별자는 필수입니다."
        );

        Objects.requireNonNull(
                issuedTokens,
                "발급된 토큰은 필수입니다."
        );

        if (accessTokenExpiresIn < 0) {
            throw new IllegalArgumentException(
                    "Access Token 만료 시간은 0 이상이어야 합니다."
            );
        }
    }

    /**
     * 토큰 원문이 로그에 노출되지 않도록 숨깁니다.
     */
    @Override
    public String toString() {
        return "IssuedAuthSession[sessionId="
                + sessionId
                + ", issuedTokens=[PROTECTED]"
                + ", accessTokenExpiresIn="
                + accessTokenExpiresIn
                + "]";
    }
}
