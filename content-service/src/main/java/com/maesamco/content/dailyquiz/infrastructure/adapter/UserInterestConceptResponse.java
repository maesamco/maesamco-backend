package com.maesamco.content.dailyquiz.infrastructure.adapter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.UUID;

/**
 * User Service의 사용자 정보 응답 중 정보가 없는 즉 Daily Quiz 콜드스타트에 필요한
 * 관심 개념 ID만 전달받는 DTO입니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserInterestConceptResponse(
        List<UUID> interestConceptIds
) {
    public UserInterestConceptResponse {
        interestConceptIds = interestConceptIds == null
                ? List.of()
                : List.copyOf(interestConceptIds);
    }
}
