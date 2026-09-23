package com.maesamco.user.presentation.api_controller;

import com.maesamco.user.application.service.SocialLoginResult;
import com.maesamco.user.global.response.ErrorResponse;
import com.maesamco.user.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;

/**
 * 소셜 로그인 API의 OpenAPI 계약입니다.
 */
public interface SocialAuthApiDocs {

    @Operation(
            summary = "Google 소셜 로그인",
            description = """
                    Google ID Token을 검증하여 소셜 로그인을 처리합니다.

                    이미 Google 소셜 계정이 등록된 사용자는 로그인을 완료하고
                    Access Token과 Refresh Token Cookie를 발급합니다.

                    처음 인증한 Google 사용자는 User를 즉시 생성하지 않고
                    SIGNUP_REQUIRED와 socialSignupToken을 반환합니다.

                    동일 이메일로 일반 회원가입된 계정이 존재하는 경우
                    자동 연결하지 않고 소셜 회원가입을 거부합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description =
                            "Google 로그인 성공 또는 추가 회원가입 필요",
                    headers = @Header(
                            name = "Set-Cookie",
                            description =
                                    "기존 소셜 회원 로그인 성공 시에만 "
                                            + "Refresh Token HttpOnly Cookie 발급",
                            schema = @Schema(
                                    type = "string"
                            )
                    ),
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "400",
                    description =
                            "INVALID_INPUT_VALUE 또는 "
                                    + "SOCIAL_PROVIDER_NOT_SUPPORTED",
                    content = @Content(
                            schema = @Schema(
                                    implementation =
                                            ErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description =
                            "AUTH_INVALID_TOKEN 또는 "
                                    + "SOCIAL_EMAIL_NOT_VERIFIED",
                    content = @Content(
                            schema = @Schema(
                                    implementation =
                                            ErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description =
                            "SOCIAL_SIGNUP_EMAIL_ALREADY_EXISTS "
                                    + "— 이미 다른 방식으로 가입된 이메일",
                    content = @Content(
                            schema = @Schema(
                                    implementation =
                                            ErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "503",
                    description =
                            "SOCIAL_PROVIDER_UNAVAILABLE "
                                    + "— Google 인증 서비스 일시 장애",
                    content = @Content(
                            schema = @Schema(
                                    implementation =
                                            ErrorResponse.class
                            )
                    )
            )
    })
    ResponseEntity<SuccessResponse<SocialLoginResult>>
    googleLogin(
            GoogleSocialLoginRequest request
    );
}
