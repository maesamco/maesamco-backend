package com.maesamco.content.application.result;

import java.util.List;
import java.util.UUID;

/** 내부 서비스용 개념 일괄 검증 결과(이슈 #309). */
public record ConceptValidationInternalResult(
        boolean valid,
        List<UUID> validConceptIds,
        List<UUID> invalidConceptIds
) {
}
