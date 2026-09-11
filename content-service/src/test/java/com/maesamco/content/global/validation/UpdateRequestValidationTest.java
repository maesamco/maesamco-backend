package com.maesamco.content.global.validation;

import com.maesamco.content.curriculum.presentation.dto.request.CurriculumUpdateRequest;
import com.maesamco.content.lesson.presentation.dto.request.LessonUpdateRequest;
import com.maesamco.content.tag.presentation.dto.request.TagUpdateRequest;
import com.maesamco.content.unit.presentation.dto.request.UnitUpdateRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory =
                Validation.buildDefaultValidatorFactory();

        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @ParameterizedTest(
            name = "{0}의 {2} 필드는 공백만 입력할 수 없다"
    )
    @MethodSource("blankStringFields")
    @DisplayName("수정 요청의 문자열 필드는 공백만 입력하면 검증에 실패한다")
    void updateRequest_blankString_isRejected(
            String requestName,
            Object request,
            String fieldName
    ) {
        // given
        ReflectionTestUtils.setField(
                request,
                fieldName,
                "   "
        );

        // when
        var violations = validator.validate(request);

        // then
        assertThat(violations)
                .extracting(violation ->
                        violation.getPropertyPath().toString()
                )
                .contains(fieldName);
    }

    @Test
    @DisplayName("수정 요청의 선택 필드는 null이면 검증을 통과한다")
    void updateRequest_nullOptionalFields_areAccepted() {
        // given
        List<Object> requests = List.of(
                new CurriculumUpdateRequest(),
                new UnitUpdateRequest(),
                new LessonUpdateRequest(),
                new TagUpdateRequest()
        );

        // when & then
        assertThat(requests)
                .allSatisfy(request ->
                        assertThat(validator.validate(request))
                                .isEmpty()
                );
    }

    private static Stream<Arguments> blankStringFields() {
        return Stream.of(
                Arguments.of(
                        "CurriculumUpdateRequest",
                        new CurriculumUpdateRequest(),
                        "title"
                ),
                Arguments.of(
                        "UnitUpdateRequest",
                        new UnitUpdateRequest(),
                        "title"
                ),
                Arguments.of(
                        "LessonUpdateRequest",
                        new LessonUpdateRequest(),
                        "title"
                ),
                Arguments.of(
                        "LessonUpdateRequest",
                        new LessonUpdateRequest(),
                        "description"
                ),
                Arguments.of(
                        "LessonUpdateRequest",
                        new LessonUpdateRequest(),
                        "content"
                ),
                Arguments.of(
                        "TagUpdateRequest",
                        new TagUpdateRequest(),
                        "name"
                )
        );
    }
}
