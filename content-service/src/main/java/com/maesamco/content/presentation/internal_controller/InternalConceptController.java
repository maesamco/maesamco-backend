package com.maesamco.content.presentation.internal_controller;

import com.maesamco.content.application.persistence_service.ConceptValidationInternalService;
import com.maesamco.content.application.result.ConceptValidationInternalResult;
import com.maesamco.content.global.response.SuccessResponse;
import com.maesamco.content.global.security.hmac.AllowedInternalCallers;
import com.maesamco.content.presentation.request.ConceptValidationRequest;
import com.maesamco.content.presentation.response.ConceptValidationResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내부 서비스용 개념(Concept) 검증 API(이슈 #309, 이슈 #199 "Content Service 연동 계약").
 *
 * <p>User Service의 {@code PUT /api/v1/users/me/interests}(이슈 #199)가 관심 개념을
 * 저장하기 전에 이 API로 개념 ID 목록이 실존하며 활성 상태인지 한 번에 검증한다.</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1")
@AllowedInternalCallers({"user-service"})
public class InternalConceptController {

    private final ConceptValidationInternalService conceptValidationInternalService;

    /** 내부 서비스용 개념 ID 목록 일괄 검증 */
    @PostMapping("/concepts/validate")
    public ResponseEntity<SuccessResponse<ConceptValidationResponse>> validateConcepts(
            @Valid @RequestBody ConceptValidationRequest request
    ) {
        ConceptValidationInternalResult result =
                conceptValidationInternalService.validate(request.conceptIds());

        return ResponseEntity.ok(
                SuccessResponse.success(ConceptValidationResponse.from(result))
        );
    }
}
