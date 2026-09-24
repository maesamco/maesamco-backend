package com.maesamco.content.presentation.api_controller;

import com.maesamco.content.application.facade.ProblemPublicationFacade;
import com.maesamco.content.application.persistence_service.ProblemService;
import com.maesamco.content.application.persistence_service.ProblemVersionService;
import com.maesamco.content.application.result.ProblemSearchResult;
import com.maesamco.content.domain.entity.problem.ProblemVersion;
import com.maesamco.content.global.response.PageResponse;
import com.maesamco.content.global.response.SuccessResponse;
import com.maesamco.content.global.util.PageableFactory;
import com.maesamco.content.presentation.request.ProblemSearchRequest;
import com.maesamco.content.presentation.response.AdminProblemSearchItemResponse;
import com.maesamco.content.presentation.response.ProblemVersionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import com.maesamco.content.global.security.authorization.RequireAdmin;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AdminProblemController implements AdminProblemApiDocs {

    private final ProblemPublicationFacade problemPublicationFacade;

    private final ProblemService problemService;

    private final ProblemVersionService problemVersionService;

    /**
     * 관리자가 문제 상태를 포함한 조건으로 문제 목록을 검색합니다.
     *
     * <p>상태를 지정하지 않으면 ARCHIVED를 포함한 모든 상태를 조회합니다.</p>
     */
    @Override
    @RequireAdmin
    public ResponseEntity<
            SuccessResponse<
                    PageResponse<AdminProblemSearchItemResponse>
                    >
            > searchProblems(
            ProblemSearchRequest request,
            Integer page,
            Integer size,
            String sort,
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

        PageResponse<AdminProblemSearchItemResponse> response =
                PageResponse.from(
                        results,
                        AdminProblemSearchItemResponse::from
                );

        return ResponseEntity.ok(
                SuccessResponse.success(response)
        );
    }

    /**
     * 비공개 테스트케이스가 포함될 수 있는 문제 버전 이력을 관리자에게 조회합니다.
     */
    @Override
    @RequireAdmin
    public ResponseEntity<SuccessResponse<List<ProblemVersionResponse>>> getProblemVersions(UUID problemId) {
        List<ProblemVersionResponse> response = problemVersionService.getProblemVersions(problemId)
                .stream()
                .map(ProblemVersionResponse::from)
                .toList();
        return ResponseEntity.ok(SuccessResponse.success(response));
    }

    /**
     * 문제의 특정 버전 스냅샷을 관리자에게 조회합니다.
     */
    @Override
    @RequireAdmin
    public ResponseEntity<SuccessResponse<ProblemVersionResponse>> getProblemVersion(
            UUID problemId,
            Integer versionNo
    ) {
        ProblemVersion problemVersion = problemVersionService.getProblemVersion(problemId, versionNo);
        return ResponseEntity.ok(SuccessResponse.success(ProblemVersionResponse.from(problemVersion)));
    }

    /**
     * REVIEW_PENDING 상태의 문제 발행을 승인합니다.
     *
     * @param problemId 발행을 승인할 문제의 고유 ID
     * @return 응답 데이터가 없는 성공 응답
     */
    @Override
    @RequireAdmin
    public ResponseEntity<SuccessResponse<Void>> approvePublication(UUID problemId) {
        // TODO: Facade 방식으로 (관리자가 문제 상태를 PUBLISHED로 변경 -> ProblemVersion 생성/저장 -> 발행 이벤트 기록)
        problemPublicationFacade.approvePublication(problemId);

        return ResponseEntity.ok(
                SuccessResponse.empty()
        );
    }

    /**
     * 발행된 문제를 재발행하기 위해 관리자 승인 대기(REVIEW_PENDING) 상태로 되돌립니다.
     *
     * <p>이슈 #253 — ProblemPublished 이벤트가 어떤 이유로든(버그, Kafka 장애 등)
     * 다른 서비스에 정상 반영되지 못했을 때, 삭제·재생성 없이 기존 문제 그대로
     * 재발행할 수 있도록 하는 복구 경로입니다. 이 API 호출 후 기존
     * {@code POST /{problemId}/approve}를 다시 호출하면 새 ProblemVersion과
     * 새 ProblemPublished 이벤트가 생성됩니다.</p>
     *
     * @param problemId 재발행을 위해 되돌릴 문제의 고유 ID
     * @return 응답 데이터가 없는 성공 응답
     */
    @Override
    @RequireAdmin
    public ResponseEntity<SuccessResponse<Void>> revertToReviewPendingForRepublish(UUID problemId) {
        problemPublicationFacade.revertToReviewPendingForRepublish(problemId);

        return ResponseEntity.ok(
                SuccessResponse.empty()
        );
    }



    // TODO: POST /api/v1/admin/problems/{problemId}/reject
}
