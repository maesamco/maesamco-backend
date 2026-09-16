package com.maesamco.user.application.service;

import com.maesamco.user.domain.entity.LearningLevel;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 내 정보 수정 명령의 Validation 정책을 검증합니다.
 */
class UpdateMyProfileCommandValidationTest {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory()
                    .getValidator();

    @Test
    @DisplayName(
            "정상적인 프로필 수정 명령은 Validation을 통과한다"
    )
    void validCommand() {
        UpdateMyProfileCommand command =
                new UpdateMyProfileCommand(
                        "새닉네임",
                        LearningLevel.BASIC,
                        6
                );

        Set<ConstraintViolation<UpdateMyProfileCommand>>
                violations =
                validator.validate(command);

        assertThat(violations)
                .isEmpty();
    }

    @Test
    @DisplayName(
            "닉네임의 앞뒤 공백을 제거한다"
    )
    void trimsNickname() {
        UpdateMyProfileCommand command =
                new UpdateMyProfileCommand(
                        "  새닉네임  ",
                        LearningLevel.BASIC,
                        6
                );

        assertThat(command.nickname())
                .isEqualTo("새닉네임");

        assertThat(validator.validate(command))
                .isEmpty();
    }

    @Test
    @DisplayName(
            "닉네임이 누락되거나 공백이면 Validation에 실패한다"
    )
    void rejectsMissingNickname() {
        UpdateMyProfileCommand nullNickname =
                new UpdateMyProfileCommand(
                        null,
                        LearningLevel.BASIC,
                        6
                );

        UpdateMyProfileCommand blankNickname =
                new UpdateMyProfileCommand(
                        "   ",
                        LearningLevel.BASIC,
                        6
                );

        assertThat(
                invalidFields(nullNickname)
        ).contains("nickname");

        assertThat(
                invalidFields(blankNickname)
        ).contains("nickname");
    }

    @Test
    @DisplayName(
            "닉네임이 2자 미만이거나 20자를 초과하면 Validation에 실패한다"
    )
    void rejectsInvalidNicknameLength() {
        UpdateMyProfileCommand shortNickname =
                new UpdateMyProfileCommand(
                        "김",
                        LearningLevel.BASIC,
                        6
                );

        UpdateMyProfileCommand longNickname =
                new UpdateMyProfileCommand(
                        "가".repeat(21),
                        LearningLevel.BASIC,
                        6
                );

        assertThat(
                invalidFields(shortNickname)
        ).contains("nickname");

        assertThat(
                invalidFields(longNickname)
        ).contains("nickname");
    }

    @Test
    @DisplayName(
            "닉네임에 허용되지 않은 문자가 포함되면 Validation에 실패한다"
    )
    void rejectsInvalidNicknameCharacters() {
        UpdateMyProfileCommand command =
                new UpdateMyProfileCommand(
                        "새 닉네임!",
                        LearningLevel.BASIC,
                        6
                );

        assertThat(
                invalidFields(command)
        ).contains("nickname");
    }

    @Test
    @DisplayName(
            "학습 수준이 누락되면 Validation에 실패한다"
    )
    void rejectsMissingLearningLevel() {
        UpdateMyProfileCommand command =
                new UpdateMyProfileCommand(
                        "새닉네임",
                        null,
                        6
                );

        assertThat(
                invalidFields(command)
        ).contains("learningLevel");
    }

    @Test
    @DisplayName(
            "Java 경험 개월 수가 누락되면 Validation에 실패한다"
    )
    void rejectsMissingJavaExperienceMonths() {
        UpdateMyProfileCommand command =
                new UpdateMyProfileCommand(
                        "새닉네임",
                        LearningLevel.BASIC,
                        null
                );

        assertThat(
                invalidFields(command)
        ).contains("javaExperienceMonths");
    }

    @Test
    @DisplayName(
            "Java 경험 개월 수가 음수이면 Validation에 실패한다"
    )
    void rejectsNegativeJavaExperienceMonths() {
        UpdateMyProfileCommand command =
                new UpdateMyProfileCommand(
                        "새닉네임",
                        LearningLevel.BASIC,
                        -1
                );

        assertThat(
                invalidFields(command)
        ).contains("javaExperienceMonths");
    }

    @Test
    @DisplayName(
            "Java 경험 개월 수로 0을 사용할 수 있다"
    )
    void acceptsZeroJavaExperienceMonths() {
        UpdateMyProfileCommand command =
                new UpdateMyProfileCommand(
                        "새닉네임",
                        LearningLevel.BEGINNER,
                        0
                );

        assertThat(validator.validate(command))
                .isEmpty();
    }

    private Set<String> invalidFields(
            UpdateMyProfileCommand command
    ) {
        return validator.validate(command)
                .stream()
                .map(
                        violation ->
                                violation.getPropertyPath()
                                        .toString()
                )
                .collect(
                        Collectors.toSet()
                );
    }
}
