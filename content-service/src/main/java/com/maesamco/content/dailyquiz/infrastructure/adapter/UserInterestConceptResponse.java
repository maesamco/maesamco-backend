package com.maesamco.content.dailyquiz.infrastructure.adapter;

import java.util.List;
import java.util.UUID;

/**
 * User Service의 사용자 정보 응답에서
 * Daily Quiz 콜드스타트에 필요한 관심 개념 ID만 전달받는 DTO입니다.
 */
public record UserInterestConceptResponse(
        List<UUID> interestConceptIds
) {
    public UserInterestConceptResponse {
        interestConceptIds = interestConceptIds == null
                ? List.of()
                : List.copyOf(interestConceptIds);
    }
}
