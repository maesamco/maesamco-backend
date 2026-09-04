package com.maesamco.coaching.presentation.api_controller;

import com.maesamco.coaching.application.facade.FollowUpAnswerFacade;
import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import com.maesamco.coaching.global.response.SuccessResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 코칭 서비스 API 명세 5번 API(역질문 답변 등록, 이슈 #51) — ExplanationApiController와는
 * 별개의 리소스 루트(/follow-up-questions/{followUpQuestionId})라 클래스 레벨
 * @RequestMapping을 공유할 수 없어 별도 컨트롤러로 둔다(Spring이 클래스/메서드 레벨
 * @RequestMapping을 이어붙이지, 절대경로로 덮어쓰지 않는다).
 */
@RestController
@RequestMapping("/api/v1/coaching/follow-up-questions/{followUpQuestionId}/answers")
public class FollowUpAnswerApiController {

    private final FollowUpAnswerFacade followUpAnswerFacade;

    public FollowUpAnswerApiController(FollowUpAnswerFacade followUpAnswerFacade) {
        this.followUpAnswerFacade = followUpAnswerFacade;
    }

    @PostMapping
    public ResponseEntity<SuccessResponse<FollowUpAnswerRegisterResponse>> registerAnswer(
            @PathVariable UUID followUpQuestionId,
            @Valid @RequestBody FollowUpAnswerRegisterRequest request,
            @AuthenticationPrincipal UUID userId
    ) {
        requireAuthenticated(userId);
        FollowUpAnswerFacade.FollowUpAnswerRegisterResult result =
                followUpAnswerFacade.registerAnswer(followUpQuestionId, request.answerText(), userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SuccessResponse.success(FollowUpAnswerRegisterResponse.from(result)));
    }

    /**
     * HintApiController.requireAuthenticated()와 동일한 이유 — SecurityConfig가
     * anyRequest().permitAll()이라 각 API가 알아서 인증을 확인해야 한다(PR #70 리뷰).
     */
    private void requireAuthenticated(UUID userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }
    }
}
