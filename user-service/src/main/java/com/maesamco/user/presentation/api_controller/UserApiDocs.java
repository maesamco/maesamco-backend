package com.maesamco.user.presentation.api_controller;

import com.maesamco.user.application.service.ChangePasswordCommand;
import com.maesamco.user.application.service.GetMyProfileResult;
import com.maesamco.user.global.response.ErrorResponse;
import com.maesamco.user.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * User API의 Swagger/OpenAPI 계약을 정의합니다.
 */
public interface UserApiDocs {

    /**
     * 로그인 사용자의 기본 정보를 조회합니다.
     *
     * @param authentication 현재 Access Token 인증 정보
     * @return 로그인 사용자의 기본 정보
     */
    @Operation(
            summary = "내 정보 조회",
            description = "Access Token으로 인증된 사용자의 "
                    + "기본 정보를 조회합니다. "
                    + "비밀번호 해시와 인증 토큰은 반환하지 않습니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "내 정보 조회 성공",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "AUTH_UNAUTHORIZED 또는 AUTH_INVALID_TOKEN",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "USER_NOT_FOUND",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "INTERNAL_SERVER_ERROR",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ErrorResponse.class
                            )
                    )
            )
    })
    ResponseEntity<SuccessResponse<GetMyProfileResult>> getMyProfile(
            @Parameter(hidden = true)
            Authentication authentication
    );

    /**
     * 로그인 사용자의 비밀번호를 변경합니다.
     *
     * @param authentication 현재 Access Token 인증 정보
     * @param command 비밀번호 변경 입력값
     * @return 본문이 없는 204 응답
     */
    @Operation(
            summary = "비밀번호 변경",
            description = "현재 비밀번호를 확인한 후 새 비밀번호로 변경합니다. "
                    + "변경이 완료되면 기존 Access Token과 "
                    + "Refresh Token 인증 세션을 모두 무효화합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "비밀번호 변경 성공",
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
                    responseCode = "400",
                    description = "INVALID_INPUT_VALUE, "
                            + "USER_CURRENT_PASSWORD_MISMATCH 또는 "
                            + "USER_PASSWORD_POLICY_VIOLATION",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "AUTH_UNAUTHORIZED 또는 AUTH_INVALID_TOKEN",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "USER_NOT_ACTIVE",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "USER_NOT_FOUND",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "INTERNAL_SERVER_ERROR",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ErrorResponse.class
                            )
                    )
            )
    })
    ResponseEntity<Void> changePassword(
            @Parameter(hidden = true)
            Authentication authentication,

            @Valid
            @RequestBody
            ChangePasswordCommand command
    );
}
