package com.maesamco.content.problem;

import com.maesamco.content.global.config.JacksonConfig;
import com.maesamco.content.problem.presentation.dto.request.ProblemUpdateRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jackson.test.autoconfigure.JsonTest;
import org.springframework.boot.test.json.JacksonTester;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@JsonTest
@Import(JacksonConfig.class)
class JsonNullableModuleTest {

    @Autowired
    private JacksonTester<ProblemUpdateRequest> json;

    @Test
    @DisplayName("starterCode에 값이 전달되면 present 상태로 역직렬화된다")
    void deserialize_starterCodeWithValue_isPresent() throws Exception {

        // given
        String content = """
                {
                    "starterCode": "public class Main {}"
                }
                """;

        // when
        ProblemUpdateRequest request =
                json.parseObject(content);

        // then
        assertThat(request.getStarterCode())
                .isNotNull();

        assertThat(request.getStarterCode().isPresent())
                .isTrue();

        assertThat(request.getStarterCode().orElse(null))
                .isEqualTo("public class Main {}");
    }

    @Test
    @DisplayName("starterCode에 null이 명시되면 present 상태이면서 값은 null이다")
    void deserialize_starterCodeWithNull_isPresent() throws Exception {

        // given
        String content = """
                {
                    "starterCode": null
                }
                """;

        // when
        ProblemUpdateRequest request =
                json.parseObject(content);

        // then
        assertThat(request.getStarterCode())
                .isNotNull();

        assertThat(request.getStarterCode().isPresent())
                .isTrue();

        assertThat(request.getStarterCode().orElse("default"))
                .isNull();
    }

    @Test
    @DisplayName("starterCode가 생략되면 undefined 상태로 유지된다")
    void deserialize_starterCodeOmitted_isUndefined() throws Exception {

        // given
        String content = """
                {}
                """;

        // when
        ProblemUpdateRequest request =
                json.parseObject(content);

        // then
        assertThat(request.getStarterCode())
                .isNotNull();

        assertThat(request.getStarterCode().isPresent())
                .isFalse();
    }
}