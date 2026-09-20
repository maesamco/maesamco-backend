package com.maesamco.content.presentation.internal_controller;

import com.maesamco.content.application.result.ProblemInternalResult;
import com.maesamco.content.application.persistence_service.ProblemInternalService;
import com.maesamco.content.global.response.SuccessResponse;
import com.maesamco.content.presentation.response.InternalProblemResponse;
import com.maesamco.content.global.security.hmac.AllowedInternalCallers;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ⚠️ 이슈 #185 재리뷰(P1) — content-service가 원래부터 갖고 있던 유일한 내부
 * 컨트롤러인데, "내부 API 호출자 인가 공통 메커니즘"(이슈 #138) PR의 diff에
 * 포함되지 않아 애노테이션이 하나도 안 붙은 채로 남아있었다. 지금은
 * coaching-service만 실제 호출자(ContentServiceFeignClient)라 당장 악용
 * 가능한 제3자는 없지만, 나중에 다른 서비스가 content-service HMAC 키를 새로
 * 발급받는 순간 별도 승인 없이 이 두 엔드포인트에도 접근 권한이 자동으로
 * 생기는 구조적 위험이 있었다. 클래스 레벨에 붙여 두 메서드 모두에 적용한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1")
@AllowedInternalCallers({"coaching-service"})
public class InternalProblemController {

    private final ProblemInternalService problemInternalService;

    /** 내부 서비스용 문제 단건 조회 */
    @GetMapping("/problems/{problemId}")
    public ResponseEntity<SuccessResponse<InternalProblemResponse>> getProblem(
            @PathVariable UUID problemId
    ) {
        ProblemInternalResult result = problemInternalService.getProblemMetaData(problemId);

        return ResponseEntity.ok(
                SuccessResponse.success(InternalProblemResponse.from(result))
        );
    }

    /** 내부 서비스용 문제 버전 단건 조회 — 제출 시점 문제 버전 기준 조회가 필요한 호출자용(이슈 #178) */
    @GetMapping("/problem-versions/{problemVersionId}")
    public ResponseEntity<SuccessResponse<InternalProblemResponse>> getProblemVersion(
            @PathVariable UUID problemVersionId
    ) {
        ProblemInternalResult result = problemInternalService.getProblemVersionMetaData(problemVersionId);

        return ResponseEntity.ok(
                SuccessResponse.success(InternalProblemResponse.from(result))
        );
    }
}