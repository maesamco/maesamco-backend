package com.maesamco.user.application.service;

import java.time.Duration;

/**
 * 이메일 인증 흐름에서 사용하는 보안 및 만료 정책입니다.
 *
 * <p>인증 코드, 회원가입 인증 토큰, 재전송 cooldown,
 * 인증 실패 횟수 등의 정책 값을 한 곳에서 관리합니다.</p>
 *
 * <p>{@code maxVerificationAttempts}는 이메일별 누적 한도가 아니라
 * 현재 발급된 인증 코드 1개당 최대 확인 시도 횟수입니다.
 * 재전송으로 새 인증 코드가 발급되면 challenge가 새로 생성되므로
 * 확인 시도 횟수도 다시 0부터 시작합니다.</p>
 */
public record EmailVerificationPolicy(
        Duration challengeTtl,
        Duration signupTokenTtl,
        Duration resendCooldown,
        Duration requestLimitWindow,
        int maxVerificationAttempts,
        int maxRequestsPerWindow
) {

    public EmailVerificationPolicy {
        requirePositive(
                challengeTtl,
                "인증 코드 TTL"
        );
        requirePositive(
                signupTokenTtl,
                "회원가입 인증 토큰 TTL"
        );
        requirePositive(
                resendCooldown,
                "재전송 cooldown"
        );
        requirePositive(
                requestLimitWindow,
                "이메일 인증 요청 제한 기간"
        );

        if (maxVerificationAttempts <= 0) {
            throw new IllegalArgumentException(
                    "최대 인증 시도 횟수는 1 이상이어야 합니다."
            );
        }

        if (maxRequestsPerWindow <= 0) {
            throw new IllegalArgumentException(
                    "기간당 최대 인증 요청 횟수는 1 이상이어야 합니다."
            );
        }
    }

    private static void requirePositive(
            Duration duration,
            String fieldName
    ) {
        if (duration == null
                || duration.isZero()
                || duration.isNegative()) {
            throw new IllegalArgumentException(
                    fieldName + "은 0보다 커야 합니다."
            );
        }
    }
}
