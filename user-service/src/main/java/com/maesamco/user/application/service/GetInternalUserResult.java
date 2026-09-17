package com.maesamco.user.application.service;

import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Content Service에 제공하는 내부 사용자 조회 결과입니다.
 *
 * <p>Daily Quiz 콜드스타트에 필요한 활성 관심 개념 ID만
 * 서비스 간 계약으로 제공합니다.</p>
 */
public record GetInternalUserResult(
        List<UUID> interestConceptIds
) {

    /**
     * 응답 목록의 null 요소와 외부 변경 가능성을 차단합니다.
     */
    public GetInternalUserResult {
        if (interestConceptIds == null) {
            throw invalidResult(
                    "관심 개념 ID 목록은 필수입니다."
            );
        }

        boolean containsNull =
                interestConceptIds
                        .stream()
                        .anyMatch(
                                Objects::isNull
                        );

        if (containsNull) {
            throw invalidResult(
                    "관심 개념 ID는 null일 수 없습니다."
            );
        }

        interestConceptIds =
                List.copyOf(
                        interestConceptIds
                );
    }

    /**
     * 잘못 구성된 내부 조회 결과에 사용할 예외를 생성합니다.
     */
    private static BusinessException invalidResult(
            String message
    ) {
        return new BusinessException(
                ErrorCode.INVALID_INPUT_VALUE,
                message
        );
    }
}
