package com.maesamco.content.domain.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Unit 도메인 테스트")
class UnitTest {

    private static final UUID CURRICULUM_ID =
            UUID.randomUUID();

    private static final String TITLE =
            "Java 기본 문법";

    private static final ProgrammingLanguage LANGUAGE =
            ProgrammingLanguage.JAVA;

    private static final Integer DISPLAY_ORDER =
            1;

    // ============================================================
    // 1. Unit 생성
    // ============================================================

    @Nested
    @DisplayName("Unit 생성")
    class Create {

        @Test
        @DisplayName("Unit 생성 시 전달한 값이 저장된다")
        void create_setsFields() {

            // when
            Unit unit =
                    Unit.create(
                            CURRICULUM_ID,
                            TITLE,
                            LANGUAGE,
                            DISPLAY_ORDER
                    );

            // then
            assertThat(unit.getCurriculumId())
                    .isEqualTo(CURRICULUM_ID);

            assertThat(unit.getTitle())
                    .isEqualTo(TITLE);

            assertThat(unit.getLanguage())
                    .isEqualTo(LANGUAGE);

            assertThat(unit.getDisplayOrder())
                    .isEqualTo(DISPLAY_ORDER);
        }

        @Test
        @DisplayName("새로 생성된 Unit은 삭제 상태가 아니다")
        void create_notDeleted() {

            // when
            Unit unit =
                    createUnit();

            // then
            assertThat(unit.getDeletedAt())
                    .isNull();

            assertThat(unit.getDeletedBy())
                    .isNull();
        }
    }

    // ============================================================
    // 2. 제목 변경
    // ============================================================

    @Nested
    @DisplayName("Unit 제목 변경")
    class ChangeTitle {

        @Test
        @DisplayName("changeTitle 호출 시 제목이 변경된다")
        void changeTitle_changesTitle() {

            // given
            Unit unit =
                    createUnit();

            String newTitle =
                    "Java 객체지향";

            // when
            unit.changeTitle(
                    newTitle
            );

            // then
            assertThat(unit.getTitle())
                    .isEqualTo(newTitle);
        }

        @Test
        @DisplayName("제목을 변경해도 다른 정보는 변경되지 않는다")
        void changeTitle_keepsOtherFields() {

            // given
            Unit unit =
                    createUnit();

            // when
            unit.changeTitle(
                    "변경된 Unit"
            );

            // then
            assertThat(unit.getCurriculumId())
                    .isEqualTo(CURRICULUM_ID);

            assertThat(unit.getLanguage())
                    .isEqualTo(LANGUAGE);

            assertThat(unit.getDisplayOrder())
                    .isEqualTo(DISPLAY_ORDER);
        }
    }

    // ============================================================
    // 3. 언어 변경
    // ============================================================

    @Nested
    @DisplayName("Unit 언어 변경")
    class ChangeLanguage {

        @Test
        @DisplayName("changeLanguage 호출 시 언어가 변경된다")
        void changeLanguage_changesLanguage() {

            // given
            Unit unit =
                    createUnit();

            // when
            unit.changeLanguage(
                    ProgrammingLanguage.PYTHON
            );

            // then
            assertThat(unit.getLanguage())
                    .isEqualTo(
                            ProgrammingLanguage.PYTHON
                    );
        }

        @Test
        @DisplayName("언어를 변경해도 기존 정보는 유지된다")
        void changeLanguage_keepsOtherFields() {

            // given
            Unit unit =
                    createUnit();

            // when
            unit.changeLanguage(
                    ProgrammingLanguage.PYTHON
            );

            // then
            assertThat(unit.getCurriculumId())
                    .isEqualTo(CURRICULUM_ID);

            assertThat(unit.getTitle())
                    .isEqualTo(TITLE);

            assertThat(unit.getDisplayOrder())
                    .isEqualTo(DISPLAY_ORDER);
        }
    }

    // ============================================================
    // 4. Unit 순서 변경
    // ============================================================

    @Nested
    @DisplayName("Unit 순서 변경")
    class ChangeDisplayOrder {

        @Test
        @DisplayName("changeDisplayOrder 호출 시 순서가 변경된다")
        void changeDisplayOrder_changesOrder() {

            // given
            Unit unit =
                    createUnit();

            Integer newDisplayOrder =
                    3;

            // when
            unit.changeDisplayOrder(
                    newDisplayOrder
            );

            // then
            assertThat(unit.getDisplayOrder())
                    .isEqualTo(newDisplayOrder);
        }

        @Test
        @DisplayName("순서를 변경해도 기존 정보는 유지된다")
        void changeDisplayOrder_keepsOtherFields() {

            // given
            Unit unit =
                    createUnit();

            // when
            unit.changeDisplayOrder(
                    5
            );

            // then
            assertThat(unit.getCurriculumId())
                    .isEqualTo(CURRICULUM_ID);

            assertThat(unit.getTitle())
                    .isEqualTo(TITLE);

            assertThat(unit.getLanguage())
                    .isEqualTo(LANGUAGE);

            assertThat(unit.getDisplayOrder())
                    .isEqualTo(5);
        }
    }

    // ============================================================
    // 5. Unit 삭제
    // ============================================================

    @Nested
    @DisplayName("Unit 삭제")
    class Delete {

        @Test
        @DisplayName("softDelete 호출 시 삭제 시간과 삭제자가 기록된다")
        void softDelete_setsDeleteInformation() {

            // given
            Unit unit =
                    createUnit();

            UUID userId =
                    UUID.randomUUID();

            // when
            unit.softDelete(
                    userId
            );

            // then
            assertThat(unit.getDeletedAt())
                    .isNotNull();

            assertThat(unit.getDeletedBy())
                    .isEqualTo(userId);
        }

        @Test
        @DisplayName("삭제해도 Unit의 기존 정보는 유지된다")
        void softDelete_keepsUnitFields() {

            // given
            Unit unit =
                    createUnit();

            UUID userId =
                    UUID.randomUUID();

            // when
            unit.softDelete(
                    userId
            );

            // then
            assertThat(unit.getCurriculumId())
                    .isEqualTo(CURRICULUM_ID);

            assertThat(unit.getTitle())
                    .isEqualTo(TITLE);

            assertThat(unit.getLanguage())
                    .isEqualTo(LANGUAGE);

            assertThat(unit.getDisplayOrder())
                    .isEqualTo(DISPLAY_ORDER);

            assertThat(unit.getDeletedAt())
                    .isNotNull();

            assertThat(unit.getDeletedBy())
                    .isEqualTo(userId);
        }
    }

    // ============================================================
    // Fixture
    // ============================================================

    private Unit createUnit() {
        return Unit.create(
                CURRICULUM_ID,
                TITLE,
                LANGUAGE,
                DISPLAY_ORDER
        );
    }
}