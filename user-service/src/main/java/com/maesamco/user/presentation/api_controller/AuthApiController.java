package com.maesamco.user.presentation.api_controller;

import com.maesamco.user.application.port.IssuedTokens;
import com.maesamco.user.application.service.LoginCommand;
import com.maesamco.user.application.service.LoginResult;
import com.maesamco.user.application.service.LoginService;
import com.maesamco.user.application.service.SignUpCommand;
import com.maesamco.user.application.service.SignUpResult;
import com.maesamco.user.application.service.SignUpService;
import com.maesamco.user.global.response.SuccessResponse;
import com.maesamco.user.global.security.TokenExpirationCalculator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;

/**
 * 회원가입과 로그인을 포함한 사용자 인증 API를 제공합니다.
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
     * <p>Access Token은 응답 본문으로 전달하고,
     * Refresh Token은 HttpOnly Cookie로 전달합니다.</p>
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
     * Refresh Token을 HttpOnly Cookie로 생성합니다.
     *
     * <p>Refresh Token 원문은 API 응답 본문에 포함하지 않고
     * 인증 API 요청에서만 사용할 수 있도록 Cookie Path를 제한합니다.</p>
     *
     * <p>Cookie Max-Age는 공통 TokenExpirationCalculator를 사용해
     * Refresh Token의 실제 만료 시각 기준으로 계산합니다.</p>
     *
     * @param issuedTokens 발급된 인증 토큰 정보
     * @return Refresh Token Cookie
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
}
