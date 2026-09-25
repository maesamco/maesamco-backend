package com.maesamco.user.application.service;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 로그인 사용자의 현재 관심 개념 조회 결과입니다.
 *
 * <p>Content Service의 개념 이름은 이 서비스가 소유하지 않으므로 식별자만 내려주고,
 * 이름 매핑은 클라이언트가 개념 목록 조회 결과로 한다.</p>
 *
 * @param interestConceptIds 현재 저장된 관심 개념 ID 목록
 * @param count 관심 개념 개수
 */
@Schema(
        name = "GetMyInterestsResult",
        description = "로그인 사용자의 현재 관심 개념 조회 결과"
)
public record GetMyInterestsResult(

        @Schema(
                description = "현재 저장된 관심 개념 ID 목록. 설정한 개념이 없으면 빈 배열입니다.",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        List<UUID> interestConceptIds,

        @Schema(
                description = "관심 개념 개수",
                example = "3",
                minimum = "0",
                maximum = "10",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        int count
) {

    public GetMyInterestsResult {
        Objects.requireNonNull(
                interestConceptIds,
                "관심 개념 ID 목록은 필수입니다."
        );

        interestConceptIds = List.copyOf(interestConceptIds);

        if (count != interestConceptIds.size()) {
            throw new IllegalArgumentException(
                    "관심 개념 개수와 목록 크기가 일치해야 합니다."
            );
        }
    }

    /**
     * 관심 개념 ID 목록으로 조회 결과를 만듭니다.
     */
    public static GetMyInterestsResult of(
            List<UUID> interestConceptIds
    ) {
        Objects.requireNonNull(
                interestConceptIds,
                "관심 개념 ID 목록은 필수입니다."
        );

        return new GetMyInterestsResult(
                interestConceptIds,
                interestConceptIds.size()
        );
    }
}
