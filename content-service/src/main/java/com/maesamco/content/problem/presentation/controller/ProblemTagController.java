package com.maesamco.content.problem.presentation.controller;

import com.maesamco.content.global.response.PageResponse;
import com.maesamco.content.global.response.SuccessResponse;
import com.maesamco.content.global.util.PageableFactory;
import com.maesamco.content.problem.application.service.ProblemTagService;
import com.maesamco.content.tag.presentation.dto.response.TagResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 문제와 태그의 연결 조회, 등록, 제거 API를 제공합니다.
 *
 * <p>태그 목록 조회는 공개 API로 제공하고,
 * 태그 등록과 제거는 관리자만 수행할 수 있습니다.</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/contents/problems/{problemId}/tags")
public class ProblemTagController {

    private final ProblemTagService problemTagService;

    /**
     * 특정 문제에 등록된 태그 목록을 조회합니다.
     *
     * @param problemId 조회할 문제 식별자
     * @param page 조회할 페이지 번호
     * @param size 한 페이지의 태그 개수
     * @return 문제에 등록된 태그 목록
     */
    @GetMapping
    public ResponseEntity<
            SuccessResponse<PageResponse<TagResponse>>
            > getProblemTags(
            @PathVariable UUID problemId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        Pageable pageable =
                PageableFactory.of(
                        page,
                        size,
                        null,
                        null
                );

        PageResponse<TagResponse> response =
                problemTagService.searchProblemTags(
                        problemId,
                        pageable
                );

        return ResponseEntity.ok(
                SuccessResponse.success(response)
        );
    }

    /**
     * 문제에 태그를 등록합니다.
     *
     * @param problemId 태그를 등록할 문제 식별자
     * @param tagId 등록할 태그 식별자
     * @return 데이터가 없는 성공 응답
     */
    @PostMapping("/{tagId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SuccessResponse<Void>> addTagToProblem(
            @PathVariable UUID problemId,
            @PathVariable UUID tagId
    ) {
        problemTagService.addTagToProblem(
                problemId,
                tagId
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(SuccessResponse.empty());
    }

    /**
     * 문제에서 태그 연결을 제거합니다.
     *
     * @param problemId 태그를 제거할 문제 식별자
     * @param tagId 제거할 태그 식별자
     * @return 데이터가 없는 성공 응답
     */
    @DeleteMapping("/{tagId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SuccessResponse<Void>> removeTagFromProblem(
            @PathVariable UUID problemId,
            @PathVariable UUID tagId
    ) {
        problemTagService.removeTagFromProblem(
                problemId,
                tagId
        );

        return ResponseEntity.ok(
                SuccessResponse.empty()
        );
    }
}
