package com.maesamco.user.application.service;

import com.maesamco.user.domain.entity.UserInterestConcept;
import com.maesamco.user.domain.repository.UserInterestConceptRepository;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserInterestConceptService의 관심 개념 등록·조회와
 * 중복 방지 규칙을 검증하는 단위 테스트입니다.
 */
@ExtendWith(MockitoExtension.class)
class UserInterestConceptServiceTest {

    /**
     * 테스트에서 사용할 도메인 Repository Mock입니다.
     */
    @Mock
    private UserInterestConceptRepository
            interestConceptRepository;

    /**
     * Repository Mock을 주입받는 테스트 대상 서비스입니다.
     */
    @InjectMocks
    private UserInterestConceptService
            interestConceptService;

    /**
     * 중복되지 않은 관심 개념이 정상적으로
     * 생성되고 저장되는지 검증합니다.
     */
    @Test
    @DisplayName("중복되지 않은 사용자 관심 개념을 등록한다")
    void registerInterestConcept() {
        // given
        UUID userId = UUID.randomUUID();
        UUID conceptId = UUID.randomUUID();

        when(
                interestConceptRepository
                        .existsByUserIdAndConceptId(
                                userId,
                                conceptId
                        )
        ).thenReturn(false);

        when(
                interestConceptRepository.save(
                        any(UserInterestConcept.class)
                )
        ).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        // when
        UserInterestConcept registeredInterestConcept =
                interestConceptService.register(
                        userId,
                        conceptId
                );

        // then
        assertThat(registeredInterestConcept.getId())
                .isNotNull();
        assertThat(registeredInterestConcept.getUserId())
                .isEqualTo(userId);
        assertThat(registeredInterestConcept.getConceptId())
                .isEqualTo(conceptId);

        verify(interestConceptRepository)
                .existsByUserIdAndConceptId(
                        userId,
                        conceptId
                );

        verify(interestConceptRepository)
                .save(any(UserInterestConcept.class));
    }

    /**
     * 활성 상태의 동일한 관심 개념이 이미 존재하면
     * 중복 등록을 차단하는지 검증합니다.
     */
    @Test
    @DisplayName("동일한 사용자와 개념의 중복 등록을 차단한다")
    void rejectDuplicatedInterestConcept() {
        // given
        UUID userId = UUID.randomUUID();
        UUID conceptId = UUID.randomUUID();

        when(
                interestConceptRepository
                        .existsByUserIdAndConceptId(
                                userId,
                                conceptId
                        )
        ).thenReturn(true);

        // when & then
        assertThatThrownBy(() ->
                interestConceptService.register(
                        userId,
                        conceptId
                )
        )
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> {
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(
                                            ErrorCode
                                                    .USER_INTEREST_CONCEPT_ALREADY_EXISTS
                                    );

                            assertThat(exception.getMessage())
                                    .isEqualTo(
                                            "이미 등록된 관심 개념입니다."
                                    );
                        }
                );

        verify(interestConceptRepository)
                .existsByUserIdAndConceptId(
                        userId,
                        conceptId
                );

        verify(
                interestConceptRepository,
                never()
        ).save(any(UserInterestConcept.class));
    }

    /**
     * 사용자별 활성 관심 개념 목록을
     * Repository에서 조회하는지 검증합니다.
     */
    @Test
    @DisplayName("사용자별 활성 관심 개념 목록을 조회한다")
    void getAllInterestConceptsByUserId() {
        // given
        UUID userId = UUID.randomUUID();

        List<UserInterestConcept> expectedInterestConcepts =
                List.of(
                        UserInterestConcept.create(
                                userId,
                                UUID.randomUUID()
                        ),
                        UserInterestConcept.create(
                                userId,
                                UUID.randomUUID()
                        )
                );

        when(
                interestConceptRepository.findAllByUserId(
                        userId
                )
        ).thenReturn(expectedInterestConcepts);

        // when
        List<UserInterestConcept> foundInterestConcepts =
                interestConceptService.getAllByUserId(
                        userId
                );

        // then
        assertThat(foundInterestConcepts)
                .containsExactlyElementsOf(
                        expectedInterestConcepts
                );

        verify(interestConceptRepository)
                .findAllByUserId(userId);
    }
}