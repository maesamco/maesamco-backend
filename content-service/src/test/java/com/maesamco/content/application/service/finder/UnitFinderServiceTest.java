package com.maesamco.content.application.service.finder;

import com.maesamco.content.domain.entity.Unit;
import com.maesamco.content.domain.repository.CurriculumRepository;
import com.maesamco.content.domain.repository.UnitRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UnitFinderService 단위 테스트")
class UnitFinderServiceTest {

    @Mock
    private UnitRepository unitRepository;

    @Mock
    private CurriculumRepository curriculumRepository;

    private UnitFinderService unitFinderService;

    @BeforeEach
    void setUp() {
        unitFinderService = new UnitFinderService(
                unitRepository,
                curriculumRepository
        );
    }

    // -------------------------------------------------------------------------
    // 1. findById - 정상 조회
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("findById 성공")
    class FindByIdSuccess {

        @Test
        @DisplayName("유닛과 상위 커리큘럼이 모두 존재하면 해당 유닛을 반환한다")
        void findById_existingUnitAndCurriculum_returnsUnit() {
            // given
            UUID unitId = UUID.randomUUID();
            UUID curriculumId = UUID.randomUUID();

            Unit unit = mock(Unit.class);

            when(unit.getCurriculumId())
                    .thenReturn(curriculumId);

            when(unitRepository.findById(unitId))
                    .thenReturn(Optional.of(unit));

            when(curriculumRepository.findById(curriculumId))
                    .thenReturn(Optional.of(mock(
                            com.maesamco.content.domain.entity.Curriculum.class
                    )));

            // when
            Unit result = unitFinderService.findById(unitId);

            // then
            assertThat(result).isNotNull();
            assertThat(result).isSameAs(unit);

            verify(unitRepository, times(1))
                    .findById(unitId);

            verify(unit, times(1))
                    .getCurriculumId();

            verify(curriculumRepository, times(1))
                    .findById(curriculumId);

            verifyNoMoreInteractions(
                    unitRepository,
                    curriculumRepository,
                    unit
            );
        }

        @Test
        @DisplayName("Repository에서 조회한 Unit 인스턴스를 변환하지 않고 그대로 반환한다")
        void findById_repositoryReturnsUnit_preservesSameInstance() {
            // given
            UUID unitId = UUID.randomUUID();
            UUID curriculumId = UUID.randomUUID();

            Unit unit = mock(Unit.class);

            when(unit.getCurriculumId())
                    .thenReturn(curriculumId);

            when(unitRepository.findById(unitId))
                    .thenReturn(Optional.of(unit));

            when(curriculumRepository.findById(curriculumId))
                    .thenReturn(Optional.of(mock(
                            com.maesamco.content.domain.entity.Curriculum.class
                    )));

            // when
            Unit result = unitFinderService.findById(unitId);

            // then
            assertThat(result)
                    .as("Finder는 UnitRepository가 반환한 Unit 인스턴스를 그대로 반환해야 한다")
                    .isSameAs(unit);

            verify(unitRepository).findById(unitId);
            verify(unit).getCurriculumId();
            verify(curriculumRepository).findById(curriculumId);

            verifyNoMoreInteractions(
                    unitRepository,
                    curriculumRepository,
                    unit
            );
        }

        @Test
        @DisplayName("전달받은 unitId를 변경하지 않고 UnitRepository에 그대로 전달한다")
        void findById_givenUnitId_queriesExactUnitId() {
            // given
            UUID unitId = UUID.randomUUID();
            UUID curriculumId = UUID.randomUUID();

            Unit unit = mock(Unit.class);

            when(unit.getCurriculumId())
                    .thenReturn(curriculumId);

            when(unitRepository.findById(unitId))
                    .thenReturn(Optional.of(unit));

            when(curriculumRepository.findById(curriculumId))
                    .thenReturn(Optional.of(mock(
                            com.maesamco.content.domain.entity.Curriculum.class
                    )));

            // when
            unitFinderService.findById(unitId);

            // then
            verify(unitRepository, times(1))
                    .findById(unitId);

            verify(unit).getCurriculumId();

            verify(curriculumRepository, times(1))
                    .findById(curriculumId);

            verifyNoMoreInteractions(
                    unitRepository,
                    curriculumRepository,
                    unit
            );
        }

        @Test
        @DisplayName("Unit에 저장된 curriculumId를 사용해 상위 커리큘럼을 조회한다")
        void findById_existingUnit_queriesCurriculumUsingUnitsCurriculumId() {
            // given
            UUID unitId = UUID.randomUUID();
            UUID curriculumId = UUID.randomUUID();

            Unit unit = mock(Unit.class);

            when(unit.getCurriculumId())
                    .thenReturn(curriculumId);

            when(unitRepository.findById(unitId))
                    .thenReturn(Optional.of(unit));

            when(curriculumRepository.findById(curriculumId))
                    .thenReturn(Optional.of(mock(
                            com.maesamco.content.domain.entity.Curriculum.class
                    )));

            // when
            unitFinderService.findById(unitId);

            // then
            verify(unit, times(1))
                    .getCurriculumId();

            verify(curriculumRepository, times(1))
                    .findById(curriculumId);

            verify(unitRepository, times(1))
                    .findById(unitId);

            verifyNoMoreInteractions(
                    unitRepository,
                    curriculumRepository,
                    unit
            );
        }

        @Test
        @DisplayName("Unit 조회 후 상위 Curriculum 조회 순서로 계층 검증을 수행한다")
        void findById_existingUnit_checksHierarchyInOrder() {
            // given
            UUID unitId = UUID.randomUUID();
            UUID curriculumId = UUID.randomUUID();

            Unit unit = mock(Unit.class);

            when(unit.getCurriculumId())
                    .thenReturn(curriculumId);

            when(unitRepository.findById(unitId))
                    .thenReturn(Optional.of(unit));

            when(curriculumRepository.findById(curriculumId))
                    .thenReturn(Optional.of(mock(
                            com.maesamco.content.domain.entity.Curriculum.class
                    )));

            // when
            Unit result = unitFinderService.findById(unitId);

            // then
            assertThat(result).isSameAs(unit);

            InOrder inOrder = inOrder(
                    unitRepository,
                    unit,
                    curriculumRepository
            );

            inOrder.verify(unitRepository)
                    .findById(unitId);

            inOrder.verify(unit)
                    .getCurriculumId();

            inOrder.verify(curriculumRepository)
                    .findById(curriculumId);

            inOrder.verifyNoMoreInteractions();
        }

        @Test
        @DisplayName("서로 다른 유닛을 조회하면 각 Unit의 curriculumId로 상위 커리큘럼을 검증한다")
        void findById_differentUnits_queriesTheirOwnCurriculums() {
            // given
            UUID firstUnitId = UUID.randomUUID();
            UUID secondUnitId = UUID.randomUUID();

            UUID firstCurriculumId = UUID.randomUUID();
            UUID secondCurriculumId = UUID.randomUUID();

            Unit firstUnit = mock(Unit.class);
            Unit secondUnit = mock(Unit.class);

            when(firstUnit.getCurriculumId())
                    .thenReturn(firstCurriculumId);

            when(secondUnit.getCurriculumId())
                    .thenReturn(secondCurriculumId);

            when(unitRepository.findById(firstUnitId))
                    .thenReturn(Optional.of(firstUnit));

            when(unitRepository.findById(secondUnitId))
                    .thenReturn(Optional.of(secondUnit));

            when(curriculumRepository.findById(firstCurriculumId))
                    .thenReturn(Optional.of(mock(
                            com.maesamco.content.domain.entity.Curriculum.class
                    )));

            when(curriculumRepository.findById(secondCurriculumId))
                    .thenReturn(Optional.of(mock(
                            com.maesamco.content.domain.entity.Curriculum.class
                    )));

            // when
            Unit firstResult =
                    unitFinderService.findById(firstUnitId);

            Unit secondResult =
                    unitFinderService.findById(secondUnitId);

            // then
            assertThat(firstResult).isSameAs(firstUnit);
            assertThat(secondResult).isSameAs(secondUnit);

            verify(unitRepository, times(1))
                    .findById(firstUnitId);

            verify(unitRepository, times(1))
                    .findById(secondUnitId);

            verify(firstUnit, times(1))
                    .getCurriculumId();

            verify(secondUnit, times(1))
                    .getCurriculumId();

            verify(curriculumRepository, times(1))
                    .findById(firstCurriculumId);

            verify(curriculumRepository, times(1))
                    .findById(secondCurriculumId);

            verifyNoMoreInteractions(
                    unitRepository,
                    curriculumRepository,
                    firstUnit,
                    secondUnit
            );
        }
    }

    // -------------------------------------------------------------------------
    // 2. findById - Unit 없음
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("findById 실패 - Unit 없음")
    class FindByIdUnitNotFound {

        @Test
        @DisplayName("존재하지 않는 unitId를 조회하면 BusinessException을 던진다")
        void findById_nonExistingUnit_throwsBusinessException() {
            // given
            UUID unitId = UUID.randomUUID();

            when(unitRepository.findById(unitId))
                    .thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> unitFinderService.findById(unitId))
                    .isInstanceOf(BusinessException.class);

            verify(unitRepository, times(1))
                    .findById(unitId);

            verifyNoInteractions(curriculumRepository);
            verifyNoMoreInteractions(unitRepository);
        }

        @Test
        @DisplayName("존재하지 않는 유닛 조회 시 UNIT_NOT_FOUND ErrorCode를 가진다")
        void findById_nonExistingUnit_hasUnitNotFoundErrorCode() {
            // given
            UUID unitId = UUID.randomUUID();

            when(unitRepository.findById(unitId))
                    .thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> unitFinderService.findById(unitId))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(exception -> {
                        BusinessException businessException =
                                (BusinessException) exception;

                        assertThat(businessException.getErrorCode())
                                .isEqualTo(ErrorCode.UNIT_NOT_FOUND);
                    });

            verify(unitRepository, times(1))
                    .findById(unitId);

            verifyNoInteractions(curriculumRepository);
            verifyNoMoreInteractions(unitRepository);
        }

        @Test
        @DisplayName("UNIT_NOT_FOUND 예외는 ErrorCode에 정의된 메시지를 사용한다")
        void findById_nonExistingUnit_hasCorrectErrorMessage() {
            // given
            UUID unitId = UUID.randomUUID();

            when(unitRepository.findById(unitId))
                    .thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> unitFinderService.findById(unitId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(ErrorCode.UNIT_NOT_FOUND.getMessage());

            verify(unitRepository, times(1))
                    .findById(unitId);

            verifyNoInteractions(curriculumRepository);
            verifyNoMoreInteractions(unitRepository);
        }

        @Test
        @DisplayName("Unit이 존재하지 않으면 CurriculumRepository는 조회하지 않는다")
        void findById_nonExistingUnit_doesNotQueryCurriculumRepository() {
            // given
            UUID unitId = UUID.randomUUID();

            when(unitRepository.findById(unitId))
                    .thenReturn(Optional.empty());

            // when
            try {
                unitFinderService.findById(unitId);
            } catch (BusinessException ignored) {
            }

            // then
            verify(unitRepository, times(1))
                    .findById(unitId);

            verifyNoInteractions(curriculumRepository);
            verifyNoMoreInteractions(unitRepository);
        }

        @Test
        @DisplayName("Unit이 존재하지 않으면 UnitRepository 조회는 정확히 한 번만 수행한다")
        void findById_nonExistingUnit_queriesUnitRepositoryOnlyOnce() {
            // given
            UUID unitId = UUID.randomUUID();

            when(unitRepository.findById(unitId))
                    .thenReturn(Optional.empty());

            // when
            try {
                unitFinderService.findById(unitId);
            } catch (BusinessException ignored) {
            }

            // then
            verify(unitRepository, times(1))
                    .findById(unitId);

            verifyNoInteractions(curriculumRepository);
            verifyNoMoreInteractions(unitRepository);
        }
    }

    // -------------------------------------------------------------------------
    // 3. findById - 상위 Curriculum 없음
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("findById 실패 - 상위 Curriculum 없음")
    class FindByIdCurriculumNotFound {

        @Test
        @DisplayName("Unit은 존재하지만 상위 Curriculum이 없으면 BusinessException을 던진다")
        void findById_curriculumDoesNotExist_throwsBusinessException() {
            // given
            UUID unitId = UUID.randomUUID();
            UUID curriculumId = UUID.randomUUID();

            Unit unit = mock(Unit.class);

            when(unit.getCurriculumId())
                    .thenReturn(curriculumId);

            when(unitRepository.findById(unitId))
                    .thenReturn(Optional.of(unit));

            when(curriculumRepository.findById(curriculumId))
                    .thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> unitFinderService.findById(unitId))
                    .isInstanceOf(BusinessException.class);

            verify(unitRepository, times(1))
                    .findById(unitId);

            verify(unit, times(1))
                    .getCurriculumId();

            verify(curriculumRepository, times(1))
                    .findById(curriculumId);

            verifyNoMoreInteractions(
                    unitRepository,
                    curriculumRepository,
                    unit
            );
        }

        @Test
        @DisplayName("상위 Curriculum이 없으면 CURRICULUM_NOT_FOUND ErrorCode를 가진다")
        void findById_curriculumDoesNotExist_hasCurriculumNotFoundErrorCode() {
            // given
            UUID unitId = UUID.randomUUID();
            UUID curriculumId = UUID.randomUUID();

            Unit unit = mock(Unit.class);

            when(unit.getCurriculumId())
                    .thenReturn(curriculumId);

            when(unitRepository.findById(unitId))
                    .thenReturn(Optional.of(unit));

            when(curriculumRepository.findById(curriculumId))
                    .thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> unitFinderService.findById(unitId))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(exception -> {
                        BusinessException businessException =
                                (BusinessException) exception;

                        assertThat(businessException.getErrorCode())
                                .isEqualTo(ErrorCode.CURRICULUM_NOT_FOUND);
                    });

            verify(unitRepository, times(1))
                    .findById(unitId);

            verify(unit, times(1))
                    .getCurriculumId();

            verify(curriculumRepository, times(1))
                    .findById(curriculumId);

            verifyNoMoreInteractions(
                    unitRepository,
                    curriculumRepository,
                    unit
            );
        }

        @Test
        @DisplayName("CURRICULUM_NOT_FOUND 예외는 ErrorCode에 정의된 메시지를 사용한다")
        void findById_curriculumDoesNotExist_hasCorrectErrorMessage() {
            // given
            UUID unitId = UUID.randomUUID();
            UUID curriculumId = UUID.randomUUID();

            Unit unit = mock(Unit.class);

            when(unit.getCurriculumId())
                    .thenReturn(curriculumId);

            when(unitRepository.findById(unitId))
                    .thenReturn(Optional.of(unit));

            when(curriculumRepository.findById(curriculumId))
                    .thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> unitFinderService.findById(unitId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(
                            ErrorCode.CURRICULUM_NOT_FOUND.getMessage()
                    );

            verify(unitRepository, times(1))
                    .findById(unitId);

            verify(unit, times(1))
                    .getCurriculumId();

            verify(curriculumRepository, times(1))
                    .findById(curriculumId);

            verifyNoMoreInteractions(
                    unitRepository,
                    curriculumRepository,
                    unit
            );
        }

        @Test
        @DisplayName("상위 Curriculum 조회에는 Unit이 가진 curriculumId를 정확히 사용한다")
        void findById_curriculumDoesNotExist_queriesExactCurriculumId() {
            // given
            UUID unitId = UUID.randomUUID();

            UUID curriculumId = UUID.randomUUID();
            UUID anotherCurriculumId = UUID.randomUUID();

            Unit unit = mock(Unit.class);

            when(unit.getCurriculumId())
                    .thenReturn(curriculumId);

            when(unitRepository.findById(unitId))
                    .thenReturn(Optional.of(unit));

            when(curriculumRepository.findById(curriculumId))
                    .thenReturn(Optional.empty());

            // when
            try {
                unitFinderService.findById(unitId);
            } catch (BusinessException ignored) {
            }

            // then
            verify(unitRepository, times(1))
                    .findById(unitId);

            verify(unit, times(1))
                    .getCurriculumId();

            verify(curriculumRepository, times(1))
                    .findById(curriculumId);

            verify(curriculumRepository, never())
                    .findById(anotherCurriculumId);

            verifyNoMoreInteractions(
                    unitRepository,
                    curriculumRepository,
                    unit
            );
        }

        @Test
        @DisplayName("상위 Curriculum이 존재하지 않아도 각 Repository 조회는 한 번씩만 수행한다")
        void findById_curriculumDoesNotExist_queriesRepositoriesOnlyOnce() {
            // given
            UUID unitId = UUID.randomUUID();
            UUID curriculumId = UUID.randomUUID();

            Unit unit = mock(Unit.class);

            when(unit.getCurriculumId())
                    .thenReturn(curriculumId);

            when(unitRepository.findById(unitId))
                    .thenReturn(Optional.of(unit));

            when(curriculumRepository.findById(curriculumId))
                    .thenReturn(Optional.empty());

            // when
            try {
                unitFinderService.findById(unitId);
            } catch (BusinessException ignored) {
            }

            // then
            verify(unitRepository, times(1))
                    .findById(unitId);

            verify(unit, times(1))
                    .getCurriculumId();

            verify(curriculumRepository, times(1))
                    .findById(curriculumId);

            verifyNoMoreInteractions(
                    unitRepository,
                    curriculumRepository,
                    unit
            );
        }
    }

    // -------------------------------------------------------------------------
    // 4. 계층 검증 흐름
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("계층 검증")
    class HierarchyValidation {

        @Test
        @DisplayName("Unit이 존재해야만 상위 Curriculum 검증을 진행한다")
        void findById_unitMustExistBeforeCurriculumValidation() {
            // given
            UUID unitId = UUID.randomUUID();

            when(unitRepository.findById(unitId))
                    .thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> unitFinderService.findById(unitId))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(exception -> {
                        BusinessException businessException =
                                (BusinessException) exception;

                        assertThat(businessException.getErrorCode())
                                .isEqualTo(ErrorCode.UNIT_NOT_FOUND);
                    });

            verify(unitRepository, times(1))
                    .findById(unitId);

            verifyNoInteractions(curriculumRepository);
            verifyNoMoreInteractions(unitRepository);
        }

        @Test
        @DisplayName("Unit과 상위 Curriculum이 모두 존재할 때만 Unit을 반환한다")
        void findById_returnsUnitOnlyWhenEntireHierarchyIsValid() {
            // given
            UUID unitId = UUID.randomUUID();
            UUID curriculumId = UUID.randomUUID();

            Unit unit = mock(Unit.class);

            when(unit.getCurriculumId())
                    .thenReturn(curriculumId);

            when(unitRepository.findById(unitId))
                    .thenReturn(Optional.of(unit));

            when(curriculumRepository.findById(curriculumId))
                    .thenReturn(Optional.of(mock(
                            com.maesamco.content.domain.entity.Curriculum.class
                    )));

            // when
            Unit result = unitFinderService.findById(unitId);

            // then
            assertThat(result).isSameAs(unit);

            InOrder inOrder = inOrder(
                    unitRepository,
                    unit,
                    curriculumRepository
            );

            inOrder.verify(unitRepository)
                    .findById(unitId);

            inOrder.verify(unit)
                    .getCurriculumId();

            inOrder.verify(curriculumRepository)
                    .findById(curriculumId);

            inOrder.verifyNoMoreInteractions();
        }
    }
}