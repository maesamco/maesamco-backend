package com.maesamco.content.domain.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TestCase 도메인 테스트")
class TestCaseTest {

    private static final UUID PROBLEM_ID =
            UUID.randomUUID();

    private static final String INPUT =
            "1 2";

    private static final String EXPECTED_OUTPUT =
            "3";

    private static final Boolean IS_PUBLIC =
            true;

    private static final Integer TEST_CASE_ORDER =
            1;

    // ============================================================
    // 1. TestCase 생성
    // ============================================================

    @Nested
    @DisplayName("TestCase 생성")
    class Create {

        @Test
        @DisplayName("관리자가 TestCase 생성 시 전달한 값이 저장된다")
        void createByAdmin_setsFields() {

            // when
            TestCase testCase =
                    TestCase.createByAdmin(
                            PROBLEM_ID,
                            INPUT,
                            EXPECTED_OUTPUT,
                            IS_PUBLIC,
                            TEST_CASE_ORDER
                    );

            // then
            assertThat(testCase.getProblemId())
                    .isEqualTo(PROBLEM_ID);

            assertThat(testCase.getInput())
                    .isEqualTo(INPUT);

            assertThat(testCase.getExpectedOutput())
                    .isEqualTo(EXPECTED_OUTPUT);

            assertThat(testCase.getIsPublic())
                    .isEqualTo(IS_PUBLIC);

            assertThat(testCase.getTestCaseOrder())
                    .isEqualTo(TEST_CASE_ORDER);
        }

        @Test
        @DisplayName("공개 TestCase를 생성할 수 있다")
        void createByAdmin_publicTestCase() {

            // when
            TestCase testCase =
                    TestCase.createByAdmin(
                            PROBLEM_ID,
                            INPUT,
                            EXPECTED_OUTPUT,
                            true,
                            TEST_CASE_ORDER
                    );

            // then
            assertThat(testCase.getIsPublic())
                    .isTrue();
        }

        @Test
        @DisplayName("비공개 TestCase를 생성할 수 있다")
        void createByAdmin_privateTestCase() {

            // when
            TestCase testCase =
                    TestCase.createByAdmin(
                            PROBLEM_ID,
                            INPUT,
                            EXPECTED_OUTPUT,
                            false,
                            TEST_CASE_ORDER
                    );

            // then
            assertThat(testCase.getIsPublic())
                    .isFalse();
        }

        @Test
        @DisplayName("새로 생성된 TestCase는 삭제 상태가 아니다")
        void createByAdmin_notDeleted() {

            // when
            TestCase testCase =
                    createTestCase();

            // then
            assertThat(testCase.getDeletedAt())
                    .isNull();

            assertThat(testCase.getDeletedBy())
                    .isNull();
        }
    }

    // ============================================================
    // 2. TestCase 순서 변경
    // ============================================================

    @Nested
    @DisplayName("TestCase 순서 변경")
    class ChangeTestCaseOrder {

        @Test
        @DisplayName("changeTestCaseOrder 호출 시 순서가 변경된다")
        void changeTestCaseOrder_changesOrder() {

            // given
            TestCase testCase =
                    createTestCase();

            Integer newOrder =
                    3;

            // when
            testCase.changeTestCaseOrder(
                    newOrder
            );

            // then
            assertThat(testCase.getTestCaseOrder())
                    .isEqualTo(newOrder);
        }

        @Test
        @DisplayName("순서를 변경해도 기존 TestCase 정보는 유지된다")
        void changeTestCaseOrder_keepsOtherFields() {

            // given
            TestCase testCase =
                    createTestCase();

            // when
            testCase.changeTestCaseOrder(
                    5
            );

            // then
            assertThat(testCase.getProblemId())
                    .isEqualTo(PROBLEM_ID);

            assertThat(testCase.getInput())
                    .isEqualTo(INPUT);

            assertThat(testCase.getExpectedOutput())
                    .isEqualTo(EXPECTED_OUTPUT);

            assertThat(testCase.getIsPublic())
                    .isEqualTo(IS_PUBLIC);

            assertThat(testCase.getTestCaseOrder())
                    .isEqualTo(5);
        }
    }

    // ============================================================
    // 3. TestCase 삭제
    // ============================================================

    @Nested
    @DisplayName("TestCase 삭제")
    class Delete {

        @Test
        @DisplayName("softDelete 호출 시 삭제 시간과 삭제자가 기록된다")
        void softDelete_setsDeleteInformation() {

            // given
            TestCase testCase =
                    createTestCase();

            UUID userId =
                    UUID.randomUUID();

            // when
            testCase.softDelete(
                    userId
            );

            // then
            assertThat(testCase.getDeletedAt())
                    .isNotNull();

            assertThat(testCase.getDeletedBy())
                    .isEqualTo(userId);
        }

        @Test
        @DisplayName("삭제해도 TestCase의 기존 정보는 유지된다")
        void softDelete_keepsTestCaseFields() {

            // given
            TestCase testCase =
                    createTestCase();

            UUID userId =
                    UUID.randomUUID();

            // when
            testCase.softDelete(
                    userId
            );

            // then
            assertThat(testCase.getProblemId())
                    .isEqualTo(PROBLEM_ID);

            assertThat(testCase.getInput())
                    .isEqualTo(INPUT);

            assertThat(testCase.getExpectedOutput())
                    .isEqualTo(EXPECTED_OUTPUT);

            assertThat(testCase.getIsPublic())
                    .isEqualTo(IS_PUBLIC);

            assertThat(testCase.getTestCaseOrder())
                    .isEqualTo(TEST_CASE_ORDER);

            assertThat(testCase.getDeletedAt())
                    .isNotNull();

            assertThat(testCase.getDeletedBy())
                    .isEqualTo(userId);
        }
    }

    // ============================================================
    // Fixture
    // ============================================================

    private TestCase createTestCase() {
        return TestCase.createByAdmin(
                PROBLEM_ID,
                INPUT,
                EXPECTED_OUTPUT,
                IS_PUBLIC,
                TEST_CASE_ORDER
        );
    }
}