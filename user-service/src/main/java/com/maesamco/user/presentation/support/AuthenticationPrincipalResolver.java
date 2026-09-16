package com.maesamco.user.presentation.support;

import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import org.springframework.security.core.Authentication;

import java.util.UUID;

/**
 * 인증 객체에서 현재 사용자 식별자를 추출합니다.
 */
public final class AuthenticationPrincipalResolver {

    private AuthenticationPrincipalResolver() {
    }

    /**
     * 인증 principal에서 사용자 식별자를 반환합니다.
     *
     * @param authentication 현재 인증 정보
     * @return 사용자 식별자
     */
    public static UUID requireUserId(
            Authentication authentication
    ) {
        if (
                authentication == null
                        || !authentication.isAuthenticated()
        ) {
            throw new BusinessException(
                    ErrorCode.AUTH_UNAUTHORIZED
            );
        }

        if (!(authentication.getPrincipal() instanceof UUID userId)) {
            throw new BusinessException(
                    ErrorCode.AUTH_INVALID_TOKEN
            );
        }

        return userId;
    }
}
