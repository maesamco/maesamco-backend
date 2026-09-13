package com.maesamco.user.infrastructure.security.email;

import com.maesamco.user.application.service.EmailVerificationPolicy;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 이메일 인증에 사용할 설정입니다.
 *
 * @param challengeTtl            인증 코드 유효시간
 * @param signupTokenTtl          회원가입 인증 토큰 유효시간
 * @param resendCooldown          인증 메일 재전송 대기시간
 * @param requestLimitWindow      인증 요청 횟수를 계산할 기간
 * @param maxVerificationAttempts 인증 코드 최대 확인 시도 횟수
 * @param maxRequestsPerWindow    제한 기간 내 최대 인증 요청 횟수
 */
@Validated
@ConfigurationProperties(prefix = "security.email-verification")
public record EmailVerificationProperties(
        @NotNull Duration challengeTtl,
        @NotNull Duration signupTokenTtl,
        @NotNull Duration resendCooldown,
        @NotNull Duration requestLimitWindow,
        @Min(1) int maxVerificationAttempts,
        @Min(1) int maxRequestsPerWindow
) {

    /**
     * 설정값을 애플리케이션 계층의 이메일 인증 정책으로 변환합니다.
     */
    public EmailVerificationPolicy toPolicy() {
        return new EmailVerificationPolicy(
                challengeTtl,
                signupTokenTtl,
                resendCooldown,
                requestLimitWindow,
                maxVerificationAttempts,
                maxRequestsPerWindow
        );
    }
}
