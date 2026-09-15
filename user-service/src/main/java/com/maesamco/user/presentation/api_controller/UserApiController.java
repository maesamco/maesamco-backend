package com.maesamco.user.presentation.api_controller;

import com.maesamco.user.application.service.ChangePasswordCommand;
import com.maesamco.user.application.service.ChangePasswordRetryService;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

/**
 * 로그인 사용자의 계정 정보를 관리하는 User API를 제공합니다.
 */
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
@Tag(
        name = "User",
        description = "로그인 사용자의 계정 정보 관리 API"
)
public class UserApiController implements UserApiDocs {

    private static final String REFRESH_TOKEN_COOKIE_NAME =
            "refreshToken";

    private static final String REFRESH_TOKEN_COOKIE_PATH =
            "/api/v1/auth";

    private static final String REFRESH_TOKEN_SAME_SITE =
            "Lax";

    private final ChangePasswordRetryService changePasswordRetryService;

    /**
     * 현재 비밀번호를 확인한 후 새 비밀번호로 변경합니다.
     *
     * <p>변경이 완료되면 사용자의 모든 인증 세션을 무효화하고
     * 현재 클라이언트의 Refresh Token Cookie를 삭제합니다.</p>
     *
     * @param authentication 현재 Access Token 인증 정보
     * @param command 비밀번호 변경 입력값
     * @return 본문이 없는 204 응답
     */
    @Override
    @PatchMapping("/password")
    public ResponseEntity<Void> changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordCommand command
    ) {
        UUID userId =
                requireUserId(
                        authentication
                );

        changePasswordRetryService.changePassword(
                userId,
                command
        );

        ResponseCookie expiredRefreshTokenCookie =
                createExpiredRefreshTokenCookie();

        return ResponseEntity
                .noContent()
                .header(
                        HttpHeaders.SET_COOKIE,
                        expiredRefreshTokenCookie.toString()
                )
                .build();
    }

    /**
     * 인증 principal에서 사용자 식별자를 추출합니다.
     */
    private UUID requireUserId(
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

    /**
     * 브라우저에 저장된 Refresh Token Cookie를 삭제합니다.
     */
    private ResponseCookie createExpiredRefreshTokenCookie() {
        return ResponseCookie
                .from(
                        REFRESH_TOKEN_COOKIE_NAME,
                        ""
                )
                .httpOnly(true)
                .secure(true)
                .sameSite(REFRESH_TOKEN_SAME_SITE)
                .path(REFRESH_TOKEN_COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build();
    }
}
