package com.maesamco.user.application.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.maesamco.user.application.port.IssuedTokens;

import java.util.Objects;

/**
 * Refresh Token Rotation 완료 후 클라이언트에 전달할
 * 새로운 인증 토큰 정보를 담습니다.
 *
 * <p>Access Token은 API 응답 본문에 포함하지만,
 * 새 Refresh Token은 HttpOnly Cookie 생성에만 사용합니다.</p>
 *
 * @param accessToken 새로 발급된 Access Token
 * @param accessTokenExpiresIn Access Token 만료까지 남은 시간(초)
 * @param issuedTokens Controller의 새 Refresh Token Cookie 생성에 사용할 전체 토큰 정보
 */
public record RefreshResult(
        String accessToken,
        long accessTokenExpiresIn,
        IssuedTokens issuedTokens
) {

    /**
     * Refresh 결과의 필수값과 Access Token 만료 시간을 검증합니다.
     */
    public RefreshResult {
        Objects.requireNonNull(
                accessToken,
                "Access Token은 필수입니다."
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
     * Refresh Token을 포함한 전체 토큰 정보는
     * JSON 응답 본문에 직렬화하지 않습니다.
     *
     * <p>Controller가 HttpOnly Cookie를 생성할 때만 사용합니다.</p>
     */
    @JsonIgnore
    public IssuedTokens issuedTokens() {
        return issuedTokens;
    }

    /**
     * Access Token과 내부 발급 토큰 정보가
     * 로그에 노출되지 않도록 민감값을 숨깁니다.
     */
    @Override
    public String toString() {
        return "RefreshResult[accessToken=[PROTECTED]"
                + ", accessTokenExpiresIn="
                + accessTokenExpiresIn
                + ", issuedTokens=[PROTECTED]]";
    }
}
