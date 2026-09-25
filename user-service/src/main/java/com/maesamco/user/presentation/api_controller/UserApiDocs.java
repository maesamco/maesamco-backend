package com.maesamco.user.presentation.api_controller;

import com.maesamco.user.application.service.ChangePasswordCommand;
import com.maesamco.user.application.service.GetMyGamificationResult;
import com.maesamco.user.application.service.GetMyInterestsResult;
import com.maesamco.user.application.service.GetMyProfileResult;
import com.maesamco.user.application.service.GetMyXpHistoriesResult;
import com.maesamco.user.application.service.UpdateMyInterestsCommand;
import com.maesamco.user.application.service.UpdateMyInterestsResult;
import com.maesamco.user.application.service.UpdateMyProfileCommand;
import com.maesamco.user.application.service.UpdateMyProfileResult;
import com.maesamco.user.application.service.WithdrawUserCommand;
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
import org.springframework.web.bind.annotation.RequestParam;

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
     * 로그인 사용자의 현재 게이미피케이션 상태를 조회합니다.
     *
     * @param authentication 현재 Access Token 인증 정보
     * @return 누적 XP, 레벨과 연속 학습 상태
     */
    @Operation(
            summary = "내 게이미피케이션 상태 조회",
            description = "Access Token으로 인증된 활성 사용자의 "
                    + "현재 누적 XP, 레벨과 연속 학습 상태를 조회합니다. "
                    + "사용자 식별자, 낙관적 락 버전과 영속성 감사 필드는 "
                    + "응답에 포함하지 않습니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "게이미피케이션 상태 조회 성공",
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
                    description = "USER_NOT_FOUND 또는 GAMIFICATION_STATE_NOT_FOUND",
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
    ResponseEntity<SuccessResponse<GetMyGamificationResult>>
    getMyGamification(
            @Parameter(hidden = true)
            Authentication authentication
    );

    /**
     * 로그인 사용자의 XP 지급·차감 이력을 조회합니다.
     *
     * @param authentication 현재 Access Token 인증 정보
     * @param size 페이지당 조회 개수
     * @param cursor 다음 페이지 조회 cursor
     * @return cursor 기반 XP 이력 페이지
     */
    @Operation(
            summary = "내 XP 이력 조회",
            description = "Access Token으로 인증된 활성 사용자의 "
                    + "XP 지급·차감 이력을 최신순으로 조회합니다. "
                    + "earnedAt과 내부 XP 이력 식별자를 기준으로 "
                    + "cursor 기반 keyset pagination을 적용합니다. "
                    + "cursor는 내부 구조에 의존하지 않는 "
                    + "opaque 문자열로 취급해야 합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "XP 이력 조회 성공",
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
    ResponseEntity<SuccessResponse<GetMyXpHistoriesResult>>
    getMyXpHistories(
            @Parameter(hidden = true)
            Authentication authentication,

            @Parameter(
                    description = "페이지당 조회 개수. 생략하면 20",
                    example = "20",
                    schema = @Schema(
                            minimum = "1",
                            maximum = "100",
                            defaultValue = "20"
                    )
            )
            @RequestParam(required = false)
            Integer size,

            @Parameter(
                    description = "이전 응답의 nextCursor. 최초 조회에서는 생략",
                    example = "djF8MjAyNi0wOS0xN1QwMToyMDozMFp8"
                            + "MTExMTExMTEtMTExMS0xMTExLTExMTEt"
                            + "MTExMTExMTExMTEx"
            )
            @RequestParam(required = false)
            String cursor
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
     * 로그인 사용자의 현재 관심 개념 목록을 조회합니다.
     *
     * @param authentication 현재 Access Token 인증 정보
     * @return 현재 저장된 관심 개념 ID 목록
     */
    @Operation(
            summary = "내 관심 개념 조회",
            description = "Access Token으로 인증된 활성 사용자의 현재 관심 개념 ID 목록을 조회합니다. "
                    + "설정한 개념이 없으면 빈 배열을 반환하며, 개념 이름은 "
                    + "Content Service의 태그 목록 조회 결과와 ID로 매핑합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "관심 개념 조회 성공",
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
    ResponseEntity<SuccessResponse<GetMyInterestsResult>>
    getMyInterests(
            @Parameter(hidden = true)
            Authentication authentication
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

    /**
     * 로그인 사용자를 탈퇴 처리합니다.
     *
     * @param authentication 현재 Access Token 인증 정보
     * @param command 회원 탈퇴 입력값
     * @return 본문이 없는 204 응답
     */
    @Operation(
            summary = "회원 탈퇴",
            description = "본인 확인 후 인증된 사용자를 논리 삭제합니다. "
                    + "사용자의 관심 개념과 소셜 계정 연결도 논리 삭제하며, "
                    + "기존 Access Token과 Refresh Token 인증 세션을 모두 무효화합니다.\n\n"
                    + "본인 확인 수단은 둘 중 하나만 보냅니다 (GET /users/me의 hasPassword 기준).\n"
                    + "- hasPassword=true: currentPassword\n"
                    + "- hasPassword=false (소셜 가입): googleIdToken — Google로 다시 인증해 받은 ID Token. "
                    + "가입에 사용한 Google 계정이어야 합니다.\n\n"
                    + "탈퇴 후 같은 Google 계정으로 다시 가입할 수 있습니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "회원 탈퇴 성공",
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
                            + "USER_CURRENT_PASSWORD_MISMATCH, "
                            + "USER_PASSWORD_NOT_SET(소셜 계정인데 비밀번호를 보낸 경우) 또는 "
                            + "SOCIAL_REAUTH_FAILED(Google ID Token이 유효하지 않음)",
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
                    description = "USER_NOT_ACTIVE 또는 "
                            + "SOCIAL_REAUTH_ACCOUNT_MISMATCH(가입에 쓰지 않은 Google 계정)",
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
    ResponseEntity<Void> withdraw(
            @Parameter(hidden = true)
            Authentication authentication,

            @Valid
            @RequestBody
            WithdrawUserCommand command
    );
}
