package com.maesamco.content.presentation.response;

import com.maesamco.content.application.result.ConceptValidationInternalResult;

import java.util.List;
import java.util.UUID;

/**
 * 내부 서비스용 개념 일괄 검증 응답 DTO(이슈 #309).
 *
 * <p>User Service의 {@code ConceptValidationResponse}와 필드명이 동일해야 한다
 * (이슈 #199 "Content Service 연동 계약" 섹션) — {@code valid}/{@code validConceptIds}/
 * {@code invalidConceptIds}.</p>
 */
public record ConceptValidationResponse(
        boolean valid,
        List<UUID> validConceptIds,
        List<UUID> invalidConceptIds
) {

    public static ConceptValidationResponse from(ConceptValidationInternalResult result) {
        return new ConceptValidationResponse(
                result.valid(),
                result.validConceptIds(),
                result.invalidConceptIds()
        );
    }
}
