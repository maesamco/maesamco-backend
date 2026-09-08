package com.maesamco.content.problem.presentation.controller;

import com.maesamco.content.global.response.PageResponse;
import com.maesamco.content.global.response.SuccessResponse;
import com.maesamco.content.global.util.PageableFactory;
import com.maesamco.content.problem.application.service.ProblemService;
import com.maesamco.content.problem.presentation.dto.request.ProblemCreateRequest;
import com.maesamco.content.problem.presentation.dto.request.ProblemSearchRequest;
import com.maesamco.content.problem.presentation.dto.request.ProblemUpdateRequest;
import com.maesamco.content.problem.presentation.dto.response.ProblemCreateResponse;
import com.maesamco.content.problem.presentation.dto.response.ProblemResponse;
import com.maesamco.content.problem.presentation.dto.response.ProblemSearchItemResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 문제 생성, 조회, 수정, 삭제를 위한 API를 제공합니다.
 *
 * <p>문제 생성, 단건 조회, 목록 조회, 수정, 삭제는
 * {@link ProblemService}에 위임합니다.</p>
 *
 * <p>모든 정상 응답은 {@link SuccessResponse}로 감싸 반환하며,
 * 목록 조회 결과는 {@link PageResponse}를 사용해 페이징 정보를 함께 제공합니다.</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/contents/problems")
public class ProblemController {

    /** 문제 생성, 조회, 수정, 삭제 비즈니스 로직을 담당하는 서비스입니다. */
    private final ProblemService problemService;

    /**
     * 새로운 문제를 생성합니다.
     *
     * <p>요청 본문의 문제 생성 정보를 검증한 뒤,
     * 문제 생성 로직을 {@link ProblemService}에 위임합니다.</p>
     *
     * @param request 문제 생성 요청 정보
     * @return 생성된 문제 정보를 포함한 성공 응답
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<SuccessResponse<ProblemCreateResponse>> createProblem(
            @Valid @RequestBody ProblemCreateRequest request
    ) {
        ProblemCreateResponse response = problemService.createProblem(request);

        return ResponseEntity
                .status(HttpStatus.CREATED) // 201 Created
                .body(SuccessResponse.success(response));
    }

    /**
     * 문제 ID를 기준으로 단일 문제를 조회합니다.
     *
     * <p>문제 ID를 기준으로 문제를 조회하고,
     * 조회 결과를 {@link ProblemResponse}로 반환합니다.</p>
     *
     * @param problemId 조회할 문제의 고유 ID
     * @return 조회된 문제 정보를 포함한 성공 응답
     */
    @GetMapping("/{problemId}")
    public ResponseEntity<SuccessResponse<ProblemResponse>> getProblem(
            @PathVariable UUID problemId
    ) {
        ProblemResponse response = problemService.getProblem(problemId);

        return ResponseEntity.ok(
                SuccessResponse.success(response)
        );
    }

    /**
     * 검색 조건에 해당하는 문제 목록을 조회합니다.
     *
     * <p>난이도, 유형, 언어, 출처 등의 검색 조건을
     * {@link ProblemSearchRequest}로 전달받아 조건에 맞는 문제 목록을 조회합니다.</p>
     *
     * <p>조회 결과는 {@link PageResponse}로 변환하여
     * 문제 목록과 페이징 정보를 함께 반환합니다.</p>
     *
     * @param request 문제 목록 검색 조건
     * @param page 조회할 페이지 번호
     * @param size 한 페이지에 조회할 문제 개수
     * @param sort 정렬 기준으로 사용할 필드명
     * @param direction 정렬 방향
     * @return 검색 조건에 해당하는 페이징된 문제 목록
     */
    @GetMapping
    public ResponseEntity<SuccessResponse<PageResponse<ProblemSearchItemResponse>>> getProblems(
            @Valid @ModelAttribute ProblemSearchRequest request,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction
    ) {
        Pageable pageable = PageableFactory.of(page, size, sort, direction); // 팀 규칙에 맞춰 단일 정렬만 가능하도록 pageable 객체를 만든다.
        PageResponse<ProblemSearchItemResponse> response = problemService.searchProblems(request, pageable);

        return ResponseEntity.ok(
                SuccessResponse.success(response)
        );
    }

    /*
    * 다중 정렬은
    * @RequestParam(required = false) Integer page,
    * @RequestParam(required = false) Integer size,
    * @RequestParam(required = false) List<String> sort
    * 로 Controller에서 받고, 요청은
    * ?page=0
    * &size=20
    * &sort=title,asc
    * &sort=createdAt,desc
    * 와 같은 식의 예시처럼 받기로 약속한다.
    * 그리고 List<String> sort의 경우, PageableFactory에서 약속한 sort 리스트의 구분자로 파싱해서 Sort.Order로 바꾼다.
    * 이에 대해 다중 정렬에 대한 구현은 ProblemSearchRepositoryImpl에 미리 해놓았다.
    * */

    /**
     * 지정한 문제의 정보를 수정합니다.
     *
     * <p>문제 ID로 수정 대상을 식별하고,
     * 요청 본문에 전달된 수정 정보를 반영합니다.</p>
     *
     * <p>수정 요청에 포함된 값만 변경하며,
     * 문제의 현재 버전 번호를 증가시킵니다.</p>
     *
     * @param problemId 수정할 문제의 고유 ID
     * @param request 문제 수정 요청 정보
     * @return 수정된 문제 정보를 포함한 성공 응답
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{problemId}")
    public ResponseEntity<SuccessResponse<ProblemResponse>> updateProblem(
            @PathVariable UUID problemId,
            @Valid @RequestBody ProblemUpdateRequest request
    ) {
        ProblemResponse response = problemService.updateProblem(problemId, request);

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
     * <p>삭제 시 실제 데이터를 제거하지 않고,
     * 삭제 시각과 삭제 사용자 정보를 기록하는 Soft Delete 방식으로 처리합니다.</p>
     *
     * @param problemId 삭제할 문제의 고유 ID
     * @param userId 삭제를 요청한 사용자의 고유 ID
     * @return 응답 데이터가 없는 성공 응답
     */
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{problemId}")
    public ResponseEntity<SuccessResponse<Void>> deleteProblem(
            @PathVariable UUID problemId,
            @AuthenticationPrincipal UUID userId
    ) {
        problemService.deleteProblem(problemId, userId);

        return ResponseEntity.ok(
                SuccessResponse.empty()
        );
    }
}