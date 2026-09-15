package com.maesamco.user.application.service;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * 로그인 사용자의 관심 개념 전체 교체 명령입니다.
 *
 * <p>중복된 개념 ID는 입력 순서를 유지하면서 하나로 정리합니다.
 * 빈 목록은 모든 관심 개념을 해제하는 요청으로 처리합니다.</p>
 *
 * @param conceptIds 새롭게 설정할 관심 개념 식별자 목록
 */
@Schema(
        name = "UpdateMyInterestsCommand",
        description = "로그인 사용자 관심 개념 전체 교체 요청"
)
public record UpdateMyInterestsCommand(

        @ArraySchema(
                arraySchema = @Schema(
                        description = "새롭게 설정할 관심 개념 ID 목록",
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        maxLength = 10
                ),
                schema = @Schema(
                        type = "string",
                        format = "uuid",
                        example = "11111111-1111-1111-1111-111111111111"
                ),
                maxItems = 10,
                uniqueItems = true
        )
        @NotNull(message = "관심 개념 ID 목록은 필수입니다.")
        @Size(
                max = 10,
                message = "관심 개념은 최대 10개까지 선택할 수 있습니다."
        )
        List<
                @Valid
                @NotNull(message = "개념 ID는 필수입니다.")
                        UUID
                > conceptIds
) {

    /**
     * 중복된 개념 ID를 입력 순서를 유지하면서 제거합니다.
     */
    public UpdateMyInterestsCommand {
        if (conceptIds != null) {
            conceptIds = conceptIds
                    .stream()
                    .distinct()
                    .toList();
        }
    }
}
