package com.maesamco.content.presentation.internal_controller;

import com.maesamco.content.application.problem.service.ProblemInternalService;
import com.maesamco.content.global.response.SuccessResponse;
import com.maesamco.content.presentation.response.InternalProblemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1")
public class InternalProblemController {

    private final ProblemInternalService problemInternalService;

    /** 내부 서비스용 문제 단건 조회 */
    @GetMapping("/problems/{problemId}")
    public ResponseEntity<SuccessResponse<InternalProblemResponse>> getProblem(
            @PathVariable UUID problemId
    ) {
        InternalProblemResponse response = problemInternalService.getProblemMetaData(problemId);

        return ResponseEntity.ok(
                SuccessResponse.success(response)
        );
    }

    /** 내부 서비스용 문제 버전 단건 조회 — 제출 시점 문제 버전 기준 조회가 필요한 호출자용(이슈 #178) */
    @GetMapping("/problem-versions/{problemVersionId}")
    public ResponseEntity<SuccessResponse<InternalProblemResponse>> getProblemVersion(
            @PathVariable UUID problemVersionId
    ) {
        InternalProblemResponse response = problemInternalService.getProblemVersionMetaData(problemVersionId);

        return ResponseEntity.ok(
                SuccessResponse.success(response)
        );
    }
}