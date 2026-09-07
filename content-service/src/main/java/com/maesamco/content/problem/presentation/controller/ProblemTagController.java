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
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 문제와 태그의 연결 조회, 등록, 제거를 위한 API를 제공합니다.
 *
 * <p>특정 문제에 등록된 태그 목록 조회,
 * 문제에 태그 등록 및 제거 로직은 {@link ProblemTagService}에 위임합니다.</p>
 *
 * <p>모든 정상 응답은 {@link SuccessResponse}로 감싸 반환하며,
 * 목록 조회 결과는 {@link PageResponse}를 사용하여 페이징 정보를 함께 제공합니다.</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/problems/{problemId}/tags")
public class ProblemTagController {

    /** 문제와 태그의 연결 조회, 등록, 제거 비즈니스 로직을 담당하는 서비스입니다. */
    private final ProblemTagService problemTagService;

    /**
     * 특정 문제에 등록된 태그 목록을 조회합니다.
     *
     * <p>문제 ID를 기준으로 연결된 태그를 조회하며,
     * 조회 결과는 페이징 정보와 함께 반환합니다.</p>
     *
     * @param problemId 조회할 문제의 고유 ID
     * @param page 조회할 페이지 번호
     * @param size 한 페이지에 조회할 태그 개수
     * @return 특정 문제에 등록된 페이징된 태그 목록
     */
    @GetMapping
    public ResponseEntity<SuccessResponse<PageResponse<TagResponse>>> getProblemTags(
            @PathVariable UUID problemId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        Pageable pageable = PageableFactory.of(page, size, null, null);

        PageResponse<TagResponse> response = problemTagService.searchProblemTags(problemId, pageable);

        return ResponseEntity.ok(
                SuccessResponse.success(response)
        );
    }

    /**
     * 문제에 태그를 등록합니다.
     *
     * @param problemId 태그를 등록할 문제의 고유 ID
     * @param tagId 등록할 태그의 고유 ID
     * @return 응답 데이터가 없는 성공 응답
     */
    @PostMapping("/{tagId}")
    public ResponseEntity<SuccessResponse<Void>> addTagToProblem(
            @PathVariable UUID problemId,
            @PathVariable UUID tagId
    ) {
        problemTagService.addTagToProblem(problemId, tagId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(SuccessResponse.empty());
    }

    /**
     * 문제에서 태그를 제거합니다.
     *
     * @param problemId 태그를 제거할 문제의 고유 ID
     * @param tagId 제거할 태그의 고유 ID
     * @return 응답 데이터가 없는 성공 응답
     */
    @DeleteMapping("/{tagId}")
    public ResponseEntity<SuccessResponse<Void>> removeTagFromProblem(
            @PathVariable UUID problemId,
            @PathVariable UUID tagId
    ) {
        problemTagService.removeTagFromProblem(problemId, tagId);

        return ResponseEntity.ok(
                SuccessResponse.empty()
        );
    }
}