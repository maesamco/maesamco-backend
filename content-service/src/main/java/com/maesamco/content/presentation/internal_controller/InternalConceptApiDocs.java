package com.maesamco.content.presentation.internal_controller;

import com.maesamco.content.global.response.SuccessResponse;
import com.maesamco.content.presentation.request.ConceptValidationRequest;
import com.maesamco.content.presentation.response.ConceptValidationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/internal/v1")
@Tag(name = "Internal Concept", description = "내부 서비스용 개념(Concept) 검증 API")
public interface InternalConceptApiDocs {

    @PostMapping("/concepts/validate")
    @Operation(
            summary = "내부 개념 일괄 검증",
            description = "개념 ID 목록이 모두 존재하며 활성 상태인지 한 번에 검증합니다. "
                    + "개념은 attribute가 CONCEPT인 태그이며, 삭제되었거나 다른 attribute인 태그 ID는 "
                    + "invalidConceptIds에 담깁니다. 요청은 최대 100개까지 허용하며, "
                    + "user-service와 같이 허용된 내부 서비스만 호출할 수 있습니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "검증 결과 반환 (일부가 무효여도 200이며 valid=false)"),
            @ApiResponse(responseCode = "400", description = "conceptIds가 비어 있거나 100개를 초과하거나 UUID 형식이 아님"),
            @ApiResponse(responseCode = "403", description = "허용되지 않은 내부 서비스 호출")
    })
    ResponseEntity<SuccessResponse<ConceptValidationResponse>> validateConcepts(
            @Valid @RequestBody ConceptValidationRequest request
    );
}
