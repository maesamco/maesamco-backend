package com.maesamco.content.presentation.api_controller;

import com.maesamco.content.application.result.ProblemResult;
import com.maesamco.content.application.result.ProblemSearchResult;
import com.maesamco.content.application.persistence_service.ProblemService;
import com.maesamco.content.global.common.pagination.PageQuery;
import com.maesamco.content.global.common.pagination.PageResult;
import com.maesamco.content.global.response.PageResponse;
import com.maesamco.content.global.response.SuccessResponse;
import com.maesamco.content.global.util.PageQueryFactory;
import com.maesamco.content.presentation.request.ProblemCreateRequest;
import com.maesamco.content.presentation.request.ProblemSearchRequest;
import com.maesamco.content.presentation.request.ProblemUpdateRequest;
import com.maesamco.content.presentation.response.ProblemCreateResponse;
import com.maesamco.content.presentation.response.ProblemResponse;
import com.maesamco.content.presentation.response.ProblemSearchItemResponse;
import com.maesamco.content.presentation.response.ProblemShortResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.maesamco.content.global.security.authorization.RequireAdmin;
import org.springframework.web.bind.annotation.RestController;

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
public class ProblemController implements ProblemApiDocs {

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
    @Override
    @RequireAdmin
    public ResponseEntity<SuccessResponse<ProblemCreateResponse>> createProblem(ProblemCreateRequest request) {
        ProblemResult result = problemService.createProblem(request.toCommand());

        return ResponseEntity
                .status(HttpStatus.CREATED) // 201 Created
                .body(SuccessResponse.success(
                                ProblemCreateResponse.from(result)
                        )
                );
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
    // 정확한 정보는 관리자만이 조회할 수 있다.
    @Override
    @RequireAdmin
    public ResponseEntity<SuccessResponse<ProblemResponse>> getProblem(UUID problemId) {
        ProblemResult result = problemService.getProblemForAdmin(problemId);

        return ResponseEntity.ok(
                SuccessResponse.success(
                        ProblemResponse.from(result)
                )
        );
    }
    // 모든 사용자는 문제의 간단 정보를 조회할 수 있다.
    @Override
    public ResponseEntity<SuccessResponse<ProblemShortResponse>> getProblemShort(UUID problemId) {
        ProblemResult result =
                problemService.getProblemForUser(problemId);

        return ResponseEntity.ok(
                SuccessResponse.success(
                        ProblemShortResponse.from(result)
                )
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
    @Override
    public ResponseEntity<SuccessResponse<PageResponse<ProblemSearchItemResponse>>> getProblems(
            ProblemSearchRequest request,
            Integer page,
            Integer size,
            String sort,
            String direction
    ) {
        // 잘못된 페이징 파라미터는 기본값으로 보정한다(팀 컨벤션 10절).
        PageQuery pageQuery = PageQueryFactory.of(page, size, sort, direction);

        // Service는 Spring Data 타입이 아닌 자체 PageResult로 반환한다(#230).
        PageResult<ProblemSearchResult> results = problemService.searchProblems(request.toQuery(), pageQuery);

        // 페이지 공통 반환 객체 PageResponse로 변환한다.
        PageResponse<ProblemSearchItemResponse> response = PageResponse.from(results, ProblemSearchItemResponse::from);

        return ResponseEntity.ok(
                SuccessResponse.success(response)
        );
    }

    // 현재 PageQueryFactory는 단일 sort/direction만 파싱한다.
    // 여러 정렬 조건을 받는 API가 필요해지면 sort 파싱을 별도로 확장한다.

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
    @Override
    @RequireAdmin
    public ResponseEntity<SuccessResponse<ProblemResponse>> updateProblem(UUID problemId, ProblemUpdateRequest request) {
        ProblemResult result = problemService.updateProblem(problemId, request.toCommand());

        return ResponseEntity.ok(
                SuccessResponse.success(
                        ProblemResponse.from(result)
                )
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
    @Override
    @RequireAdmin
    public ResponseEntity<SuccessResponse<Void>> deleteProblem(UUID problemId, UUID userId) {
        problemService.deleteProblem(problemId, userId);

        return ResponseEntity.ok(
                SuccessResponse.empty()
        );
    }
}