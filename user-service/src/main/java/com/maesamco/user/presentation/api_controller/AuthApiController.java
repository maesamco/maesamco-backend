package com.maesamco.user.presentation.api_controller;

import com.maesamco.user.application.service.*;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import com.maesamco.user.global.response.ErrorResponse;
import com.maesamco.user.global.response.SuccessResponse;
import com.maesamco.user.global.security.AccessTokenAuthenticationDetails;
import com.maesamco.user.presentation.support.RefreshTokenCookieFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.util.UUID;

import static com.maesamco.user.presentation.support.AuthenticationPrincipalResolver.requireUserId;
import static com.maesamco.user.presentation.support.RefreshTokenCookieFactory.COOKIE_NAME;


/**
 * 회원가입, 로그인, Refresh Token 재발급 및 로그아웃을 포함한
 * 사용자 인증 API를 제공합니다.
 *
 * <p>Access Token은 응답 본문으로 전달하고,
 * Refresh Token은 HttpOnly Cookie로만 전달합니다.</p>
 */
@Tag(
        name = "Auth",
        description = "이메일 인증, 회원가입 및 로그인 세션 관리 API"
)
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthApiController implements AuthApiDocs {

    private final EmailVerificationService emailVerificationService;
    private final SignUpService signUpService;
    private final LoginService loginService;
    private final RefreshService refreshService;
    private final LogoutService logoutService;
    private final Clock clock;
    private final LogoutAllService logoutAllService;
    private final RefreshTokenCookieFactory refreshTokenCookieFactory;

    /**
     * 회원가입을 위한 이메일 인증 코드를 요청합니다.
     *
     * <p>이미 가입된 이메일인지 여부와 관계없이 동일한
     * 202 Accepted 응답과 응답 구조를 반환합니다.</p>
     *
     * <p>재전송 cooldown 또는 요청 횟수 제한이 적용된 경우에도
     * 외부 응답을 통해 내부 상태를 구분할 수 없도록 동일하게 응답합니다.</p>
     *
     * @param command 이메일 인증 요청 입력값
     * @return 인증 요청 접수 응답
     */
    @Override
    @PostMapping("/email-verifications")
    public ResponseEntity<SuccessResponse<Void>> requestEmailVerification(
            @Valid @RequestBody RequestEmailVerificationCommand command
    ) {
        emailVerificationService.requestVerification(
                command
        );

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(
                        SuccessResponse.empty()
                );
    }

    /**
     * 이메일 인증 코드를 확인하고
     * 회원가입에 사용할 일회용 인증 토큰을 발급합니다.
     *
     * <p>인증 코드가 올바른 경우 짧은 TTL의 회원가입 인증 토큰을 반환하며,
     * 해당 토큰은 이후 회원가입 요청에서 한 번만 사용할 수 있습니다.</p>
     *
     * @param command 이메일 및 인증 코드 확인 입력값
     * @return 회원가입 인증 토큰과 만료 시간
     */
    @Override
    @PostMapping("/email-verifications/confirm")
    public ResponseEntity<SuccessResponse<ConfirmEmailVerificationResult>>
    confirmEmailVerification(
            @Valid @RequestBody ConfirmEmailVerificationCommand command
    ) {
        ConfirmEmailVerificationResult result =
                emailVerificationService.confirmVerification(
                        command
                );

        return ResponseEntity
                .ok(
                        SuccessResponse.success(
                                result
                        )
                );
    }

    /**
     * 신규 사용자를 생성하고 자동 로그인용 인증 정보를 발급합니다.
     *
     * @param command 회원가입 입력값
     * @return 생성된 사용자 정보와 Access Token
     */
    @Override
    @PostMapping("/signup")
    public ResponseEntity<SuccessResponse<SignUpResult>> signUp(
            @Valid @RequestBody SignUpCommand command
    ) {
        SignUpResult result =
                signUpService.signUp(command);

        var refreshTokenCookie =
                refreshTokenCookieFactory.create(
                        result.issuedTokens(),
                        clock
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

        var refreshTokenCookie =
                refreshTokenCookieFactory.create(
                        result.issuedTokens(),
                        clock
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
                    value = COOKIE_NAME,
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

        var refreshTokenCookie =
                refreshTokenCookieFactory.create(
                        result.issuedTokens(),
                        clock
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

        var expiredRefreshTokenCookie =
                refreshTokenCookieFactory.createExpired();

        return ResponseEntity
                .noContent()
                .header(
                        HttpHeaders.SET_COOKIE,
                        expiredRefreshTokenCookie.toString()
                )
                .build();
    }

    /**
     * 현재 사용자에게 발급된 모든 인증 세션을 종료합니다.
     *
     * <p>사용자의 모든 Refresh Token 세션을 제거하고
     * 전체 로그아웃 이전에 발급된 Access Token을 사용자 단위로
     * 무효화한 뒤, 현재 클라이언트의 Refresh Token Cookie를 삭제합니다.</p>
     *
     * @param authentication 현재 Access Token 인증 정보
     * @return 본문이 없는 204 응답
     */
    @Operation(
            summary = "전체 기기 로그아웃",
            description = "현재 사용자에게 발급된 모든 인증 세션을 종료합니다. "
                    + "모든 Refresh Token 세션을 제거하고, "
                    + "전체 로그아웃 이전에 발급된 Access Token을 "
                    + "사용자 단위로 무효화합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "전체 기기 로그아웃 성공",
                    headers = @Header(
                            name = "Set-Cookie",
                            description = "Refresh Token Cookie 삭제 "
                                    + "(Max-Age=0, Secure, HttpOnly, "
                                    + "SameSite=Lax, Path=/api/v1/auth)",
                            schema = @Schema(
                                    type = "string"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "AUTH_UNAUTHORIZED",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ErrorResponse.class
                            )
                    )
            )
    })
    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(
            Authentication authentication
    ) {
        UUID userId =
                requireUserId(
                        authentication
                );

        logoutAllService.logoutAll(
                new LogoutAllCommand(
                        userId
                )
        );

        var expiredRefreshTokenCookie =
                refreshTokenCookieFactory.createExpired();

        return ResponseEntity
                .noContent()
                .header(
                        HttpHeaders.SET_COOKIE,
                        expiredRefreshTokenCookie.toString()
                )
                .build();
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
}