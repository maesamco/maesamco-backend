package com.maesamco.content.presentation.api_controller;

import com.maesamco.content.application.facade.ProblemPublicationFacade;
import com.maesamco.content.application.persistence_service.ProblemService;
import com.maesamco.content.application.result.ProblemSearchResult;
import com.maesamco.content.global.response.PageResponse;
import com.maesamco.content.global.response.SuccessResponse;
import com.maesamco.content.global.util.PageableFactory;
import com.maesamco.content.presentation.request.ProblemSearchRequest;
import com.maesamco.content.presentation.response.AdminProblemSearchItemResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/contents/problems")
public class AdminProblemController {

    private final ProblemPublicationFacade problemPublicationFacade;

    private final ProblemService problemService;

    /**
     * 관리자가 문제 상태를 포함한 조건으로 문제 목록을 검색합니다.
     *
     * <p>공개 문제 목록과 달리 DRAFT, REVIEW_PENDING,
     * PUBLISHED 상태를 모두 조회할 수 있습니다.</p>
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<
            SuccessResponse<
                    PageResponse<AdminProblemSearchItemResponse>
                    >
            > searchProblems(
            @Valid
            @ModelAttribute
            ProblemSearchRequest request,
            @RequestParam(required = false)
            Integer page,
            @RequestParam(required = false)
            Integer size,
            @RequestParam(required = false)
            String sort,
            @RequestParam(required = false)
            String direction
    ) {
        Pageable pageable =
                PageableFactory.of(
                        page,
                        size,
                        sort,
                        direction
                );

        Page<ProblemSearchResult> results =
                problemService.searchProblemsForAdmin(
                        request.toQuery(),
                        pageable
                );

        PageResponse<AdminProblemSearchItemResponse>
                response =
                PageResponse.from(
                        results,
                        AdminProblemSearchItemResponse::from
                );

        return ResponseEntity.ok(
                SuccessResponse.success(
                        response
                )
        );
    }

    /**
     * REVIEW_PENDING 상태의 문제 발행을 승인합니다.
     *
     * @param problemId 발행을 승인할 문제의 고유 ID
     * @return 응답 데이터가 없는 성공 응답
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{problemId}/approve")
    public ResponseEntity<SuccessResponse<Void>>
    approvePublication(
            @PathVariable
            UUID problemId
    ) {
        problemPublicationFacade.approvePublication(
                problemId
        );

        return ResponseEntity.ok(
                SuccessResponse.empty()
        );
    }

    /**
     * 발행된 문제를 재발행하기 위해 관리자 승인 대기 상태로 되돌립니다.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{problemId}/republish")
    public ResponseEntity<SuccessResponse<Void>>
    revertToReviewPendingForRepublish(
            @PathVariable
            UUID problemId
    ) {
        problemPublicationFacade
                .revertToReviewPendingForRepublish(
                        problemId
                );

        return ResponseEntity.ok(
                SuccessResponse.empty()
        );
    }

    // TODO: POST /api/v1/admin/problems/{problemId}/reject
}
