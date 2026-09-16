package com.maesamco.judge.presentation.internal_controller;

import com.maesamco.judge.application.query.SubmissionGetQuery;
import com.maesamco.judge.application.query_service.SubmissionQueryService;
import com.maesamco.judge.application.result.SubmissionGetResult;
import com.maesamco.judge.global.response.SuccessResponse;
import com.maesamco.judge.global.security.hmac.AllowedInternalCallers;
import com.maesamco.judge.presentation.response.SubmissionInternalGetResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 이 API는 Content Service, Coaching Service 둘 다 실제로 사용한다(담당자 확인,
 * 이슈 #138). judge-service의 internal.hmac.keys에는 content-service/
 * coaching-service 둘 다 등록돼 있지만, 그 자체는 "호출 자격"만 보장할 뿐
 * "이 API를 써도 되는가"는 별도로 명시해야 한다 — @AllowedInternalCallers로
 * 이 두 서비스만 허용함을 명시적으로 표현한다.
 */
@RestController
@RequestMapping("/internal/v1/submissions")
@RequiredArgsConstructor
public class SubmissionInternalController {

    private final SubmissionQueryService submissionQueryService;

    @AllowedInternalCallers({"content-service", "coaching-service"})
    @GetMapping("/{submissionId}")
    public SuccessResponse<SubmissionInternalGetResponse> getSubmission(@PathVariable UUID submissionId) {
        SubmissionGetResult result =
                submissionQueryService.getSubmissionForInternal(SubmissionGetQuery.from(submissionId));
        return SuccessResponse.success(SubmissionInternalGetResponse.from(result));
    }
}