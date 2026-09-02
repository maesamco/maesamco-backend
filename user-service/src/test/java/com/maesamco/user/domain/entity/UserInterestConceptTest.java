package com.maesamco.user.domain.entity;

import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UserInterestConcept 도메인의 생성 규칙과
 * 상태 변경을 검증하는 단위 테스트입니다.
 */
class UserInterestConceptTest {

    /**
     * 사용자 ID와 개념 ID를 이용해 관심 개념을 생성할 수 있는지 검증합니다.
     */
    @Test
    @DisplayName("사용자 ID와 개념 ID로 관심 개념을 생성한다")
    void createUserInterestConcept() {
        // given
        UUID userId = UUID.randomUUID();
        UUID conceptId = UUID.randomUUID();

        // when
        UserInterestConcept interestConcept =
                UserInterestConcept.create(
                        userId,
                        conceptId
                );

        // then
        assertThat(interestConcept.getId()).isNotNull();
        assertThat(interestConcept.getUserId()).isEqualTo(userId);
        assertThat(interestConcept.getConceptId()).isEqualTo(conceptId);
        assertThat(interestConcept.isDeleted()).isFalse();
    }

    /**
     * 사용자 ID 없이 관심 개념을 생성할 수 없는지 검증합니다.
     */
    @Test
    @DisplayName("사용자 ID가 null이면 관심 개념을 생성할 수 없다")
    void rejectNullUserId() {
        // given
        UUID conceptId = UUID.randomUUID();

        // when & then
        assertThatThrownBy(() ->
                UserInterestConcept.create(
                        null,
                        conceptId
                )
        )
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> {
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(
                                            ErrorCode.INVALID_INPUT_VALUE
                                    );
                            assertThat(exception.getMessage())
                                    .isEqualTo(
                                            "사용자 ID는 필수입니다."
                                    );
                        }
                );
    }

    /**
     * 개념 ID 없이 관심 개념을 생성할 수 없는지 검증합니다.
     */
    @Test
    @DisplayName("개념 ID가 null이면 관심 개념을 생성할 수 없다")
    void rejectNullConceptId() {
        // given
        UUID userId = UUID.randomUUID();

        // when & then
        assertThatThrownBy(() ->
                UserInterestConcept.create(
                        userId,
                        null
                )
        )
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> {
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(
                                            ErrorCode.INVALID_INPUT_VALUE
                                    );
                            assertThat(exception.getMessage())
                                    .isEqualTo(
                                            "개념 ID는 필수입니다."
                                    );
                        }
                );
    }

    /**
     * 관심 개념을 논리 삭제할 수 있는지 검증합니다.
     */
    @Test
    @DisplayName("관심 개념을 논리 삭제한다")
    void softDeleteUserInterestConcept() {
        // given
        UserInterestConcept interestConcept =
                UserInterestConcept.create(
                        UUID.randomUUID(),
                        UUID.randomUUID()
                );

        UUID deletedBy = UUID.randomUUID();

        // when
        interestConcept.softDelete(deletedBy);

        // then
        assertThat(interestConcept.isDeleted()).isTrue();
        assertThat(interestConcept.getDeletedAt()).isNotNull();
        assertThat(interestConcept.getDeletedBy())
                .isEqualTo(deletedBy);
    }
}