package com.maesamco.content.presentation.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * 내부 서비스용 개념(Concept) 일괄 검증 요청 DTO(이슈 #309).
 *
 * <p>User Service의 {@code ConceptValidationRequest}와 계약이 동일하다
 * (이슈 #199 "Content Service 연동 계약" 섹션).</p>
 */
public record ConceptValidationRequest(
        @NotEmpty(message = "conceptIds는 비어 있을 수 없습니다.")
        List<@NotNull UUID> conceptIds
) {
}
