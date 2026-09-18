package com.maesamco.content.presentation.response;

import com.maesamco.content.application.result.ProblemInternalResult;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

/** 내부 서비스용 문제 조회 응답 DTO */
@Getter
@AllArgsConstructor
public class InternalProblemResponse {

    /**
     * Coaching Service가 ProblemSnapshot.problemId로 매핑하는 필드입니다.
     * 외부 응답 필드명은 기존 계약대로 id를 유지합니다.
     */
    private final UUID id;

    private final String description;

    private final List<String> conceptTags;

    /** Application 조회 결과를 내부 서비스용 응답 DTO로 변환합니다. */
    public static InternalProblemResponse from(ProblemInternalResult result) {
        return new InternalProblemResponse(
                result.getId(),
                result.getDescription(),
                result.getConceptTags()
        );
    }
}