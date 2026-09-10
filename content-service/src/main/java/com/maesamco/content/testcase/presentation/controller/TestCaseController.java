package com.maesamco.content.testcase.presentation.controller;

import com.maesamco.content.global.response.PageResponse;
import com.maesamco.content.global.response.SuccessResponse;
import com.maesamco.content.global.util.PageableFactory;
import com.maesamco.content.testcase.application.service.TestCaseService;
import com.maesamco.content.testcase.presentation.dto.request.TestCaseCreateRequest;
import com.maesamco.content.testcase.presentation.dto.request.TestCaseUpdateRequest;
import com.maesamco.content.testcase.presentation.dto.response.TestCaseCreateResponse;
import com.maesamco.content.testcase.presentation.dto.response.TestCaseResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 테스트케이스 생성, 조회, 수정, 삭제를 위한 API를 제공합니다.
 *
 * <p>테스트케이스 생성, 단건 조회, 특정 문제의 테스트케이스 목록 조회,
 * 수정 및 삭제 로직은 {@link TestCaseService}에 위임합니다.</p>
 *
 * <p>모든 정상 응답은 {@link SuccessResponse}로 감싸 반환하며,
 * 목록 조회 결과는 {@link PageResponse}를 사용하여 페이징 정보를 함께 제공합니다.</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class TestCaseController {

    /** 테스트케이스 생성, 조회, 수정, 삭제 비즈니스 로직을 담당하는 서비스입니다. */
    private final TestCaseService testCaseService;

    /**
     * 새로운 테스트케이스를 생성합니다.
     *
     * <p>문제 ID를 기준으로 상위 문제를 확인하고,
     * 요청 정보를 검증한 뒤 테스트케이스를 생성합니다.</p>
     *
     * @param problemId 테스트케이스가 속할 문제의 고유 ID
     * @param request 테스트케이스 생성 요청 정보
     * @return 생성된 테스트케이스 정보를 포함한 성공 응답
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/contents/problems/{problemId}/test-cases")
    public ResponseEntity<SuccessResponse<TestCaseCreateResponse>> createTestCase(
            @PathVariable UUID problemId,
            @Valid @RequestBody TestCaseCreateRequest request
    ) {
        TestCaseCreateResponse response = testCaseService.createTestCase(problemId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(SuccessResponse.success(response));
    }

    /**
     * 테스트케이스 ID를 기준으로 단일 테스트케이스를 조회합니다.
     *
     * @param testCaseId 조회할 테스트케이스의 고유 ID
     * @return 조회된 테스트케이스 정보를 포함한 성공 응답
     */
    @GetMapping("/contents/test-cases/{testCaseId}")
    public ResponseEntity<SuccessResponse<TestCaseResponse>> getTestCase(
            @PathVariable UUID testCaseId,
            Authentication authentication
    ) {
        TestCaseResponse response = null;
        if (isAdmin(authentication)) {
            response = testCaseService.getTestCase(testCaseId);
        }
        else {
            response = testCaseService.getPublicTestCase(testCaseId);
        }

        return ResponseEntity.ok(
                SuccessResponse.success(response)
        );
    }

    /**
     * 특정 문제에 속한 테스트케이스 목록을 조회합니다.
     *
     * <p>문제 ID를 기준으로 삭제되지 않은 테스트케이스를 조회하며,
     * 조회 결과는 페이징 정보와 함께 반환합니다.</p>
     *
     * @param problemId 조회할 문제의 고유 ID
     * @param page 조회할 페이지 번호
     * @param size 한 페이지에 조회할 테스트케이스 개수
     * @return 특정 문제의 페이징된 테스트케이스 목록
     */
    @GetMapping("/contents/problems/{problemId}/test-cases")
    public ResponseEntity<SuccessResponse<PageResponse<TestCaseResponse>>> getTestCases(
            @PathVariable UUID problemId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            Authentication authentication
    ) {
        Pageable pageable = PageableFactory.of(page, size, null, null);

        PageResponse<TestCaseResponse> response = null;
        if (isAdmin(authentication)) {
            response = testCaseService.searchTestCasesAll(problemId, pageable); // ADMIN
        }
        else {
            response = testCaseService.searchTestCasesPublic(problemId, pageable); // 비로그인, 로그인을 했지만 ADMIN이 아닌 사용자
        }

        return ResponseEntity.ok(
                SuccessResponse.success(response)
        );
    }

    /**
     * 지정한 테스트케이스의 정보를 수정합니다.
     *
     * <p>테스트케이스 ID를 기준으로 수정 대상을 조회하고,
     * 요청에 포함된 값을 반영합니다.</p>
     *
     * @param testCaseId 수정할 테스트케이스의 고유 ID
     * @param request 테스트케이스 수정 요청 정보
     * @return 수정된 테스트케이스 정보를 포함한 성공 응답
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/contents/test-cases/{testCaseId}")
    public ResponseEntity<SuccessResponse<TestCaseResponse>> updateTestCase(
            @PathVariable UUID testCaseId,
            @Valid @RequestBody TestCaseUpdateRequest request
    ) {
        TestCaseResponse response = testCaseService.updateTestCase(testCaseId, request);

        return ResponseEntity.ok(
                SuccessResponse.success(response)
        );
    }

    /**
     * 지정한 테스트케이스를 삭제합니다.
     *
     * <p>테스트케이스 ID를 기준으로 삭제 대상을 조회한 뒤,
     * 실제 데이터를 제거하지 않고 Soft Delete 방식으로 처리합니다.</p>
     *
     * @param testCaseId 삭제할 테스트케이스의 고유 ID
     * @param userId 삭제를 요청한 사용자의 고유 ID
     * @return 응답 데이터가 없는 성공 응답
     */
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/contents/test-cases/{testCaseId}")
    public ResponseEntity<SuccessResponse<Void>> deleteTestCase(
            @PathVariable UUID testCaseId,
            @AuthenticationPrincipal UUID userId
    ) {
        testCaseService.deleteTestCase(testCaseId, userId);

        return ResponseEntity.ok(
                SuccessResponse.empty()
        );
    }


    /** 현재 인증 사용자가 ADMIN 권한을 가지고 있는지 확인합니다. */
    private boolean isAdmin(Authentication authentication) {
        return authentication != null &&
                authentication.getAuthorities().stream()
                        .anyMatch(authority ->
                                "ROLE_ADMIN".equals(authority.getAuthority())
                        );
    }
}
