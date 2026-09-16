package com.maesamco.user.presentation.api_controller;

import com.maesamco.user.application.service.*;
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
                    description = "AUTH_UNAUTHORIZED",
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
     * 로그인 사용자의 닉네임과 Java 학습 정보를 수정합니다.
     *
     * @param authentication 현재 Access Token 인증 정보
     * @param command 변경할 사용자 기본 정보
     * @return 변경된 사용자 기본 정보
     */
    @Operation(
            summary = "내 정보 수정",
            description = "Access Token으로 인증된 사용자의 닉네임, "
                    + "Java 학습 수준과 경험 개월 수를 수정합니다. "
                    + "이메일, 권한과 계정 상태는 수정할 수 없습니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "내 정보 수정 성공",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "INVALID_INPUT_VALUE",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ErrorResponse.class
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
                    responseCode = "409",
                    description = "USER_DUPLICATE_NICKNAME 또는 "
                            + "USER_PROFILE_UPDATE_CONFLICT",
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
    ResponseEntity<SuccessResponse<UpdateMyProfileResult>> updateMyProfile(
            @Parameter(hidden = true)
            Authentication authentication,

            @Valid
            @RequestBody
            UpdateMyProfileCommand command
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
                    description = "AUTH_UNAUTHORIZED",
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
                    responseCode = "409",
                    description = "USER_PASSWORD_CHANGE_CONFLICT",
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

    /**
     * 로그인 사용자의 관심 개념 목록을 전체 교체합니다.
     *
     * @param authentication 현재 Access Token 인증 정보
     * @param command 새롭게 설정할 관심 개념 목록
     * @return 최종 관심 개념 목록과 변경 시각
     */
    @Operation(
            summary = "관심 개념 설정",
            description = "Access Token으로 인증된 사용자의 관심 개념 목록을 "
                    + "요청한 목록으로 전체 교체합니다. "
                    + "빈 배열을 전달하면 모든 관심 개념을 해제합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "관심 개념 설정 성공",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "INVALID_INPUT_VALUE",
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
                    description = "USER_NOT_FOUND 또는 CONCEPT_NOT_FOUND",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "CONTENT_SERVICE_UNAVAILABLE",
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
    ResponseEntity<SuccessResponse<UpdateMyInterestsResult>>
    updateMyInterests(
            @Parameter(hidden = true)
            Authentication authentication,

            @Valid
            @RequestBody
            UpdateMyInterestsCommand command
    );
}
