package com.maesamco.user.presentation.api_controller;

import com.maesamco.user.application.service.ChangePasswordCommand;
import com.maesamco.user.application.service.ChangePasswordRetryService;
import com.maesamco.user.application.service.GetMyGamificationResult;
import com.maesamco.user.application.service.GetMyGamificationService;
import com.maesamco.user.application.service.GetMyInterestsResult;
import com.maesamco.user.application.service.GetMyInterestsService;
import com.maesamco.user.application.service.GetMyProfileResult;
import com.maesamco.user.application.service.GetMyProfileService;
import com.maesamco.user.application.service.GetMyXpHistoriesQuery;
import com.maesamco.user.application.service.GetMyXpHistoriesResult;
import com.maesamco.user.application.service.GetMyXpHistoriesService;
import com.maesamco.user.application.service.UpdateMyInterestsCommand;
import com.maesamco.user.application.service.UpdateMyInterestsResult;
import com.maesamco.user.application.service.UpdateMyInterestsService;
import com.maesamco.user.application.service.UpdateMyProfileCommand;
import com.maesamco.user.application.service.UpdateMyProfileResult;
import com.maesamco.user.application.service.UpdateMyProfileService;
import com.maesamco.user.application.service.WithdrawUserCommand;
import com.maesamco.user.application.service.WithdrawUserService;
import com.maesamco.user.global.response.SuccessResponse;
import com.maesamco.user.presentation.support.RefreshTokenCookieFactory;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static com.maesamco.user.presentation.support.AuthenticationPrincipalResolver.requireUserId;

/**
 * 로그인 사용자의 계정 정보를 관리하는 User API를 제공합니다.
 */
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
@Tag(
        name = "User",
        description = "로그인 사용자의 계정 정보 관리 API"
)
public class UserApiController implements UserApiDocs {

    private final GetMyProfileService getMyProfileService;

    private final GetMyGamificationService getMyGamificationService;

    private final ChangePasswordRetryService changePasswordRetryService;

    private final UpdateMyProfileService updateMyProfileService;

    private final GetMyInterestsService getMyInterestsService;

    private final UpdateMyInterestsService updateMyInterestsService;

    private final WithdrawUserService withdrawUserService;

    private final GetMyXpHistoriesService getMyXpHistoriesService;

    private final RefreshTokenCookieFactory refreshTokenCookieFactory;

    /**
     * 로그인 사용자의 기본 정보를 조회합니다.
     *
     * @param authentication 현재 Access Token 인증 정보
     * @return 로그인 사용자의 기본 정보
     */
    @Override
    @GetMapping
    public ResponseEntity<SuccessResponse<GetMyProfileResult>>
    getMyProfile(
            Authentication authentication
    ) {
        UUID userId =
                requireUserId(
                        authentication
                );

        GetMyProfileResult result =
                getMyProfileService.getMyProfile(
                        userId
                );

        return ResponseEntity.ok(
                SuccessResponse.success(
                        result
                )
        );
    }

    /**
     * 로그인 사용자의 현재 게이미피케이션 상태를 조회합니다.
     *
     * @param authentication 현재 Access Token 인증 정보
     * @return 누적 XP, 레벨과 연속 학습 상태
     */
    @Override
    @GetMapping("/gamification")
    public ResponseEntity<SuccessResponse<GetMyGamificationResult>>
    getMyGamification(
            Authentication authentication
    ) {
        UUID userId =
                requireUserId(
                        authentication
                );

        GetMyGamificationResult result =
                getMyGamificationService
                        .getMyGamification(
                                userId
                        );

        return ResponseEntity.ok(
                SuccessResponse.success(
                        result
                )
        );
    }

    /**
     * 로그인 사용자의 XP 지급·차감 이력을 조회합니다.
     *
     * @param authentication 현재 Access Token 인증 정보
     * @param size 페이지당 조회 개수
     * @param cursor 다음 페이지 조회 cursor
     * @return cursor 기반 XP 이력 페이지
     */
    @Override
    @GetMapping("/xp-histories")
    public ResponseEntity<SuccessResponse<GetMyXpHistoriesResult>>
    getMyXpHistories(
            Authentication authentication,
            @RequestParam(required = false)
            Integer size,
            @RequestParam(required = false)
            String cursor
    ) {
        UUID userId =
                requireUserId(
                        authentication
                );

        GetMyXpHistoriesQuery query =
                GetMyXpHistoriesQuery.of(
                        size,
                        cursor
                );

        GetMyXpHistoriesResult result =
                getMyXpHistoriesService
                        .getMyXpHistories(
                                userId,
                                query
                        );

        return ResponseEntity.ok(
                SuccessResponse.success(
                        result
                )
        );
    }

    /**
     * 로그인 사용자의 닉네임과 Java 학습 정보를 수정합니다.
     *
     * @param authentication 현재 Access Token 인증 정보
     * @param command 변경할 사용자 기본 정보
     * @return 변경된 사용자 기본 정보
     */
    @Override
    @PatchMapping
    public ResponseEntity<SuccessResponse<UpdateMyProfileResult>>
    updateMyProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateMyProfileCommand command
    ) {
        UUID userId =
                requireUserId(
                        authentication
                );

        UpdateMyProfileResult result =
                updateMyProfileService.updateMyProfile(
                        userId,
                        command
                );

        return ResponseEntity.ok(
                SuccessResponse.success(
                        result
                )
        );
    }

    /**
     * 로그인 사용자의 현재 관심 개념 목록을 조회합니다.
     *
     * @param authentication 현재 Access Token 인증 정보
     * @return 현재 저장된 관심 개념 ID 목록
     */
    @Override
    @GetMapping("/interests")
    public ResponseEntity<SuccessResponse<GetMyInterestsResult>>
    getMyInterests(
            Authentication authentication
    ) {
        UUID userId =
                requireUserId(
                        authentication
                );

        GetMyInterestsResult result =
                getMyInterestsService.getMyInterests(
                        userId
                );

        return ResponseEntity.ok(
                SuccessResponse.success(
                        result
                )
        );
    }

    /**
     * 로그인 사용자의 관심 개념 목록을 전체 교체합니다.
     *
     * @param authentication 현재 Access Token 인증 정보
     * @param command 새롭게 설정할 관심 개념 목록
     * @return 최종 관심 개념 목록과 변경 시각
     */
    @Override
    @PutMapping("/interests")
    public ResponseEntity<SuccessResponse<UpdateMyInterestsResult>>
    updateMyInterests(
            Authentication authentication,
            @Valid @RequestBody UpdateMyInterestsCommand command
    ) {
        UUID userId =
                requireUserId(
                        authentication
                );

        UpdateMyInterestsResult result =
                updateMyInterestsService.updateMyInterests(
                        userId,
                        command
                );

        return ResponseEntity.ok(
                SuccessResponse.success(
                        result
                )
        );
    }

    /**
     * 현재 비밀번호를 확인한 후 새 비밀번호로 변경합니다.
     *
     * <p>변경이 완료되면 사용자의 모든 인증 세션을 무효화하고
     * 현재 클라이언트의 Refresh Token Cookie를 삭제합니다.</p>
     *
     * @param authentication 현재 Access Token 인증 정보
     * @param command 비밀번호 변경 입력값
     * @return 본문이 없는 204 응답
     */
    @Override
    @PatchMapping("/password")
    public ResponseEntity<Void> changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordCommand command
    ) {
        UUID userId =
                requireUserId(
                        authentication
                );

        changePasswordRetryService.changePassword(
                userId,
                command
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
     * 현재 비밀번호를 확인한 후 로그인 사용자를 탈퇴 처리합니다.
     *
     * <p>탈퇴가 완료되면 사용자와 관심 개념을 논리 삭제하고,
     * 모든 인증 세션을 무효화하며 현재 클라이언트의
     * Refresh Token Cookie를 삭제합니다.</p>
     *
     * @param authentication 현재 Access Token 인증 정보
     * @param command 회원 탈퇴 입력값
     * @return 본문이 없는 204 응답
     */
    @Override
    @DeleteMapping
    public ResponseEntity<Void> withdraw(
            Authentication authentication,
            @Valid @RequestBody WithdrawUserCommand command
    ) {
        UUID userId =
                requireUserId(
                        authentication
                );

        withdrawUserService.withdraw(
                userId,
                command
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
}
