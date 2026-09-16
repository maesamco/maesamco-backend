package com.maesamco.user.application.service;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 로그인 사용자의 관심 개념 전체 교체 결과입니다.
 *
 * @param interestConceptIds 최종 저장된 관심 개념 식별자 목록
 * @param count 최종 저장된 관심 개념 개수
 * @param updatedAt 관심 개념 변경 완료 시각
 */
@Schema(
        name = "UpdateMyInterestsResult",
        description = "로그인 사용자의 관심 개념 전체 교체 결과"
)
public record UpdateMyInterestsResult(

        @ArraySchema(
                arraySchema = @Schema(
                        description = "최종 저장된 관심 개념 ID 목록",
                        requiredMode = Schema.RequiredMode.REQUIRED
                ),
                schema = @Schema(
                        type = "string",
                        format = "uuid",
                        example = "11111111-1111-1111-1111-111111111111"
                ),
                maxItems = 10,
                uniqueItems = true
        )
        List<UUID> interestConceptIds,

        @Schema(
                description = "설정된 관심 개념 개수",
                example = "3",
                minimum = "0",
                maximum = "10",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        int count,

        @Schema(
                description = "관심 개념의 마지막 변경 시각. "
                        + "변경 이력이 없으면 null입니다.",
                example = "2026-09-15T06:30:00Z",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED,
                nullable = true
        )
        Instant updatedAt
) {

    /**
     * 결과 목록을 불변 목록으로 복사하고 필수값과 개수를 검증합니다.
     */
    public UpdateMyInterestsResult {
        Objects.requireNonNull(
                interestConceptIds,
                "관심 개념 ID 목록은 필수입니다."
        );

        interestConceptIds =
                List.copyOf(interestConceptIds);

        if (count != interestConceptIds.size()) {
            throw new IllegalArgumentException(
                    "관심 개념 개수와 목록 크기가 일치해야 합니다."
            );
        }

        if (count > 10) {
            throw new IllegalArgumentException(
                    "관심 개념은 최대 10개까지 반환할 수 있습니다."
            );
        }
    }
}
