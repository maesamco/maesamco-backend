package com.maesamco.content.domain.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Lesson 도메인 테스트")
class LessonTest {

    private static final UUID UNIT_ID =
            UUID.randomUUID();

    private static final String TITLE =
            "Java 변수";

    private static final ProgrammingLanguage LANGUAGE =
            ProgrammingLanguage.JAVA;

    private static final String DESCRIPTION =
            "Java 변수에 대해 학습합니다.";

    private static final String CONTENT =
            "변수는 값을 저장하기 위한 공간입니다.";

    private static final Integer DISPLAY_ORDER = 1;

    // ============================================================
    // 1. Lesson 생성
    // ============================================================

    @Nested
    @DisplayName("Lesson 생성")
    class Create {

        @Test
        @DisplayName("Lesson 생성 시 전달한 값이 저장된다")
        void create_setsFields() {

            // when
            Lesson lesson =
                    Lesson.create(
                            UNIT_ID,
                            TITLE,
                            DESCRIPTION,
                            CONTENT,
                            LANGUAGE,
                            DISPLAY_ORDER
                    );

            // then
            assertThat(lesson.getUnitId())
                    .isEqualTo(UNIT_ID);

            assertThat(lesson.getTitle())
                    .isEqualTo(TITLE);

            assertThat(lesson.getLanguage())
                    .isEqualTo(LANGUAGE);

            assertThat(lesson.getDescription())
                    .isEqualTo(DESCRIPTION);

            assertThat(lesson.getContent())
                    .isEqualTo(CONTENT);

            assertThat(lesson.getDisplayOrder())
                    .isEqualTo(DISPLAY_ORDER);
        }

        @Test
        @DisplayName("새로 생성된 Lesson은 삭제 상태가 아니다")
        void create_notDeleted() {

            // when
            Lesson lesson =
                    createLesson();

            // then
            assertThat(lesson.getDeletedAt())
                    .isNull();

            assertThat(lesson.getDeletedBy())
                    .isNull();
        }
    }

    // ============================================================
    // 2. Lesson 정보 변경
    // ============================================================

    @Nested
    @DisplayName("Lesson 정보 변경")
    class ChangeLesson {

        @Test
        @DisplayName("changeTitle 호출 시 제목이 변경된다")
        void changeTitle_changesTitle() {

            // given
            Lesson lesson =
                    createLesson();

            String newTitle =
                    "Java 조건문";

            // when
            lesson.changeTitle(
                    newTitle
            );

            // then
            assertThat(lesson.getTitle())
                    .isEqualTo(newTitle);

            assertThat(lesson.getUnitId())
                    .isEqualTo(UNIT_ID);
        }

        @Test
        @DisplayName("changeLanguage 호출 시 언어가 변경된다")
        void changeLanguage_changesLanguage() {

            // given
            Lesson lesson =
                    createLesson();

            // when
            lesson.changeLanguage(
                    ProgrammingLanguage.PYTHON
            );

            // then
            assertThat(lesson.getLanguage())
                    .isEqualTo(
                            ProgrammingLanguage.PYTHON
                    );

            assertThat(lesson.getTitle())
                    .isEqualTo(TITLE);
        }

        @Test
        @DisplayName("changeDescription 호출 시 설명이 변경된다")
        void changeDescription_changesDescription() {

            // given
            Lesson lesson =
                    createLesson();

            String newDescription =
                    "변경된 Lesson 설명";

            // when
            lesson.changeDescription(
                    newDescription
            );

            // then
            assertThat(lesson.getDescription())
                    .isEqualTo(newDescription);

            assertThat(lesson.getContent())
                    .isEqualTo(CONTENT);
        }

        @Test
        @DisplayName("changeContent 호출 시 내용이 변경된다")
        void changeContent_changesContent() {

            // given
            Lesson lesson =
                    createLesson();

            String newContent =
                    "변경된 학습 내용입니다.";

            // when
            lesson.changeContent(
                    newContent
            );

            // then
            assertThat(lesson.getContent())
                    .isEqualTo(newContent);

            assertThat(lesson.getDescription())
                    .isEqualTo(DESCRIPTION);
        }
    }

    // ============================================================
    // 3. Lesson 순서 변경
    // ============================================================

    @Nested
    @DisplayName("Lesson 순서 변경")
    class ChangeDisplayOrder {

        @Test
        @DisplayName("changeDisplayOrder 호출 시 순서가 변경된다")
        void changeDisplayOrder_changesOrder() {

            // given
            Lesson lesson =
                    createLesson();

            Integer newDisplayOrder =
                    3;

            // when
            lesson.changeDisplayOrder(
                    newDisplayOrder
            );

            // then
            assertThat(lesson.getDisplayOrder())
                    .isEqualTo(newDisplayOrder);

            assertThat(lesson.getUnitId())
                    .isEqualTo(UNIT_ID);

            assertThat(lesson.getTitle())
                    .isEqualTo(TITLE);
        }
    }

    // ============================================================
    // 4. Lesson 삭제
    // ============================================================

    @Nested
    @DisplayName("Lesson 삭제")
    class Delete {

        @Test
        @DisplayName("softDelete 호출 시 삭제 시간과 삭제자가 기록된다")
        void softDelete_setsDeleteInformation() {

            // given
            Lesson lesson =
                    createLesson();

            UUID userId =
                    UUID.randomUUID();

            // when
            lesson.softDelete(
                    userId
            );

            // then
            assertThat(lesson.getDeletedAt())
                    .isNotNull();

            assertThat(lesson.getDeletedBy())
                    .isEqualTo(userId);
        }

        @Test
        @DisplayName("삭제해도 Lesson의 기존 정보는 유지된다")
        void softDelete_keepsLessonFields() {

            // given
            Lesson lesson =
                    createLesson();

            UUID userId =
                    UUID.randomUUID();

            // when
            lesson.softDelete(
                    userId
            );

            // then
            assertThat(lesson.getUnitId())
                    .isEqualTo(UNIT_ID);

            assertThat(lesson.getTitle())
                    .isEqualTo(TITLE);

            assertThat(lesson.getLanguage())
                    .isEqualTo(LANGUAGE);

            assertThat(lesson.getDescription())
                    .isEqualTo(DESCRIPTION);

            assertThat(lesson.getContent())
                    .isEqualTo(CONTENT);

            assertThat(lesson.getDisplayOrder())
                    .isEqualTo(DISPLAY_ORDER);

            assertThat(lesson.getDeletedAt())
                    .isNotNull();

            assertThat(lesson.getDeletedBy())
                    .isEqualTo(userId);
        }
    }

    // ============================================================
    // Fixture
    // ============================================================

        private Lesson createLesson() {
        return Lesson.create(
                UNIT_ID,
                TITLE,
                DESCRIPTION,
                CONTENT,
                LANGUAGE,
                DISPLAY_ORDER
        );
    }

    @Test
    @DisplayName("Lesson은(는) DRAFT 상태로 생성된다")
    void create_startsAsDraft() {
        // given & when
        Lesson content = Lesson.create(UUID.randomUUID(), "변수", "설명", "본문", ProgrammingLanguage.JAVA, 1);

        // then
        assertThat(content.getStatus()).isEqualTo(ContentStatus.DRAFT);
        assertThat(content.isPublished()).isFalse();
    }

    @Test
    @DisplayName("Lesson을(를) 공개하면 PUBLISHED, 비공개로 되돌리면 DRAFT가 되고, 같은 전환을 반복해도 상태가 유지된다")
    void publishAndUnpublish_changeStatusIdempotently() {
        // given
        Lesson content = Lesson.create(UUID.randomUUID(), "변수", "설명", "본문", ProgrammingLanguage.JAVA, 1);

        // when & then: 공개
        content.publish();
        content.publish();
        assertThat(content.getStatus()).isEqualTo(ContentStatus.PUBLISHED);
        assertThat(content.isPublished()).isTrue();

        // when & then: 비공개
        content.unpublish();
        content.unpublish();
        assertThat(content.getStatus()).isEqualTo(ContentStatus.DRAFT);
        assertThat(content.isPublished()).isFalse();
    }
}
