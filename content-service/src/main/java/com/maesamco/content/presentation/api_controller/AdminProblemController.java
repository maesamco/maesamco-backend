package com.maesamco.content.presentation.api_controller;

import com.maesamco.content.application.facade.ProblemPublicationFacade;
import com.maesamco.content.global.response.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AdminProblemController implements AdminProblemApiDocs {

    private final ProblemPublicationFacade problemPublicationFacade;


    // TODO: GET  /api/v1/admin/problems ( problemStatus 필터 )

    /**
     * REVIEW_PENDING 상태의 문제 발행을 승인합니다.
     *
     * @param problemId 발행을 승인할 문제의 고유 ID
     * @return 응답 데이터가 없는 성공 응답
     */
    @Override
    @PreAuthorize("hasRole('ADMIN')")
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
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SuccessResponse<Void>> revertToReviewPendingForRepublish(UUID problemId) {
        problemPublicationFacade.revertToReviewPendingForRepublish(problemId);

        return ResponseEntity.ok(
                SuccessResponse.empty()
        );
    }



    // TODO: POST /api/v1/admin/problems/{problemId}/reject
}