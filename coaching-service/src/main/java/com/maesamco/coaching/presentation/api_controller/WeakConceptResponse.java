package com.maesamco.coaching.presentation.api_controller;

import com.maesamco.coaching.domain.entity.WeakConcept;

import java.time.Instant;

/**
 * 코칭 서비스 API 명세 7·8번 API 응답 항목 — 두 API 모두 같은 모양이라
 * `WeakConceptApiController`(사용자용)/`WeakConceptInternalController`(내부용)가 공유한다.
 */
public record WeakConceptResponse(String conceptTag, int occurrenceCount, Instant lastDetectedAt, boolean improved) {

    public static WeakConceptResponse from(WeakConcept weakConcept) {
        return new WeakConceptResponse(
                weakConcept.getConceptTag(),
                weakConcept.getOccurrenceCount(),
                weakConcept.getLastDetectedAt(),
                weakConcept.isImproved()
        );
    }
}
