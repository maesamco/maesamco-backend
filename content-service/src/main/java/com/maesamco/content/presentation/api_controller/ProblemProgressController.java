package com.maesamco.content.presentation.api_controller;

import com.maesamco.content.application.persistence_service.ProblemProgressService;
import com.maesamco.content.domain.entity.problem.ProblemProgress;
import com.maesamco.content.domain.entity.problem.ProblemProgressStatus;
import com.maesamco.content.global.response.PageResponse;
import com.maesamco.content.global.response.SuccessResponse;
import com.maesamco.content.global.util.PageableFactory;
import com.maesamco.content.presentation.response.ProblemProgressResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 사용자의 문제 풀이 진행 이력을 조회하기 위한 API를 제공합니다.
 *
 * <p>사용자의 전체 문제 풀이 이력 조회, 풀이 상태별 이력 조회,
 * 특정 문제에 대한 풀이 이력 조회 기능을 제공합니다.</p>
 *
 * <p>문제 풀이 이력 조회는 {@link ProblemProgressService}에 위임하며,
 * 모든 정상 응답은 {@link SuccessResponse}로 감싸 반환합니다.</p>
 *
 * <p>목록 조회 결과는 {@link PageResponse}를 사용해
 * 페이징 정보를 함께 제공합니다.</p>
 */
@Tag(
        name = "Problem Progress",
        description = "사용자 문제 풀이 진행 이력 조회 API"
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/contents/problem-progress")
public class ProblemProgressController {

    /** 문제 풀이 진행 이력 조회 비즈니스 로직을 담당하는 서비스입니다. */
    private final ProblemProgressService problemProgressService;

    /**
     * 현재 사용자의 문제 풀이 이력 목록을 조회합니다.
     *
     * <p>{@code progressStatus}가 전달되지 않은 경우
     * 사용자의 전체 문제 풀이 이력을 조회합니다.</p>
     *
     * <p>{@code progressStatus}가 전달된 경우
     * 해당 상태에 해당하는 문제 풀이 이력만 조회합니다.</p>
     *
     * <p>조회 결과는 최근 생성된 이력부터 정렬하며,
     * {@link PageResponse}를 사용해 페이징 정보를 함께 반환합니다.</p>
     *
     * @param userId 현재 인증된 사용자의 고유 ID
     * @param progressStatus 조회할 문제 풀이 상태
     * @param page 조회할 페이지 번호
     * @param size 한 페이지에 조회할 문제 풀이 이력 개수
     * @return 사용자의 페이징된 문제 풀이 이력 목록
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<SuccessResponse<PageResponse<ProblemProgressResponse>>> getProblemProgresses(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(required = false) ProblemProgressStatus progressStatus,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        Pageable pageable = PageableFactory.of(page, size, null, null);

        Page<ProblemProgress> problemProgresses =
                problemProgressService.getProblemProgresses(userId, progressStatus, pageable);

        PageResponse<ProblemProgressResponse> response =
                PageResponse.from(
                        problemProgresses,
                        ProblemProgressResponse::from
                );

        return ResponseEntity.ok(
                SuccessResponse.success(response)
        );
    }

    /**
     * 현재 사용자의 특정 문제에 대한 풀이 이력을 조회합니다.
     *
     * <p>현재 인증된 사용자와 문제 ID를 기준으로
     * 해당 문제의 풀이 진행 이력을 조회합니다.</p>
     *
     * <p>사용자가 해당 문제를 한 번도 풀이하지 않은 경우
     * 문제 풀이 이력을 찾을 수 없다는 예외가 발생합니다.</p>
     *
     * @param problemId 조회할 문제의 고유 ID
     * @param userId 현재 인증된 사용자의 고유 ID
     * @return 특정 문제의 풀이 이력을 포함한 성공 응답
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{problemId}")
    public ResponseEntity<SuccessResponse<ProblemProgressResponse>> getProblemProgress(
            @PathVariable UUID problemId,
            @AuthenticationPrincipal UUID userId
    ) {
        ProblemProgress problemProgress =
                problemProgressService.getProblemProgress(
                        userId,
                        problemId
                );

        return ResponseEntity.ok(
                SuccessResponse.success(
                        ProblemProgressResponse.from(problemProgress)
                )
        );
    }
}