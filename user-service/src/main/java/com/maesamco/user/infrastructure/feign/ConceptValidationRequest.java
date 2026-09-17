package com.maesamco.user.infrastructure.feign;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Content Service에 전달하는 개념 일괄 검증 요청입니다.
 *
 * @param conceptIds 검증할 개념 식별자 목록
 */
public record ConceptValidationRequest(
        List<UUID> conceptIds
) {

    public ConceptValidationRequest {
        Objects.requireNonNull(
                conceptIds,
                "검증할 개념 ID 목록은 필수입니다."
        );

        conceptIds = List.copyOf(conceptIds);
    }
}
