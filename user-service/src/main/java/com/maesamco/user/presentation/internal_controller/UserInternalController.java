package com.maesamco.user.presentation.internal_controller;

import com.maesamco.user.application.service.GetInternalUserResult;
import com.maesamco.user.application.service.GetInternalUserService;
import com.maesamco.user.application.service.GetQuizTargetUsersQuery;
import com.maesamco.user.application.service.GetQuizTargetUsersResult;
import com.maesamco.user.application.service.GetQuizTargetUsersService;
import com.maesamco.user.global.response.SuccessResponse;
import com.maesamco.user.global.security.hmac.AllowedInternalCallers;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Content Service에 사용자 정보를 제공하는 내부 전용 API입니다.
 *
 * <p>{@code /internal/v1/**} 경로의 HMAC 인증을 통과한 요청 중에서도
 * {@code content-service}가 서명한 요청만 호출할 수 있습니다.</p>
 */
@RestController
@RequestMapping("/internal/v1/users")
@RequiredArgsConstructor
@AllowedInternalCallers("content-service")
public class UserInternalController {

    private final GetInternalUserService
            getInternalUserService;

    private final GetQuizTargetUsersService
            getQuizTargetUsersService;

    /**
     * Daily Quiz 생성 대상 사용자를 UUID cursor 방식으로 조회합니다.
     *
     * <p>정적 경로를 Path Variable 경로보다 명확히 구분하기 위해
     * {@code /quiz-targets}를 별도 매핑합니다.</p>
     *
     * @param cursor 직전 페이지의 마지막 사용자 ID
     * @param size 한 번에 반환할 사용자 수
     * @return 대상 사용자 ID와 다음 cursor 정보
     */
    @GetMapping("/quiz-targets")
    public ResponseEntity<
            SuccessResponse<GetQuizTargetUsersResult>
            > getQuizTargetUsers(
            @RequestParam(required = false)
            UUID cursor,

            @RequestParam
            int size
    ) {
        GetQuizTargetUsersQuery query =
                new GetQuizTargetUsersQuery(
                        cursor,
                        size
                );

        GetQuizTargetUsersResult result =
                getQuizTargetUsersService
                        .getQuizTargetUsers(
                                query
                        );

        return ResponseEntity.ok(
                SuccessResponse.success(
                        result
                )
        );
    }

    /**
     * 사용자별 활성 관심 개념 ID를 조회합니다.
     *
     * @param userId 조회할 사용자 식별자
     * @return 사용자의 활성 관심 개념 ID 목록
     */
    @GetMapping("/{userId}")
    public ResponseEntity<
            SuccessResponse<GetInternalUserResult>
            > getInternalUser(
            @PathVariable
            UUID userId
    ) {
        GetInternalUserResult result =
                getInternalUserService
                        .getInternalUser(
                                userId
                        );

        return ResponseEntity.ok(
                SuccessResponse.success(
                        result
                )
        );
    }
}
