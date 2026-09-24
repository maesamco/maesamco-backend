package com.maesamco.content.presentation.api_controller;

import com.maesamco.content.global.response.PageResponse;
import com.maesamco.content.global.response.SuccessResponse;
import com.maesamco.content.presentation.request.TestCaseCreateRequest;
import com.maesamco.content.presentation.request.TestCaseUpdateRequest;
import com.maesamco.content.presentation.response.TestCaseCreateResponse;
import com.maesamco.content.presentation.response.TestCaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Controller
@RequestMapping("/api/v1")
@Tag(name = "Test Case", description = "테스트케이스 생성, 조회, 수정, 삭제 API")
public interface TestCaseApiDocs {

    @PostMapping("/contents/problems/{problemId}/test-cases")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "테스트케이스 생성",
            description = "특정 문제에 새로운 테스트케이스를 생성합니다. "
                    + "생성 전 상위 문제의 존재 여부를 확인하고 입력값, 예상 출력값, 공개 여부 등의 정보를 기준으로 등록합니다. "
                    + "ADMIN 권한이 필요합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "테스트케이스 생성 성공"),
            @ApiResponse(responseCode = "400", description = "요청 값 검증 실패"),
            @ApiResponse(responseCode = "401", description = "AUTH_UNAUTHORIZED — 인증되지 않은 요청"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음"),
            @ApiResponse(responseCode = "404", description = "PROBLEM_NOT_FOUND — 문제를 찾을 수 없음")
    })
    ResponseEntity<SuccessResponse<TestCaseCreateResponse>> createTestCase(
            @Parameter(description = "테스트케이스를 등록할 문제 ID")
            @PathVariable UUID problemId,
            @Valid @RequestBody TestCaseCreateRequest request
    );

    @GetMapping("/contents/test-cases/{testCaseId}")
    @Operation(
            summary = "테스트케이스 단건 조회",
            description = "테스트케이스 ID를 기준으로 단일 테스트케이스를 조회합니다. "
                    + "ADMIN 사용자는 공개 여부와 관계없이 조회할 수 있고 일반 사용자 또는 비로그인 사용자는 공개된 테스트케이스만 조회할 수 있습니다. "
                    + "비공개 테스트케이스는 ADMIN이 아닌 사용자에게 노출되지 않습니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "테스트케이스 조회 성공"),
            @ApiResponse(responseCode = "404", description = "TEST_CASE_NOT_FOUND — 테스트케이스를 찾을 수 없거나 조회할 수 없는 비공개 테스트케이스")
    })
    ResponseEntity<SuccessResponse<TestCaseResponse>> getTestCase(
            @Parameter(description = "조회할 테스트케이스 ID")
            @PathVariable UUID testCaseId,
            @Parameter(hidden = true)
            Authentication authentication
    );

    @GetMapping("/contents/problems/{problemId}/test-cases")
    @Operation(
            summary = "문제별 테스트케이스 목록 조회",
            description = "특정 문제에 속한 테스트케이스 목록을 페이징하여 조회합니다. "
                    + "ADMIN 사용자는 전체 테스트케이스를 조회하고 일반 사용자 또는 비로그인 사용자는 공개된 테스트케이스만 조회합니다. "
                    + "page와 size를 생략하면 기본 페이징 값이 적용됩니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "테스트케이스 목록 조회 성공"),
            @ApiResponse(responseCode = "404", description = "PROBLEM_NOT_FOUND — 문제를 찾을 수 없음")
    })
    ResponseEntity<SuccessResponse<PageResponse<TestCaseResponse>>> getTestCases(
            @Parameter(description = "테스트케이스 목록을 조회할 문제 ID")
            @PathVariable UUID problemId,

            @Parameter(
                    name = "page",
                    description = "조회할 페이지 번호",
                    in = ParameterIn.QUERY,
                    schema = @Schema(
                            type = "integer",
                            defaultValue = "0",
                            minimum = "0"
                    )
            )
            @RequestParam(required = false) Integer page,

            @Parameter(
                    name = "size",
                    description = "한 페이지에 조회할 테스트케이스 개수",
                    in = ParameterIn.QUERY,
                    schema = @Schema(
                            type = "integer",
                            defaultValue = "20",
                            minimum = "1",
                            maximum = "100"
                    )
            )
            @RequestParam(required = false) Integer size,

            @Parameter(hidden = true)
            Authentication authentication
    );

    @PatchMapping("/contents/test-cases/{testCaseId}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "테스트케이스 수정",
            description = "테스트케이스 ID를 기준으로 테스트케이스 정보를 수정합니다. "
                    + "요청에 포함된 값만 변경하며 입력값, 예상 출력값, 공개 여부 등의 정보를 수정할 수 있습니다. "
                    + "ADMIN 권한이 필요합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "테스트케이스 수정 성공"),
            @ApiResponse(responseCode = "400", description = "요청 값 검증 실패"),
            @ApiResponse(responseCode = "401", description = "AUTH_UNAUTHORIZED — 인증되지 않은 요청"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음"),
            @ApiResponse(responseCode = "404", description = "TEST_CASE_NOT_FOUND — 테스트케이스를 찾을 수 없음")
    })
    ResponseEntity<SuccessResponse<TestCaseResponse>> updateTestCase(
            @Parameter(description = "수정할 테스트케이스 ID")
            @PathVariable UUID testCaseId,
            @Valid @RequestBody TestCaseUpdateRequest request
    );

    @DeleteMapping("/contents/test-cases/{testCaseId}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "테스트케이스 삭제",
            description = "테스트케이스 ID를 기준으로 테스트케이스를 삭제합니다. "
                    + "실제 데이터를 제거하지 않고 Soft Delete 방식으로 처리하며 삭제 후 남은 테스트케이스의 순서가 다시 정리될 수 있습니다. "
                    + "ADMIN 권한이 필요합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "테스트케이스 삭제 성공"),
            @ApiResponse(responseCode = "401", description = "AUTH_UNAUTHORIZED — 인증되지 않은 요청"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음"),
            @ApiResponse(responseCode = "404", description = "TEST_CASE_NOT_FOUND — 테스트케이스를 찾을 수 없음")
    })
    ResponseEntity<SuccessResponse<Void>> deleteTestCase(
            @Parameter(description = "삭제할 테스트케이스 ID")
            @PathVariable UUID testCaseId,
            @Parameter(hidden = true)
            @AuthenticationPrincipal UUID userId
    );
}