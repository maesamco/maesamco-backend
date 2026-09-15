package com.maesamco.user.infrastructure.feign;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Content Service의 개념 일괄 검증 응답 중 data 영역입니다.
 *
 * @param valid 요청한 모든 개념의 사용 가능 여부
 * @param validConceptIds 존재하며 활성화된 개념 ID 목록
 * @param invalidConceptIds 존재하지 않거나 비활성화된 개념 ID 목록
 */
public record ConceptValidationResponse(
        boolean valid,
        List<UUID> validConceptIds,
        List<UUID> invalidConceptIds
) {

    public ConceptValidationResponse {
        Objects.requireNonNull(
                validConceptIds,
                "유효한 개념 ID 목록은 필수입니다."
        );
        Objects.requireNonNull(
                invalidConceptIds,
                "유효하지 않은 개념 ID 목록은 필수입니다."
        );

        validConceptIds = List.copyOf(validConceptIds);
        invalidConceptIds = List.copyOf(invalidConceptIds);
    }
}
