package com.maesamco.content.problem.presentation.controller;

import com.maesamco.content.global.response.PageResponse;
import com.maesamco.content.global.response.SuccessResponse;
import com.maesamco.content.problem.application.service.ProblemFinderService;
import com.maesamco.content.problem.application.service.ProblemService;
import com.maesamco.content.problem.presentation.dto.request.ProblemCreateRequest;
import com.maesamco.content.problem.presentation.dto.request.ProblemSearchRequest;
import com.maesamco.content.problem.presentation.dto.request.ProblemUpdateRequest;
import com.maesamco.content.problem.presentation.dto.response.ProblemCreateResponse;
import com.maesamco.content.problem.presentation.dto.response.ProblemListResponse;
import com.maesamco.content.problem.presentation.dto.response.ProblemResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 문제 생성, 조회, 수정, 삭제를 위한 API를 제공합니다.
 *
 * <p>문제 단건 조회와 목록 조회는 {@link ProblemFinderService}에 위임하고,
 * 문제 생성, 수정, 삭제는 {@link ProblemService}에 위임합니다.</p>
 *
 * <p>모든 정상 응답은 {@link SuccessResponse}로 감싸 반환하며,
 * 목록 조회 결과는 {@link PageResponse}를 사용해 페이징 정보를 함께 제공합니다.</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/problems")
public class ProblemController {

    /**
     * 문제 생성, 수정, 삭제 비즈니스 로직을 담당하는 서비스입니다.
     */
    private final ProblemService problemService;

    /**
     * 문제 단건 및 목록 조회를 담당하는 조회 서비스입니다.
     */
    private final ProblemFinderService problemFinderService;

    /**
     * 새로운 문제를 생성합니다.
     *
     * <p>요청 본문의 문제 생성 정보를 검증한 뒤,
     * 문제 생성 로직을 {@link ProblemService}에 위임합니다.</p>
     *
     * @param request 문제 생성 요청 정보
     * @return 생성된 문제 정보를 포함한 성공 응답
     */
    @PostMapping
    public ResponseEntity<SuccessResponse<ProblemCreateResponse>> createProblem(
            @Valid @RequestBody ProblemCreateRequest request
    ) {
        ProblemCreateResponse response =
                problemService.createProblem(request);

        return ResponseEntity.ok(
                SuccessResponse.success(response)
        );
    }

    /**
     * 문제 ID를 기준으로 단일 문제를 조회합니다.
     *
     * @param problemId 조회할 문제의 고유 ID
     * @return 조회된 문제 정보를 포함한 성공 응답
     */
    @GetMapping("/{problemId}")
    public ResponseEntity<SuccessResponse<ProblemResponse>> getProblem(
            @PathVariable UUID problemId
    ) {
        ProblemResponse response =
                problemFinderService.getProblem(problemId);

        return ResponseEntity.ok(
                SuccessResponse.success(response)
        );
    }

    /**
     * 검색 조건에 해당하는 문제 목록을 조회합니다.
     *
     * <p>난이도, 유형, 언어, 상태, 출처 등의 검색 조건을
     * {@link ProblemSearchRequest}로 전달받아 조건에 맞는 문제 목록을 조회합니다.</p>
     *
     * <p>조회 결과는 {@link PageResponse}로 변환하여
     * 문제 목록과 페이징 정보를 함께 반환합니다.</p>
     *
     * @param request 문제 목록 검색 조건 및 페이징 조건
     * @return 검색 조건에 해당하는 페이징된 문제 목록
     */
    @GetMapping
    public ResponseEntity<SuccessResponse<PageResponse<ProblemListResponse>>> getProblems(
            @Valid @ModelAttribute ProblemSearchRequest request
    ) {
        PageResponse<ProblemListResponse> response =
                problemFinderService.getProblems(request);

        return ResponseEntity.ok(
                SuccessResponse.success(response)
        );
    }

    /**
     * 지정한 문제의 정보를 수정합니다.
     *
     * <p>문제 ID로 수정 대상을 식별하고,
     * 요청 본문에 전달된 수정 정보를 반영합니다.</p>
     *
     * @param problemId 수정할 문제의 고유 ID
     * @param request 문제 수정 요청 정보
     * @return 수정된 문제 정보를 포함한 성공 응답
     */
    @PatchMapping("/{problemId}")
    public ResponseEntity<SuccessResponse<ProblemResponse>> updateProblem(
            @PathVariable UUID problemId,
            @Valid @RequestBody ProblemUpdateRequest request
    ) {
        ProblemResponse response =
                problemService.updateProblem(problemId, request);

        return ResponseEntity.ok(
                SuccessResponse.success(response)
        );
    }

    /**
     * 지정한 문제를 삭제합니다.
     *
     * <p>문제 ID를 기준으로 삭제 대상 문제를 식별한 뒤
     * 문제 삭제 로직을 {@link ProblemService}에 위임합니다.</p>
     *
     * @param problemId 삭제할 문제의 고유 ID
     * @return 응답 데이터가 없는 성공 응답
     */
    @DeleteMapping("/{problemId}")
    public ResponseEntity<SuccessResponse<Void>> deleteProblem(
            @PathVariable UUID problemId
    ) {
        problemService.deleteProblem(problemId);

        return ResponseEntity.ok(
                SuccessResponse.empty()
        );
    }
}