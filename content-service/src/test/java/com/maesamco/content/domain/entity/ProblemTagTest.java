package com.maesamco.content.domain.entity;

import com.maesamco.content.domain.entity.problem.ProblemTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemTagTest {

    @Test
    @DisplayName("문제 ID와 태그 ID로 ProblemTag를 생성한다")
    void create_createsProblemTag() {
        // given
        UUID problemId = UUID.randomUUID();
        UUID tagId = UUID.randomUUID();

        // when
        ProblemTag problemTag =
                ProblemTag.create(
                        problemId,
                        tagId
                );

        // then
        assertThat(problemTag.getProblemId())
                .isEqualTo(problemId);

        assertThat(problemTag.getTagId())
                .isEqualTo(tagId);
    }

    @Test
    @DisplayName("ProblemTag 생성 시 ID는 JPA 저장 전까지 null이다")
    void create_idIsNullBeforePersistence() {
        // given
        UUID problemId = UUID.randomUUID();
        UUID tagId = UUID.randomUUID();

        // when
        ProblemTag problemTag =
                ProblemTag.create(
                        problemId,
                        tagId
                );

        // then
        assertThat(problemTag.getId())
                .isNull();
    }
}