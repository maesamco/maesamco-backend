package com.maesamco.user.application.port;

import java.time.Duration;
import java.util.Objects;

/**
 * 원자적으로 소비한 Social Signup Token의 귀속 정보와 소비 시점의 남은 유효시간입니다(#308).
 *
 * <p>Token 소비 이후 재시도 가능한 이유(예: 동시 가입 경쟁으로 인한 닉네임 중복)로 가입이 실패하면,
 * 원래 만료 시각을 넘기지 않도록 이 남은 유효시간으로만 Token을 복구합니다.</p>
 *
 * @param ticket Token에 귀속된 소셜 인증 정보
 * @param remainingTtl 소비 시점의 남은 유효시간 (알 수 없으면 {@link Duration#ZERO})
 */
public record ConsumedSocialSignupToken(
        SocialSignupTicket ticket,
        Duration remainingTtl
) {

    public ConsumedSocialSignupToken {
        Objects.requireNonNull(
                ticket,
                "소셜 회원가입 Token 귀속 정보는 필수입니다."
        );

        if (remainingTtl == null || remainingTtl.isNegative()) {
            remainingTtl = Duration.ZERO;
        }
    }

    /**
     * 복구할 수 있을 만큼 유효시간이 남아 있는지 확인합니다.
     */
    public boolean hasRemainingTtl() {
        return !remainingTtl.isZero();
    }
}
