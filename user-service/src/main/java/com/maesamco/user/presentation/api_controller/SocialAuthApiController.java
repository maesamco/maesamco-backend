package com.maesamco.user.presentation.api_controller;

import com.maesamco.user.application.service.SocialLoginCommand;
import com.maesamco.user.application.service.SocialLoginResult;
import com.maesamco.user.application.service.SocialLoginService;
import com.maesamco.user.application.service.SocialLoginStatus;
import com.maesamco.user.application.service.SignUpResult;
import com.maesamco.user.application.service.SocialSignUpService;
import com.maesamco.user.domain.entity.SocialProvider;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import com.maesamco.user.global.response.SuccessResponse;
import com.maesamco.user.presentation.support.RefreshTokenCookieFactory;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;

/**
 * 외부 Social Provider를 이용한 인증 API를 제공합니다.
 *
 * <p>Provider별 인증 Credential의 검증은 SocialLoginService와
 * SocialIdentityVerifier 구현체에 위임합니다.</p>
 */
@RestController
@RequestMapping(
        "/api/v1/auth/social"
)
@RequiredArgsConstructor
public class SocialAuthApiController
        implements SocialAuthApiDocs {

    private final SocialLoginService socialLoginService;

    private final SocialSignUpService socialSignUpService;

    private final RefreshTokenCookieFactory
            refreshTokenCookieFactory;

    private final Clock clock;

    /**
     * Google ID Token을 검증하여
     * 기존 회원 로그인 또는 신규 회원가입 진입을 처리합니다.
     *
     * <p>기존 소셜 회원에게만 Refresh Token Cookie를 발급합니다.</p>
     *
     * <p>SIGNUP_REQUIRED 상태에서는 아직 MAESAMCO User와
     * AuthSession이 존재하지 않으므로 인증 Cookie를 발급하지 않습니다.</p>
     */
    @Override
    @PostMapping("/google")
    public ResponseEntity<
            SuccessResponse<SocialLoginResult>
            > googleLogin(
            @Valid
            @RequestBody
            GoogleSocialLoginRequest request
    ) {
        SocialLoginResult result =
                socialLoginService.login(
                        new SocialLoginCommand(
                                SocialProvider.GOOGLE,
                                request.idToken()
                        )
                );

        if (
                result.status()
                        == SocialLoginStatus.SIGNUP_REQUIRED
        ) {
            return ResponseEntity
                    .ok(
                            SuccessResponse.success(
                                    result
                            )
                    );
        }

        /*
         * AUTHENTICATED인데 내부 Token 정보가 없다면
         * 정상적인 서비스 상태가 아니므로 Cookie를 생성해서는 안 됩니다.
         */
        if (result.issuedTokens() == null) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_SERVER_ERROR
            );
        }

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
                        SuccessResponse.success(
                                result
                        )
                );
    }

    /**
     * Google 소셜 로그인에서 SIGNUP_REQUIRED를 받은 신규 사용자의 회원가입을 완료합니다(#308).
     *
     * <p>socialSignupToken에 귀속된 Google 인증 정보로 User와 SocialAccount를 생성하고,
     * 일반 회원가입과 동일하게 자동 로그인(Access Token + Refresh Token Cookie)을 처리합니다.</p>
     *
     * <p>가입이 완료된 사용자는 이후 {@code POST /google}에서 AUTHENTICATED로 로그인합니다.</p>
     */
    @Override
    @PostMapping("/google/signup")
    public ResponseEntity<
            SuccessResponse<SignUpResult>
            > googleSignUp(
            @Valid
            @RequestBody
            GoogleSocialSignUpRequest request
    ) {
        SignUpResult result =
                socialSignUpService.signUp(
                        request.toCommand()
                );

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
                        SuccessResponse.success(
                                result
                        )
                );
    }
}
