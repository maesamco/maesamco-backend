package com.maesamco.content.problem.domain.entity;

import com.maesamco.content.testcase.domain.entity.TestCase;
import com.maesamco.content.testcase.domain.enums.TestCaseStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 문제 발행에 사용되는 테스트케이스 생성 규칙을 검증합니다.
 */
class TestCaseTest {

    @Test
    @DisplayName(
            "관리자가 테스트케이스를 생성하면 "
                    + "문제 식별자와 채점 정보가 APPROVED 상태로 저장된다"
    )
    void createByAdmin_storesApprovedTestCaseData() {
        // given
        UUID problemId = UUID.randomUUID();

        // when
        TestCase testCase =
                TestCase.createByAdmin(
                        problemId,
                        "1 2",
                        "3",
                        true,
                        1
                );

        // then
        assertThat(testCase.getProblemId())
                .isEqualTo(problemId);

        assertThat(testCase.getIsPublic())
                .isTrue();

        assertThat(testCase.getInput())
                .isEqualTo("1 2");

        assertThat(testCase.getExpectedOutput())
                .isEqualTo("3");

        assertThat(testCase.getTestCaseOrder())
                .isEqualTo(1);

        assertThat(testCase.getTestCaseStatus())
                .isEqualTo(TestCaseStatus.APPROVED);
    }
}
