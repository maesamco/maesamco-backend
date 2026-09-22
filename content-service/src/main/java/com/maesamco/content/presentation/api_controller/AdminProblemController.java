package com.maesamco.content.presentation.api_controller;

import com.maesamco.content.application.facade.ProblemPublicationFacade;
import com.maesamco.content.global.response.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping(" /api/v1/admin/contents/problems")
public class AdminProblemController {

    private final ProblemPublicationFacade problemPublicationFacade;


    // TODO: GET  /api/v1/admin/problems ( problemStatus 필터 )

    /**
     * REVIEW_PENDING 상태의 문제 발행을 승인합니다.
     *
     * @param problemId 발행을 승인할 문제의 고유 ID
     * @return 응답 데이터가 없는 성공 응답
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{problemId}/approve")
    public ResponseEntity<SuccessResponse<Void>> approvePublication(
            @PathVariable UUID problemId
    ) {
        // TODO: Facade 방식으로 (관리자가 문제 상태를 PUBLISHED로 변경 -> ProblemVersion 생성/저장 -> 발행 이벤트 기록)
        problemPublicationFacade.approvePublication(problemId);

        return ResponseEntity.ok(
                SuccessResponse.empty()
        );
    }



    // TODO: POST /api/v1/admin/problems/{problemId}/reject
}