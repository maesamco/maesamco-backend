package com.maesamco.user.application.service;

import com.maesamco.user.domain.entity.LearningLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 로그인 사용자의 기본 정보 수정 명령입니다.
 *
 * @param nickname 변경할 사용자 닉네임
 * @param learningLevel 변경할 Java 학습 수준
 * @param javaExperienceMonths 변경할 Java 학습 경험 개월 수
 */
@Schema(
        name = "UpdateMyProfileCommand",
        description = "로그인 사용자 기본 정보 수정 요청"
)
public record UpdateMyProfileCommand(

        @Schema(
                description = "변경할 사용자 닉네임",
                example = "새닉네임",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(
                min = 2,
                max = 20,
                message = "닉네임은 2자 이상 20자 이하여야 합니다."
        )
        @Pattern(
                regexp = "^[가-힣A-Za-z0-9]+$",
                message = "닉네임은 한글, 영문, 숫자만 사용할 수 있습니다."
        )
        String nickname,

        @Schema(
                description = "변경할 Java 학습 수준",
                example = "BASIC",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "학습 수준은 필수입니다.")
        LearningLevel learningLevel,

        @Schema(
                description = "변경할 Java 학습 경험 개월 수",
                example = "6",
                minimum = "0",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "Java 경험 개월 수는 필수입니다.")
        @PositiveOrZero(
                message = "Java 경험 개월 수는 0 이상이어야 합니다."
        )
        Integer javaExperienceMonths
) {

    /**
     * 닉네임의 앞뒤 공백을 제거합니다.
     */
    public UpdateMyProfileCommand {
        if (nickname != null) {
            nickname = nickname.trim();
        }
    }
}
