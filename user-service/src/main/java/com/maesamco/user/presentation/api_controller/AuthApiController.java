package com.maesamco.user.presentation.api_controller;

import com.maesamco.user.application.port.IssuedTokens;
import com.maesamco.user.application.service.*;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import com.maesamco.user.global.response.SuccessResponse;
import com.maesamco.user.global.security.AccessTokenAuthenticationDetails;
import com.maesamco.user.global.security.TokenExpirationCalculator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

/**
 * 회원가입, 로그인, Refresh Token 재발급 및 로그아웃을 포함한
 * 사용자 인증 API를 제공합니다.
 *
 * <p>Access Token은 응답 본문으로 전달하고,
 * Refresh Token은 HttpOnly Cookie로만 전달합니다.</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthApiController {

    private static final String REFRESH_TOKEN_COOKIE_NAME =
            "refreshToken";

    private static final String REFRESH_TOKEN_COOKIE_PATH =
            "/api/v1/auth";

    private static final String REFRESH_TOKEN_SAME_SITE =
            "Lax";

    private final SignUpService signUpService;
    private final LoginService loginService;
    private final RefreshService refreshService;
    private final LogoutService logoutService;
    private final Clock clock;

    /**
     * 신규 사용자를 생성하고 자동 로그인용 인증 정보를 발급합니다.
     *
     * @param command 회원가입 입력값
     * @return 생성된 사용자 정보와 Access Token
     */
    @PostMapping("/signup")
    public ResponseEntity<SuccessResponse<SignUpResult>> signUp(
            @Valid @RequestBody SignUpCommand command
    ) {
        SignUpResult result =
                signUpService.signUp(command);

        ResponseCookie refreshTokenCookie =
                createRefreshTokenCookie(
                        result.issuedTokens()
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshTokenCookie.toString()
                )
                .body(
                        SuccessResponse.success(result)
                );
    }

    /**
     * 이메일과 비밀번호를 이용해 사용자를 인증하고
     * 새로운 로그인 인증 세션을 생성합니다.
     *
     * @param command 로그인 입력값
     * @return 로그인 사용자 정보와 Access Token
     */
    @PostMapping("/login")
    public ResponseEntity<SuccessResponse<LoginResult>> login(
            @Valid @RequestBody LoginCommand command
    ) {
        LoginResult result =
                loginService.login(command);

        ResponseCookie refreshTokenCookie =
                createRefreshTokenCookie(
                        result.issuedTokens()
                );

        return ResponseEntity
                .ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshTokenCookie.toString()
                )
                .body(
                        SuccessResponse.success(result)
                );
    }

    /**
     * HttpOnly Cookie의 Refresh Token을 검증하고
     * Access Token 및 Refresh Token을 새롭게 발급합니다.
     *
     * @param refreshToken HttpOnly Cookie로 전달된 Refresh Token
     * @return 새로 발급된 Access Token
     */
    @PostMapping("/refresh")
    public ResponseEntity<SuccessResponse<RefreshResult>> refresh(
            @CookieValue(
                    value = REFRESH_TOKEN_COOKIE_NAME,
                    required = false
            )
            String refreshToken
    ) {
        RefreshResult result =
                refreshService.refresh(
                        new RefreshCommand(
                                refreshToken
                        )
                );

        ResponseCookie refreshTokenCookie =
                createRefreshTokenCookie(
                        result.issuedTokens()
                );

        return ResponseEntity
                .ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshTokenCookie.toString()
                )
                .body(
                        SuccessResponse.success(result)
                );
    }

    /**
     * 현재 요청에 사용된 인증 세션을 종료합니다.
     *
     * <p>현재 AuthSession을 삭제하고 세션 블랙리스트를 등록한 뒤,
     * Refresh Token Cookie를 기존 Cookie와 동일한 속성으로 삭제합니다.</p>
     *
     * @param authentication 현재 Access Token 인증 정보
     * @return 본문이 없는 204 응답
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            Authentication authentication
    ) {
        UUID userId =
                requireUserId(authentication);

        AccessTokenAuthenticationDetails details =
                requireAuthenticationDetails(
                        authentication
                );

        logoutService.logout(
                new LogoutCommand(
                        userId,
                        details.sessionId(),
                        details.expiresAt()
                )
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
     * 인증 객체에서 로그아웃에 필요한 Access Token 정보를 추출합니다.
     */
    private AccessTokenAuthenticationDetails
    requireAuthenticationDetails(
            Authentication authentication
    ) {
        if (!(
                authentication.getDetails()
                        instanceof AccessTokenAuthenticationDetails details
        )) {
            throw new BusinessException(
                    ErrorCode.AUTH_INVALID_TOKEN
            );
        }

        return details;
    }

    /**
     * Refresh Token을 HttpOnly Cookie로 생성합니다.
     */
    private ResponseCookie createRefreshTokenCookie(
            IssuedTokens issuedTokens
    ) {
        long maxAgeSeconds =
                TokenExpirationCalculator.remainingSeconds(
                        clock.instant(),
                        issuedTokens.refreshTokenExpiresAt()
                );

        return ResponseCookie
                .from(
                        REFRESH_TOKEN_COOKIE_NAME,
                        issuedTokens.refreshToken()
                )
                .httpOnly(true)
                .secure(true)
                .sameSite(REFRESH_TOKEN_SAME_SITE)
                .path(REFRESH_TOKEN_COOKIE_PATH)
                .maxAge(
                        Duration.ofSeconds(maxAgeSeconds)
                )
                .build();
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
