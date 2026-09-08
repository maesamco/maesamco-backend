package com.maesamco.content.problem.domain.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 문제 테스트케이스 생성 규칙을 검증합니다.
 */
class TestCaseTest {

    @Test
    @DisplayName(
            "테스트케이스를 생성하면 "
                    + "문제 식별자와 채점 정보가 저장된다"
    )
    void create_storesTestCaseData() {
        UUID problemId = UUID.randomUUID();

        TestCase testCase =
                TestCase.create(
                        problemId,
                        true,
                        "1 2",
                        "3",
                        1
                );

        assertThat(testCase.getProblemId())
                .isEqualTo(problemId);

        assertThat(testCase.isPublic())
                .isTrue();

        assertThat(testCase.getInput())
                .isEqualTo("1 2");

        assertThat(testCase.getExpectedOutput())
                .isEqualTo("3");

        assertThat(testCase.getDisplayOrder())
                .isEqualTo(1);
    }
}
