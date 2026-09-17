package com.maesamco.content.domain.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Curriculum 도메인 테스트")
class CurriculumTest {

    private static final String TITLE = "Java 기초";
    private static final ProgrammingLanguage LANGUAGE =
            ProgrammingLanguage.JAVA;
    private static final Integer DISPLAY_ORDER = 1;

    // ============================================================
    // 1. Curriculum 생성
    // ============================================================

    @Nested
    @DisplayName("Curriculum 생성")
    class Create {

        @Test
        @DisplayName("Curriculum 생성 시 전달한 값이 저장된다")
        void create_setsFields() {

            // when
            Curriculum curriculum =
                    Curriculum.create(
                            TITLE,
                            LANGUAGE,
                            DISPLAY_ORDER
                    );

            // then
            assertThat(curriculum.getTitle())
                    .isEqualTo(TITLE);

            assertThat(curriculum.getLanguage())
                    .isEqualTo(LANGUAGE);

            assertThat(curriculum.getDisplayOrder())
                    .isEqualTo(DISPLAY_ORDER);
        }

        @Test
        @DisplayName("새로 생성된 Curriculum은 삭제 상태가 아니다")
        void create_notDeleted() {

            // when
            Curriculum curriculum =
                    Curriculum.create(
                            TITLE,
                            LANGUAGE,
                            DISPLAY_ORDER
                    );

            // then
            assertThat(curriculum.getDeletedAt())
                    .isNull();

            assertThat(curriculum.getDeletedBy())
                    .isNull();
        }
    }

    // ============================================================
    // 2. 제목 변경
    // ============================================================

    @Nested
    @DisplayName("Curriculum 제목 변경")
    class ChangeTitle {

        @Test
        @DisplayName("changeTitle 호출 시 제목이 변경된다")
        void changeTitle_changesTitle() {

            // given
            Curriculum curriculum =
                    createCurriculum();

            String newTitle =
                    "Java 심화";

            // when
            curriculum.changeTitle(
                    newTitle
            );

            // then
            assertThat(curriculum.getTitle())
                    .isEqualTo(newTitle);
        }

        @Test
        @DisplayName("제목을 변경해도 언어와 순서는 변경되지 않는다")
        void changeTitle_keepsOtherFields() {

            // given
            Curriculum curriculum =
                    createCurriculum();

            // when
            curriculum.changeTitle(
                    "변경된 제목"
            );

            // then
            assertThat(curriculum.getLanguage())
                    .isEqualTo(LANGUAGE);

            assertThat(curriculum.getDisplayOrder())
                    .isEqualTo(DISPLAY_ORDER);
        }
    }

    // ============================================================
    // 3. 언어 변경
    // ============================================================

    @Nested
    @DisplayName("Curriculum 언어 변경")
    class ChangeLanguage {

        @Test
        @DisplayName("changeLanguage 호출 시 언어가 변경된다")
        void changeLanguage_changesLanguage() {

            // given
            Curriculum curriculum =
                    createCurriculum();

            // when
            curriculum.changeLanguage(
                    ProgrammingLanguage.PYTHON
            );

            // then
            assertThat(curriculum.getLanguage())
                    .isEqualTo(
                            ProgrammingLanguage.PYTHON
                    );
        }

        @Test
        @DisplayName("언어를 변경해도 제목과 순서는 변경되지 않는다")
        void changeLanguage_keepsOtherFields() {

            // given
            Curriculum curriculum =
                    createCurriculum();

            // when
            curriculum.changeLanguage(
                    ProgrammingLanguage.PYTHON
            );

            // then
            assertThat(curriculum.getTitle())
                    .isEqualTo(TITLE);

            assertThat(curriculum.getDisplayOrder())
                    .isEqualTo(DISPLAY_ORDER);
        }

        @Test
        @DisplayName("제목과 언어를 각각 변경할 수 있다")
        void changeTitleAndLanguage_changesBoth() {

            // given
            Curriculum curriculum =
                    createCurriculum();

            // when
            curriculum.changeTitle(
                    "Python 기초"
            );

            curriculum.changeLanguage(
                    ProgrammingLanguage.PYTHON
            );

            // then
            assertThat(curriculum.getTitle())
                    .isEqualTo(
                            "Python 기초"
                    );

            assertThat(curriculum.getLanguage())
                    .isEqualTo(
                            ProgrammingLanguage.PYTHON
                    );

            assertThat(curriculum.getDisplayOrder())
                    .isEqualTo(DISPLAY_ORDER);
        }
    }

    // ============================================================
    // 4. Curriculum 삭제
    // ============================================================

    @Nested
    @DisplayName("Curriculum 삭제")
    class Delete {

        @Test
        @DisplayName("softDelete 호출 시 삭제 시간과 삭제자가 기록된다")
        void softDelete_setsDeleteInformation() {

            // given
            Curriculum curriculum =
                    createCurriculum();

            UUID userId =
                    UUID.randomUUID();

            // when
            curriculum.softDelete(
                    userId
            );

            // then
            assertThat(curriculum.getDeletedAt())
                    .isNotNull();

            assertThat(curriculum.getDeletedBy())
                    .isEqualTo(userId);
        }

        @Test
        @DisplayName("삭제해도 Curriculum의 기존 정보는 유지된다")
        void softDelete_keepsCurriculumFields() {

            // given
            Curriculum curriculum =
                    createCurriculum();

            UUID userId =
                    UUID.randomUUID();

            // when
            curriculum.softDelete(
                    userId
            );

            // then
            assertThat(curriculum.getTitle())
                    .isEqualTo(TITLE);

            assertThat(curriculum.getLanguage())
                    .isEqualTo(LANGUAGE);

            assertThat(curriculum.getDisplayOrder())
                    .isEqualTo(DISPLAY_ORDER);

            assertThat(curriculum.getDeletedAt())
                    .isNotNull();

            assertThat(curriculum.getDeletedBy())
                    .isEqualTo(userId);
        }
    }

    // ============================================================
    // Fixture
    // ============================================================

    private Curriculum createCurriculum() {
        return Curriculum.create(
                TITLE,
                LANGUAGE,
                DISPLAY_ORDER
        );
    }
}