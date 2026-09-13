package com.maesamco.content.problem.presentation.controller;

import com.maesamco.content.global.response.SuccessResponse;
import com.maesamco.content.problem.application.service.ProblemInternalService;
import com.maesamco.content.problem.presentation.dto.response.InternalProblemResponse;
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
}