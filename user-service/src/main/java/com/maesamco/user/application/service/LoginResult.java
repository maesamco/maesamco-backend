package com.maesamco.user.application.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.maesamco.user.application.port.IssuedTokens;
import com.maesamco.user.domain.entity.LearningLevel;
import com.maesamco.user.domain.entity.UserRole;
import com.maesamco.user.domain.entity.UserStatus;

import java.util.Objects;
import java.util.UUID;

/**
 * 로그인 완료 후 클라이언트에 전달할 사용자 정보와
 * 인증 토큰 정보를 담습니다.
 *
 * <p>Access Token은 API 응답 본문에 포함하지만,
 * Refresh Token은 HttpOnly Cookie 생성에만 사용합니다.</p>
 *
 * @param user 로그인한 사용자 공개 정보
 * @param accessToken 응답 본문에 전달할 Access Token
 * @param accessTokenExpiresIn Access Token 만료까지 남은 시간(초)
 * @param issuedTokens Controller의 Refresh Token Cookie 생성에 사용할 전체 토큰 정보
 */
public record LoginResult(
        UserInfo user,
        String accessToken,
        long accessTokenExpiresIn,
        IssuedTokens issuedTokens
) {

    /**
     * 로그인 결과의 필수값과 Access Token 만료 시간을 검증합니다.
     */
    public LoginResult {
        Objects.requireNonNull(
                user,
                "사용자 정보는 필수입니다."
        );
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
        return "LoginResult[user="
                + user
                + ", accessToken=[PROTECTED]"
                + ", accessTokenExpiresIn="
                + accessTokenExpiresIn
                + ", issuedTokens=[PROTECTED]]";
    }

    /**
     * 로그인 성공 응답에 포함할 사용자 공개 정보입니다.
     *
     * <p>이메일은 암호화 저장값을 복호화한 정규화 이메일이며,
     * 로그에는 노출하지 않습니다.</p>
     *
     * @param userId 사용자 식별자
     * @param email 정규화된 사용자 이메일
     * @param nickname 사용자 닉네임
     * @param role 사용자 권한
     * @param status 사용자 상태
     * @param learningLevel Java 학습 수준
     */
    public record UserInfo(
            UUID userId,
            String email,
            String nickname,
            UserRole role,
            UserStatus status,
            LearningLevel learningLevel
    ) {

        /**
         * 응답 사용자 정보의 필수값을 검증합니다.
         */
        public UserInfo {
            Objects.requireNonNull(
                    userId,
                    "사용자 식별자는 필수입니다."
            );
            Objects.requireNonNull(
                    email,
                    "사용자 이메일은 필수입니다."
            );
            Objects.requireNonNull(
                    nickname,
                    "사용자 닉네임은 필수입니다."
            );
            Objects.requireNonNull(
                    role,
                    "사용자 권한은 필수입니다."
            );
            Objects.requireNonNull(
                    status,
                    "사용자 상태는 필수입니다."
            );
            Objects.requireNonNull(
                    learningLevel,
                    "학습 수준은 필수입니다."
            );
        }

        /**
         * 이메일이 로그에 노출되지 않도록 민감값을 숨깁니다.
         */
        @Override
        public String toString() {
            return "UserInfo[userId="
                    + userId
                    + ", email=[PROTECTED]"
                    + ", nickname="
                    + nickname
                    + ", role="
                    + role
                    + ", status="
                    + status
                    + ", learningLevel="
                    + learningLevel
                    + "]";
        }
    }
}
