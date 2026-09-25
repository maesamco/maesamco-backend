package com.maesamco.content.domain.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Curriculum")
class CurriculumTest {

    @Test
    @DisplayName("create curriculum")
    void create_setsFields() {
        Curriculum curriculum =
                Curriculum.create(
                        "Java Basic",
                        ProgrammingLanguage.JAVA
                );

        assertThat(curriculum.getTitle())
                .isEqualTo("Java Basic");

        assertThat(curriculum.getLanguage())
                .isEqualTo(ProgrammingLanguage.JAVA);

        assertThat(curriculum.getDeletedAt())
                .isNull();

        assertThat(curriculum.getDeletedBy())
                .isNull();
    }

    @Test
    @DisplayName("change title")
    void changeTitle_changesTitle() {
        Curriculum curriculum =
                createCurriculum();

        curriculum.changeTitle(
                "Java Advanced"
        );

        assertThat(curriculum.getTitle())
                .isEqualTo("Java Advanced");

        assertThat(curriculum.getLanguage())
                .isEqualTo(ProgrammingLanguage.JAVA);
    }

    @Test
    @DisplayName("change language")
    void changeLanguage_changesLanguage() {
        Curriculum curriculum =
                createCurriculum();

        curriculum.changeLanguage(
                ProgrammingLanguage.PYTHON
        );

        assertThat(curriculum.getTitle())
                .isEqualTo("Java Basic");

        assertThat(curriculum.getLanguage())
                .isEqualTo(ProgrammingLanguage.PYTHON);
    }

    @Test
    @DisplayName("soft delete curriculum")
    void softDelete_setsDeleteInformation() {
        Curriculum curriculum =
                createCurriculum();

        UUID userId =
                UUID.randomUUID();

        curriculum.softDelete(
                userId
        );

        assertThat(curriculum.getDeletedAt())
                .isNotNull();

        assertThat(curriculum.getDeletedBy())
                .isEqualTo(userId);

        assertThat(curriculum.getTitle())
                .isEqualTo("Java Basic");
    }

    private Curriculum createCurriculum() {
        return Curriculum.create(
                "Java Basic",
                ProgrammingLanguage.JAVA
        );
    }

    @Test
    @DisplayName("Curriculum은(는) DRAFT 상태로 생성된다")
    void create_startsAsDraft() {
        // given & when
        Curriculum content = Curriculum.create("Java Basic", ProgrammingLanguage.JAVA);

        // then
        assertThat(content.getStatus()).isEqualTo(ContentStatus.DRAFT);
        assertThat(content.isPublished()).isFalse();
    }

    @Test
    @DisplayName("Curriculum을(를) 공개하면 PUBLISHED, 비공개로 되돌리면 DRAFT가 되고, 같은 전환을 반복해도 상태가 유지된다")
    void publishAndUnpublish_changeStatusIdempotently() {
        // given
        Curriculum content = Curriculum.create("Java Basic", ProgrammingLanguage.JAVA);

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
