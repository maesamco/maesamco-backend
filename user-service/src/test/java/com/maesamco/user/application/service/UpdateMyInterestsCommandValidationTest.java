package com.maesamco.user.application.service;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 관심 개념 전체 교체 명령의 Validation 정책을 검증합니다.
 */
class UpdateMyInterestsCommandValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory =
                Validation.buildDefaultValidatorFactory();

        validator =
                validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    @DisplayName(
            "유효한 관심 개념 목록은 Validation을 통과한다"
    )
    void validCommand() {
        UpdateMyInterestsCommand command =
                new UpdateMyInterestsCommand(
                        List.of(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                UUID.randomUUID()
                        )
                );

        assertThat(
                validator.validate(command)
        ).isEmpty();
    }

    @Test
    @DisplayName(
            "빈 목록은 모든 관심 개념 해제 요청으로 허용한다"
    )
    void acceptsEmptyConceptIds() {
        UpdateMyInterestsCommand command =
                new UpdateMyInterestsCommand(
                        List.of()
                );

        assertThat(
                validator.validate(command)
        ).isEmpty();
    }

    @Test
    @DisplayName(
            "관심 개념 목록이 null이면 Validation에 실패한다"
    )
    void rejectsNullConceptIds() {
        UpdateMyInterestsCommand command =
                new UpdateMyInterestsCommand(
                        null
                );

        assertThat(
                invalidFields(command)
        ).contains("conceptIds");
    }

    @Test
    @DisplayName(
            "관심 개념을 10개까지 설정할 수 있다"
    )
    void acceptsTenConceptIds() {
        List<UUID> conceptIds =
                createConceptIds(10);

        UpdateMyInterestsCommand command =
                new UpdateMyInterestsCommand(
                        conceptIds
                );

        assertThat(
                validator.validate(command)
        ).isEmpty();
    }

    @Test
    @DisplayName(
            "관심 개념이 11개이면 Validation에 실패한다"
    )
    void rejectsElevenConceptIds() {
        UpdateMyInterestsCommand command =
                new UpdateMyInterestsCommand(
                        createConceptIds(11)
                );

        assertThat(
                invalidFields(command)
        ).contains("conceptIds");
    }

    @Test
    @DisplayName(
            "중복된 개념 ID는 입력 순서를 유지하면서 제거한다"
    )
    void removesDuplicatedConceptIds() {
        UUID firstConceptId =
                UUID.randomUUID();

        UUID secondConceptId =
                UUID.randomUUID();

        UpdateMyInterestsCommand command =
                new UpdateMyInterestsCommand(
                        List.of(
                                firstConceptId,
                                secondConceptId,
                                firstConceptId,
                                secondConceptId
                        )
                );

        assertThat(command.conceptIds())
                .containsExactly(
                        firstConceptId,
                        secondConceptId
                );

        assertThat(
                validator.validate(command)
        ).isEmpty();
    }

    @Test
    @DisplayName(
            "목록 내부에 null 개념 ID가 있으면 Validation에 실패한다"
    )
    void rejectsNullConceptIdElement() {
        UpdateMyInterestsCommand command =
                new UpdateMyInterestsCommand(
                        Arrays.asList(
                                UUID.randomUUID(),
                                null
                        )
                );

        assertThat(
                validator.validate(command)
        ).anyMatch(
                violation ->
                        violation.getPropertyPath()
                                .toString()
                                .startsWith("conceptIds")
        );
    }

    @Test
    @DisplayName(
            "관심 개념 목록은 생성 후 외부에서 변경할 수 없다"
    )
    void conceptIdsAreImmutable() {
        UpdateMyInterestsCommand command =
                new UpdateMyInterestsCommand(
                        List.of(
                                UUID.randomUUID()
                        )
                );

        assertThatThrownBy(
                () -> command.conceptIds()
                        .add(UUID.randomUUID())
        ).isInstanceOf(
                UnsupportedOperationException.class
        );
    }

    private List<UUID> createConceptIds(
            int count
    ) {
        return IntStream.range(
                        0,
                        count
                )
                .mapToObj(
                        index ->
                                new UUID(
                                        0L,
                                        index + 1L
                                )
                )
                .toList();
    }

    private Set<String> invalidFields(
            UpdateMyInterestsCommand command
    ) {
        return validator.validate(command)
                .stream()
                .map(
                        violation ->
                                violation.getPropertyPath()
                                        .toString()
                )
                .collect(
                        java.util.stream.Collectors.toSet()
                );
    }

    @Test
    @DisplayName(
            "동일한 개념 ID가 10개를 초과해 반복돼도 "
                    + "중복 제거 후 10개 이하이면 허용한다"
    )
    void acceptsDuplicatedIdsWhenUniqueCountIsWithinLimit() {
        UUID conceptId = UUID.randomUUID();

        List<UUID> conceptIds =
                IntStream.range(0, 50)
                        .mapToObj(index -> conceptId)
                        .toList();

        UpdateMyInterestsCommand command =
                new UpdateMyInterestsCommand(
                        conceptIds
                );

        assertThat(command.conceptIds())
                .containsExactly(conceptId);

        assertThat(
                validator.validate(command)
        ).isEmpty();
    }
}
