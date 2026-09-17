package com.maesamco.content.domain.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Tag 도메인 테스트")
class TagTest {

    private static final String NAME =
            "반복문";

    private static final TagAttribute ATTRIBUTE =
            TagAttribute.CONCEPT;

    // ============================================================
    // 1. Tag 생성
    // ============================================================

    @Nested
    @DisplayName("Tag 생성")
    class Create {

        @Test
        @DisplayName("Tag 생성 시 전달한 값이 저장된다")
        void create_setsFields() {

            // when
            Tag tag =
                    Tag.create(
                            NAME,
                            ATTRIBUTE
                    );

            // then
            assertThat(tag.getName())
                    .isEqualTo(NAME);

            assertThat(tag.getAttribute())
                    .isEqualTo(ATTRIBUTE);
        }

        @Test
        @DisplayName("새로 생성된 Tag는 삭제 상태가 아니다")
        void create_notDeleted() {

            // when
            Tag tag =
                    createTag();

            // then
            assertThat(tag.getDeletedAt())
                    .isNull();

            assertThat(tag.getDeletedBy())
                    .isNull();
        }
    }

    // ============================================================
    // 2. 이름 변경
    // ============================================================

    @Nested
    @DisplayName("Tag 이름 변경")
    class ChangeName {

        @Test
        @DisplayName("changeName 호출 시 이름이 변경된다")
        void changeName_changesName() {

            // given
            Tag tag =
                    createTag();

            String newName =
                    "조건문";

            // when
            tag.changeName(
                    newName
            );

            // then
            assertThat(tag.getName())
                    .isEqualTo(newName);
        }

        @Test
        @DisplayName("이름을 변경해도 속성은 변경되지 않는다")
        void changeName_keepsAttribute() {

            // given
            Tag tag =
                    createTag();

            // when
            tag.changeName(
                    "배열"
            );

            // then
            assertThat(tag.getName())
                    .isEqualTo("배열");

            assertThat(tag.getAttribute())
                    .isEqualTo(ATTRIBUTE);
        }
    }

    // ============================================================
    // 3. 속성 변경
    // ============================================================

    @Nested
    @DisplayName("Tag 속성 변경")
    class ChangeAttribute {

        @Test
        @DisplayName("changeAttribute 호출 시 속성이 변경된다")
        void changeAttribute_changesAttribute() {

            // given
            Tag tag =
                    createTag();

            // when
            tag.changeAttribute(
                    TagAttribute.ALGORITHM
            );

            // then
            assertThat(tag.getAttribute())
                    .isEqualTo(
                            TagAttribute.ALGORITHM
                    );
        }

        @Test
        @DisplayName("속성을 변경해도 이름은 변경되지 않는다")
        void changeAttribute_keepsName() {

            // given
            Tag tag =
                    createTag();

            // when
            tag.changeAttribute(
                    TagAttribute.DATA_STRUCTURE
            );

            // then
            assertThat(tag.getName())
                    .isEqualTo(NAME);

            assertThat(tag.getAttribute())
                    .isEqualTo(
                            TagAttribute.DATA_STRUCTURE
                    );
        }

        @Test
        @DisplayName("이름과 속성을 각각 변경할 수 있다")
        void changeNameAndAttribute_changesBoth() {

            // given
            Tag tag =
                    createTag();

            // when
            tag.changeName(
                    "스택"
            );

            tag.changeAttribute(
                    TagAttribute.DATA_STRUCTURE
            );

            // then
            assertThat(tag.getName())
                    .isEqualTo("스택");

            assertThat(tag.getAttribute())
                    .isEqualTo(
                            TagAttribute.DATA_STRUCTURE
                    );
        }
    }

    // ============================================================
    // 4. Tag 삭제
    // ============================================================

    @Nested
    @DisplayName("Tag 삭제")
    class Delete {

        @Test
        @DisplayName("softDelete 호출 시 삭제 시간과 삭제자가 기록된다")
        void softDelete_setsDeleteInformation() {

            // given
            Tag tag =
                    createTag();

            UUID userId =
                    UUID.randomUUID();

            // when
            tag.softDelete(
                    userId
            );

            // then
            assertThat(tag.getDeletedAt())
                    .isNotNull();

            assertThat(tag.getDeletedBy())
                    .isEqualTo(userId);
        }

        @Test
        @DisplayName("삭제해도 Tag의 기존 정보는 유지된다")
        void softDelete_keepsTagFields() {

            // given
            Tag tag =
                    createTag();

            UUID userId =
                    UUID.randomUUID();

            // when
            tag.softDelete(
                    userId
            );

            // then
            assertThat(tag.getName())
                    .isEqualTo(NAME);

            assertThat(tag.getAttribute())
                    .isEqualTo(ATTRIBUTE);

            assertThat(tag.getDeletedAt())
                    .isNotNull();

            assertThat(tag.getDeletedBy())
                    .isEqualTo(userId);
        }
    }

    // ============================================================
    // Fixture
    // ============================================================

    private Tag createTag() {
        return Tag.create(
                NAME,
                ATTRIBUTE
        );
    }
}