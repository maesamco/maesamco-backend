package com.maesamco.user.presentation.api_controller;

import com.maesamco.user.application.service.ConfirmEmailVerificationCommand;
import com.maesamco.user.application.service.ConfirmEmailVerificationResult;
import com.maesamco.user.application.service.RequestEmailVerificationCommand;
import com.maesamco.user.application.service.SignUpCommand;
import com.maesamco.user.application.service.SignUpResult;
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
 * 이메일 인증과 회원가입 API의 OpenAPI 계약입니다.
 *
 * <p>세 API는 인증 없이 호출할 수 있으며, 오류 응답은 공통
 * {@link ErrorResponse} 형식을 사용합니다.</p>
 */
public interface AuthApiDocs {

    @Operation(
            summary = "이메일 인증 코드 요청",
            description = "회원가입에 사용할 이메일 인증 코드 발송을 요청합니다. "
                    + "가입 여부, 재전송 대기 시간 및 이메일별 요청 제한 상태와 관계없이 "
                    + "동일한 202 Accepted 응답을 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "202",
                    description = "이메일 인증 요청 접수",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "INVALID_INPUT_VALUE — 이메일 형식 등 요청값 검증 실패",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ErrorResponse.class
                            )
                    )
            )
    })
    ResponseEntity<SuccessResponse<Void>> requestEmailVerification(
            RequestEmailVerificationCommand command
    );

    @Operation(
            summary = "이메일 인증 코드 확인",
            description = "이메일로 전달된 6자리 인증 코드를 확인합니다. "
                    + "성공하면 회원가입 요청에서 한 번만 사용할 수 있는 signupToken과 "
                    + "만료까지 남은 시간(초)을 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "이메일 인증 성공 및 signupToken 발급",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "INVALID_INPUT_VALUE, EMAIL_VERIFICATION_INVALID_CODE "
                            + "또는 EMAIL_VERIFICATION_EXPIRED",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED "
                            + "— 인증 시도 횟수 초과",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ErrorResponse.class
                            )
                    )
            )
    })
    ResponseEntity<SuccessResponse<ConfirmEmailVerificationResult>>
    confirmEmailVerification(
            ConfirmEmailVerificationCommand command
    );

    @Operation(
            summary = "회원가입",
            description = "이메일 인증을 완료한 신규 학습자 계정을 생성합니다. "
                    + "이메일에 바인딩된 유효한 일회용 signupToken이 필요합니다. "
                    + "Access Token은 응답 본문으로 반환하고 Refresh Token은 "
                    + "응답 본문에 포함하지 않고 HttpOnly Cookie로 전달합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "회원가입 및 자동 로그인 성공",
                    headers = @Header(
                            name = "Set-Cookie",
                            description = "Refresh Token HttpOnly Cookie "
                                    + "(Secure, SameSite=Lax, Path=/api/v1/auth)",
                            schema = @Schema(type = "string")
                    ),
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "INVALID_INPUT_VALUE 또는 "
                            + "SIGNUP_VERIFICATION_TOKEN_INVALID",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "USER_DUPLICATE_EMAIL 또는 "
                            + "USER_DUPLICATE_NICKNAME",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "API Gateway의 회원가입 요청 제한 초과 "
                            + "— 응답 본문 없이 429 Too Many Requests 반환"
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "SIGNUP_AUTO_LOGIN_FAILED "
                            + "— 회원가입 후 인증 세션 생성 실패",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ErrorResponse.class
                            )
                    )
            )
    })
    ResponseEntity<SuccessResponse<SignUpResult>> signUp(
            SignUpCommand command
    );
}
