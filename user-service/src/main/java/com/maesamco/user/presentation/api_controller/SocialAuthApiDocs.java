package com.maesamco.user.presentation.api_controller;

import com.maesamco.user.application.service.SignUpResult;
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

    @Operation(
            summary = "Google 소셜 신규 회원가입 완료",
            description = """
                    Google 소셜 로그인에서 SIGNUP_REQUIRED와 함께 받은
                    socialSignupToken으로 신규 회원가입을 완료합니다.

                    이메일과 Google 사용자 식별자는 요청으로 받지 않고,
                    Token에 귀속된 Google 인증 정보만 사용합니다.
                    User 생성과 Google 소셜 계정 연결은 하나의 트랜잭션으로 처리합니다.

                    socialSignupToken은 가입에 성공하면 즉시 소멸되어 재사용할 수 없습니다.
                    닉네임 중복처럼 입력을 고쳐 재시도할 수 있는 오류에서는 Token이 유지됩니다.

                    가입 완료 후 Access Token과 Refresh Token HttpOnly Cookie를 발급하며,
                    이후에는 Google 소셜 로그인 API로 로그인합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "소셜 회원가입 및 자동 로그인 성공",
                    headers = @Header(
                            name = "Set-Cookie",
                            description = "Refresh Token HttpOnly Cookie",
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
                                    + "SOCIAL_SIGNUP_TOKEN_INVALID "
                                    + "— Token 만료·재사용·위조",
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
                            "USER_DUPLICATE_NICKNAME, "
                                    + "SOCIAL_ACCOUNT_ALREADY_LINKED "
                                    + "— 이미 가입된 Google 계정, 또는 "
                                    + "SOCIAL_SIGNUP_EMAIL_ALREADY_EXISTS "
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
                            "SIGNUP_AUTO_LOGIN_FAILED "
                                    + "— 가입은 완료됐지만 자동 로그인 실패(소셜 로그인으로 다시 로그인)",
                    content = @Content(
                            schema = @Schema(
                                    implementation =
                                            ErrorResponse.class
                            )
                    )
            )
    })
    ResponseEntity<SuccessResponse<SignUpResult>>
    googleSignUp(
            GoogleSocialSignUpRequest request
    );
}
