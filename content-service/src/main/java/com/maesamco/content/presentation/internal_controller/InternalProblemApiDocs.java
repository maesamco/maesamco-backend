package com.maesamco.content.presentation.internal_controller;

import com.maesamco.content.global.response.SuccessResponse;
import com.maesamco.content.presentation.response.InternalProblemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

@Controller
@RequestMapping("/internal/v1")
@Tag(name = "Internal Problem", description = "내부 서비스용 문제 및 문제 버전 조회 API")
public interface InternalProblemApiDocs {

    @GetMapping("/problems/{problemId}")
    @Operation(
            summary = "내부 문제 단건 조회",
            description = "문제 ID를 기준으로 내부 서비스에서 필요한 문제 메타데이터를 조회합니다. "
                    + "coaching-service와 같이 허용된 내부 서비스만 호출할 수 있습니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "내부 문제 조회 성공"),
            @ApiResponse(responseCode = "403", description = "허용되지 않은 내부 서비스 호출"),
            @ApiResponse(responseCode = "404", description = "PROBLEM_NOT_FOUND — 문제를 찾을 수 없음")
    })
    ResponseEntity<SuccessResponse<InternalProblemResponse>> getProblem(
            @Parameter(description = "조회할 문제 ID")
            @PathVariable UUID problemId
    );

    @GetMapping("/problem-versions/{problemVersionId}")
    @Operation(
            summary = "내부 문제 버전 단건 조회",
            description = "문제 버전 ID를 기준으로 제출 시점의 문제 버전 메타데이터를 조회합니다. "
                    + "버전이 고정된 문제 정보가 필요한 허용된 내부 서비스에서 사용합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "내부 문제 버전 조회 성공"),
            @ApiResponse(responseCode = "403", description = "허용되지 않은 내부 서비스 호출"),
            @ApiResponse(responseCode = "404", description = "문제 버전을 찾을 수 없음")
    })
    ResponseEntity<SuccessResponse<InternalProblemResponse>> getProblemVersion(
            @Parameter(description = "조회할 문제 버전 ID")
            @PathVariable UUID problemVersionId
    );
}