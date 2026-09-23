package com.maesamco.content.presentation.api_controller;

import com.maesamco.content.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

/**
 * Admin Problem API Swagger 문서입니다.
 *
 * <p>문제 발행 승인 및 재발행과 같은
 * 관리자 전용 문제 관리 API에 대한 명세를 제공합니다.</p>
 */
public interface AdminProblemApiDocs {

    @Operation(
            summary = "문제 발행 승인",
            description = """
                    REVIEW_PENDING 상태의 문제 발행을 승인합니다.

                    승인되면 문제 상태가 PUBLISHED로 변경되고,
                    현재 문제와 테스트케이스 정보를 기준으로 ProblemVersion 스냅샷을 생성합니다.

                    동일한 트랜잭션에서 PROBLEM_PUBLISHED 이벤트를 Transactional Outbox에 기록하며,
                    기록된 이벤트는 이후 Outbox 발행 로직을 통해 Kafka로 전달됩니다.

                    따라서 API 성공 응답은 문제 발행 승인과 Outbox 기록이 정상적으로 완료되었음을 의미하며,
                    Kafka 브로커로의 실제 전송 완료 시점과는 다를 수 있습니다.

                    ADMIN 권한이 필요합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "문제 발행 승인 및 발행 이벤트 기록 성공"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "ADMIN 권한 없음"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "PROBLEM_NOT_FOUND - 문제를 찾을 수 없음"
            )
    })
    ResponseEntity<SuccessResponse<Void>> approvePublication(
            @Parameter(
                    description = "발행을 승인할 문제 ID",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            UUID problemId
    );

    @Operation(
            summary = "문제 재발행 준비",
            description = """
                    발행된 문제를 재발행할 수 있도록
                    PUBLISHED 상태에서 REVIEW_PENDING 상태로 되돌립니다.

                    기존 문제를 삭제하거나 새로 생성하지 않고,
                    동일한 문제를 다시 발행하기 위한 복구 경로입니다.

                    이 API 호출 후 문제 발행 승인 API를 다시 호출하면
                    현재 문제와 테스트케이스 정보를 기준으로 새로운 ProblemVersion이 생성되고,
                    새로운 PROBLEM_PUBLISHED 이벤트가 Transactional Outbox에 기록됩니다.

                    Kafka 장애 또는 이벤트 처리 실패 등으로 인해
                    기존 ProblemPublished 이벤트를 다른 서비스에 다시 반영해야 하는 경우 사용할 수 있습니다.

                    ADMIN 권한이 필요합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "문제를 REVIEW_PENDING 상태로 변경 성공"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "ADMIN 권한 없음"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "PROBLEM_NOT_FOUND - 문제를 찾을 수 없음"
            )
    })
    ResponseEntity<SuccessResponse<Void>> revertToReviewPendingForRepublish(
            @Parameter(
                    description = "재발행할 문제 ID",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            UUID problemId
    );
}