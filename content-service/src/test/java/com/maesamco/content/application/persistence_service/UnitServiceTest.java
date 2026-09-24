package com.maesamco.content.application.persistence_service;

import com.maesamco.content.application.finder.CurriculumFinder;
import com.maesamco.content.application.finder.UnitFinder;
import com.maesamco.content.domain.entity.Curriculum;
import com.maesamco.content.domain.entity.ProgrammingLanguage;
import com.maesamco.content.domain.entity.Unit;
import com.maesamco.content.domain.repository.UnitRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import com.maesamco.content.global.response.PageResponse;
import com.maesamco.content.presentation.request.UnitCreateRequest;
import com.maesamco.content.presentation.request.UnitUpdateRequest;
import com.maesamco.content.presentation.response.UnitCreateResponse;
import com.maesamco.content.presentation.response.UnitResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UnitService 단위 테스트")
class UnitServiceTest {

    @Mock
    private UnitRepository unitRepository;

    @Mock
    private UnitFinder unitFinder;

    @Mock
    private CurriculumFinder curriculumFinder;

    private UnitService unitService;

    @BeforeEach
    void setUp() {
        unitService = new UnitService(unitRepository, unitFinder, curriculumFinder);
    }

    @Nested
    @DisplayName("createUnit 성공")
    class CreateUnitSuccess {

        @Test
        @DisplayName("상위 Curriculum이 존재하면 현재 Unit 수를 기준으로 displayOrder를 계산하고 Unit을 저장한다")
        void createUnit_validRequest_savesUnit() {
            // given
            UUID curriculumId = UUID.randomUUID();
            UnitCreateRequest request = createRequest(
                    curriculumId,
                    "조건문",
                    ProgrammingLanguage.JAVA
            );

            when(curriculumFinder.lockById(curriculumId)).thenReturn(mock(Curriculum.class));
            when(unitRepository.findMaxDisplayOrderByCurriculumId(curriculumId)).thenReturn(2);
            when(unitRepository.save(any(Unit.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            UnitCreateResponse result = unitService.createUnit(request);

            // then
            assertThat(result).isNotNull();

            ArgumentCaptor<Unit> captor = ArgumentCaptor.forClass(Unit.class);
            verify(unitRepository).save(captor.capture());

            Unit savedUnit = captor.getValue();

            assertThat(savedUnit.getCurriculumId()).isEqualTo(curriculumId);
            assertThat(savedUnit.getTitle()).isEqualTo("조건문");
            assertThat(savedUnit.getLanguage()).isEqualTo(ProgrammingLanguage.JAVA);
            assertThat(savedUnit.getDisplayOrder()).isEqualTo(3);

            verify(curriculumFinder).lockById(curriculumId);
            verify(unitRepository).findMaxDisplayOrderByCurriculumId(curriculumId);
            verifyNoInteractions(unitFinder);
            verifyNoMoreInteractions(curriculumFinder, unitRepository);
        }

        @Test
        @DisplayName("Unit 생성은 Curriculum 검증 -> 기존 Unit 수 조회 -> 저장 순서로 수행한다")
        void createUnit_performsOperationsInOrder() {
            // given
            UUID curriculumId = UUID.randomUUID();
            UnitCreateRequest request = createRequest(
                    curriculumId,
                    "반복문",
                    ProgrammingLanguage.JAVA
            );

            when(curriculumFinder.lockById(curriculumId)).thenReturn(mock(Curriculum.class));
            when(unitRepository.findMaxDisplayOrderByCurriculumId(curriculumId)).thenReturn(0);
            when(unitRepository.save(any(Unit.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            unitService.createUnit(request);

            // then
            InOrder inOrder = inOrder(curriculumFinder, unitRepository);

            inOrder.verify(curriculumFinder).lockById(curriculumId);
            inOrder.verify(unitRepository).findMaxDisplayOrderByCurriculumId(curriculumId);
            inOrder.verify(unitRepository).save(any(Unit.class));
            inOrder.verifyNoMoreInteractions();

            verifyNoInteractions(unitFinder);
        }

        @Test
        @DisplayName("기존 Unit 개수에 1을 더한 값을 displayOrder로 사용한다")
        void createUnit_calculatesDisplayOrderFromMax() {
            // given
            UUID curriculumId = UUID.randomUUID();
            UnitCreateRequest request = createRequest(
                    curriculumId,
                    "배열",
                    ProgrammingLanguage.JAVA
            );

            when(curriculumFinder.lockById(curriculumId)).thenReturn(mock(Curriculum.class));
            when(unitRepository.findMaxDisplayOrderByCurriculumId(curriculumId)).thenReturn(7);
            when(unitRepository.save(any(Unit.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ArgumentCaptor<Unit> captor = ArgumentCaptor.forClass(Unit.class);

            // when
            unitService.createUnit(request);

            // then
            verify(unitRepository).save(captor.capture());

            assertThat(captor.getValue().getDisplayOrder()).isEqualTo(8);

            verify(curriculumFinder).lockById(curriculumId);
            verify(unitRepository).findMaxDisplayOrderByCurriculumId(curriculumId);
            verifyNoInteractions(unitFinder);
            verifyNoMoreInteractions(curriculumFinder, unitRepository);
        }
    }

    @Nested
    @DisplayName("createUnit 실패")
    class CreateUnitFailure {

        @Test
        @DisplayName("상위 Curriculum이 존재하지 않으면 예외를 그대로 전파하고 Unit을 생성하지 않는다")
        void createUnit_curriculumNotFound_doesNotCreateUnit() {
            // given
            UUID curriculumId = UUID.randomUUID();
            UnitCreateRequest request = mock(UnitCreateRequest.class);
            BusinessException exception = new BusinessException(ErrorCode.CURRICULUM_NOT_FOUND);

            when(request.getCurriculumId()).thenReturn(curriculumId);
            when(curriculumFinder.lockById(curriculumId)).thenThrow(exception);

            // when & then
            assertThatThrownBy(() -> unitService.createUnit(request))
                    .isSameAs(exception)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.CURRICULUM_NOT_FOUND));

            verify(request).getCurriculumId();
            verify(curriculumFinder).lockById(curriculumId);
            verifyNoInteractions(unitRepository, unitFinder);
            verifyNoMoreInteractions(request, curriculumFinder);
        }

        @Test
        @DisplayName("Unit 개수 조회 중 예외가 발생하면 저장하지 않고 그대로 전파한다")
        void createUnit_findMaxThrowsException_doesNotSave() {
            // given
            UUID curriculumId = UUID.randomUUID();
            UnitCreateRequest request = mock(UnitCreateRequest.class);
            RuntimeException exception = new RuntimeException("max order failure");

            when(request.getCurriculumId()).thenReturn(curriculumId);
            when(curriculumFinder.lockById(curriculumId)).thenReturn(mock(Curriculum.class));
            when(unitRepository.findMaxDisplayOrderByCurriculumId(curriculumId)).thenThrow(exception);

            // when & then
            assertThatThrownBy(() -> unitService.createUnit(request)).isSameAs(exception);

            verify(request, times(2)).getCurriculumId();
            verify(curriculumFinder).lockById(curriculumId);
            verify(unitRepository).findMaxDisplayOrderByCurriculumId(curriculumId);
            verify(unitRepository, never()).save(any(Unit.class));
            verifyNoInteractions(unitFinder);
            verifyNoMoreInteractions(request, curriculumFinder, unitRepository);
        }

        @Test
        @DisplayName("Unit 저장 중 예외가 발생하면 그대로 전파한다")
        void createUnit_saveThrowsException_propagatesException() {
            // given
            UUID curriculumId = UUID.randomUUID();
            UnitCreateRequest request = createRequest(
                    curriculumId,
                    "조건문",
                    ProgrammingLanguage.JAVA
            );

            RuntimeException exception = new RuntimeException("save failure");

            when(curriculumFinder.lockById(curriculumId)).thenReturn(mock(Curriculum.class));
            when(unitRepository.findMaxDisplayOrderByCurriculumId(curriculumId)).thenReturn(0);
            when(unitRepository.save(any(Unit.class))).thenThrow(exception);

            // when & then
            assertThatThrownBy(() -> unitService.createUnit(request)).isSameAs(exception);

            verify(curriculumFinder).lockById(curriculumId);
            verify(unitRepository).findMaxDisplayOrderByCurriculumId(curriculumId);
            verify(unitRepository).save(any(Unit.class));
            verifyNoInteractions(unitFinder);
            verifyNoMoreInteractions(curriculumFinder, unitRepository);
        }

        @Test
        @DisplayName("displayOrder가 int 범위를 초과하면 ArithmeticException이 발생하고 저장하지 않는다")
        void createUnit_displayOrderOverflowsInt_doesNotSave() {
            // given
            UUID curriculumId = UUID.randomUUID();
            UnitCreateRequest request = mock(UnitCreateRequest.class);

            when(request.getCurriculumId()).thenReturn(curriculumId);
            when(curriculumFinder.lockById(curriculumId)).thenReturn(mock(Curriculum.class));
            when(unitRepository.findMaxDisplayOrderByCurriculumId(curriculumId)).thenReturn(Integer.MAX_VALUE);

            // when & then
            assertThatThrownBy(() -> unitService.createUnit(request))
                    .isInstanceOf(ArithmeticException.class);

            verify(request, times(2)).getCurriculumId();
            verify(curriculumFinder).lockById(curriculumId);
            verify(unitRepository).findMaxDisplayOrderByCurriculumId(curriculumId);
            verify(unitRepository, never()).save(any(Unit.class));
            verifyNoInteractions(unitFinder);
            verifyNoMoreInteractions(request, curriculumFinder, unitRepository);
        }
    }

    @Nested
    @DisplayName("getUnit")
    class GetUnit {

        @Test
        @DisplayName("존재하는 Unit을 조회하면 UnitResponse를 반환한다")
        void getUnit_existingUnit_returnsResponse() {
            // given
            UUID unitId = UUID.randomUUID();
            Unit unit = createUnitEntity(UUID.randomUUID());

            when(unitFinder.getById(unitId)).thenReturn(unit);

            // when
            UnitResponse result = unitService.getUnit(unitId);

            // then
            assertThat(result).isNotNull();

            verify(unitFinder).getById(unitId);
            verifyNoInteractions(unitRepository, curriculumFinder);
            verifyNoMoreInteractions(unitFinder);
        }

        @Test
        @DisplayName("UnitFinder에서 BusinessException이 발생하면 그대로 전파한다")
        void getUnit_finderThrowsBusinessException_propagatesException() {
            // given
            UUID unitId = UUID.randomUUID();
            BusinessException exception = new BusinessException(ErrorCode.UNIT_NOT_FOUND);

            when(unitFinder.getById(unitId)).thenThrow(exception);

            // when & then
            assertThatThrownBy(() -> unitService.getUnit(unitId)).isSameAs(exception);

            verify(unitFinder).getById(unitId);
            verifyNoInteractions(unitRepository, curriculumFinder);
            verifyNoMoreInteractions(unitFinder);
        }

        @Test
        @DisplayName("UnitFinder에서 예상하지 못한 예외가 발생하면 그대로 전파한다")
        void getUnit_finderThrowsUnexpectedException_propagatesException() {
            // given
            UUID unitId = UUID.randomUUID();
            RuntimeException exception = new RuntimeException("unit finder failure");

            when(unitFinder.getById(unitId)).thenThrow(exception);

            // when & then
            assertThatThrownBy(() -> unitService.getUnit(unitId)).isSameAs(exception);

            verify(unitFinder).getById(unitId);
            verifyNoInteractions(unitRepository, curriculumFinder);
            verifyNoMoreInteractions(unitFinder);
        }
    }

    @Nested
    @DisplayName("searchUnits 성공")
    class SearchUnitsSuccess {

        @Test
        @DisplayName("Curriculum 존재를 확인한 후 동일한 curriculumId와 Pageable로 Unit 목록을 조회한다")
        void searchUnits_validCurriculum_returnsPagedUnits() {
            // given
            UUID curriculumId = UUID.randomUUID();
            Pageable pageable = PageRequest.of(1, 2);

            Unit firstUnit = Unit.create(
                    curriculumId,
                    "조건문",
                    ProgrammingLanguage.JAVA,
                    1
            );

            Unit secondUnit = Unit.create(
                    curriculumId,
                    "반복문",
                    ProgrammingLanguage.JAVA,
                    2
            );

            Page<Unit> page = new PageImpl<>(
                    List.of(firstUnit, secondUnit),
                    pageable,
                    5
            );

            when(curriculumFinder.getById(curriculumId)).thenReturn(mock(Curriculum.class));
            when(unitRepository.searchUnits(curriculumId, pageable)).thenReturn(page);

            // when
            PageResponse<UnitResponse> result = unitService.searchUnits(curriculumId, pageable);

            // then
            assertThat(result).isNotNull();
            assertThat(result.content()).hasSize(2);
            assertThat(result.page()).isEqualTo(1);
            assertThat(result.size()).isEqualTo(2);
            assertThat(result.totalElements()).isEqualTo(5);
            assertThat(result.totalPages()).isEqualTo(3);
            assertThat(result.hasNext()).isTrue();

            verify(curriculumFinder).getById(curriculumId);
            verify(unitRepository).searchUnits(curriculumId, pageable);
            verifyNoInteractions(unitFinder);
            verifyNoMoreInteractions(curriculumFinder, unitRepository);
        }

        @Test
        @DisplayName("조회 결과가 비어 있으면 빈 PageResponse를 반환한다")
        void searchUnits_emptyPage_returnsEmptyResponse() {
            // given
            UUID curriculumId = UUID.randomUUID();
            Pageable pageable = PageRequest.of(0, 20);
            Page<Unit> page = new PageImpl<>(List.of(), pageable, 0);

            when(curriculumFinder.getById(curriculumId)).thenReturn(mock(Curriculum.class));
            when(unitRepository.searchUnits(curriculumId, pageable)).thenReturn(page);

            // when
            PageResponse<UnitResponse> result = unitService.searchUnits(curriculumId, pageable);

            // then
            assertThat(result.content()).isEmpty();
            assertThat(result.page()).isZero();
            assertThat(result.size()).isEqualTo(20);
            assertThat(result.totalElements()).isZero();
            assertThat(result.totalPages()).isZero();
            assertThat(result.hasNext()).isFalse();

            verify(curriculumFinder).getById(curriculumId);
            verify(unitRepository).searchUnits(curriculumId, pageable);
            verifyNoInteractions(unitFinder);
            verifyNoMoreInteractions(curriculumFinder, unitRepository);
        }

        @Test
        @DisplayName("Unit 목록 조회는 Curriculum 검증 후 Repository 조회 순서로 수행한다")
        void searchUnits_checksCurriculumBeforeSearching() {
            // given
            UUID curriculumId = UUID.randomUUID();
            Pageable pageable = PageRequest.of(0, 20);

            when(curriculumFinder.getById(curriculumId)).thenReturn(mock(Curriculum.class));
            when(unitRepository.searchUnits(curriculumId, pageable))
                    .thenReturn(new PageImpl<>(List.of(), pageable, 0));

            // when
            unitService.searchUnits(curriculumId, pageable);

            // then
            InOrder inOrder = inOrder(curriculumFinder, unitRepository);

            inOrder.verify(curriculumFinder).getById(curriculumId);
            inOrder.verify(unitRepository).searchUnits(curriculumId, pageable);
            inOrder.verifyNoMoreInteractions();

            verifyNoInteractions(unitFinder);
        }
    }

    @Nested
    @DisplayName("searchUnits 실패")
    class SearchUnitsFailure {

        @Test
        @DisplayName("Curriculum이 존재하지 않으면 UnitRepository를 조회하지 않는다")
        void searchUnits_curriculumNotFound_doesNotSearchUnits() {
            // given
            UUID curriculumId = UUID.randomUUID();
            Pageable pageable = PageRequest.of(0, 20);
            BusinessException exception = new BusinessException(ErrorCode.CURRICULUM_NOT_FOUND);

            when(curriculumFinder.getById(curriculumId)).thenThrow(exception);

            // when & then
            assertThatThrownBy(() -> unitService.searchUnits(curriculumId, pageable))
                    .isSameAs(exception)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.CURRICULUM_NOT_FOUND));

            verify(curriculumFinder).getById(curriculumId);
            verifyNoInteractions(unitRepository, unitFinder);
            verifyNoMoreInteractions(curriculumFinder);
        }

        @Test
        @DisplayName("UnitRepository 조회 중 예외가 발생하면 그대로 전파한다")
        void searchUnits_repositoryThrowsException_propagatesException() {
            // given
            UUID curriculumId = UUID.randomUUID();
            Pageable pageable = PageRequest.of(0, 20);
            RuntimeException exception = new RuntimeException("unit search failure");

            when(curriculumFinder.getById(curriculumId)).thenReturn(mock(Curriculum.class));
            when(unitRepository.searchUnits(curriculumId, pageable)).thenThrow(exception);

            // when & then
            assertThatThrownBy(() -> unitService.searchUnits(curriculumId, pageable))
                    .isSameAs(exception);

            verify(curriculumFinder).getById(curriculumId);
            verify(unitRepository).searchUnits(curriculumId, pageable);
            verifyNoInteractions(unitFinder);
            verifyNoMoreInteractions(curriculumFinder, unitRepository);
        }
    }

    @Nested
    @DisplayName("updateUnit 성공")
    class UpdateUnitSuccess {

        @Test
        @DisplayName("title과 language가 모두 존재하면 두 필드를 모두 수정한다")
        void updateUnit_allFieldsPresent_changesAllFields() {
            // given
            UUID unitId = UUID.randomUUID();
            Unit unit = spy(createUnitEntity(UUID.randomUUID()));
            UnitUpdateRequest request = mock(UnitUpdateRequest.class);

            when(request.getTitle()).thenReturn("수정된 제목");
            when(request.getLanguage()).thenReturn(ProgrammingLanguage.PYTHON);
            when(unitFinder.getById(unitId)).thenReturn(unit);

            // when
            UnitResponse result = unitService.updateUnit(unitId, request);

            // then
            assertThat(result).isNotNull();
            assertThat(unit.getTitle()).isEqualTo("수정된 제목");
            assertThat(unit.getLanguage()).isEqualTo(ProgrammingLanguage.PYTHON);

            verify(unit).changeTitle("수정된 제목");
            verify(unit).changeLanguage(ProgrammingLanguage.PYTHON);
            verify(unitFinder).getById(unitId);

            verifyNoInteractions(unitRepository);
        }

        @Test
        @DisplayName("title만 존재하면 title만 수정한다")
        void updateUnit_onlyTitle_changesOnlyTitle() {
            // given
            UUID unitId = UUID.randomUUID();
            Unit unit = spy(createUnitEntity(UUID.randomUUID()));
            UnitUpdateRequest request = mock(UnitUpdateRequest.class);

            when(request.getTitle()).thenReturn("새로운 제목");
            when(unitFinder.getById(unitId)).thenReturn(unit);

            // when
            unitService.updateUnit(unitId, request);

            // then
            assertThat(unit.getTitle()).isEqualTo("새로운 제목");

            verify(unit).changeTitle("새로운 제목");
            verify(unit, never()).changeLanguage(any());
            verify(unitFinder).getById(unitId);

            verifyNoInteractions(unitRepository);
        }

        @Test
        @DisplayName("language만 존재하면 language만 수정한다")
        void updateUnit_onlyLanguage_changesOnlyLanguage() {
            // given
            UUID unitId = UUID.randomUUID();
            Unit unit = spy(createUnitEntity(UUID.randomUUID()));
            UnitUpdateRequest request = mock(UnitUpdateRequest.class);

            when(request.getLanguage()).thenReturn(ProgrammingLanguage.PYTHON);
            when(unitFinder.getById(unitId)).thenReturn(unit);

            // when
            unitService.updateUnit(unitId, request);

            // then
            assertThat(unit.getLanguage()).isEqualTo(ProgrammingLanguage.PYTHON);

            verify(unit).changeLanguage(ProgrammingLanguage.PYTHON);
            verify(unit, never()).changeTitle(anyString());
            verify(unitFinder).getById(unitId);

            verifyNoInteractions(unitRepository);
        }

        @Test
        @DisplayName("모든 수정 필드가 null이면 변경 메서드를 호출하지 않는다")
        void updateUnit_allFieldsNull_doesNotChangeUnit() {
            // given
            UUID unitId = UUID.randomUUID();
            Unit unit = spy(createUnitEntity(UUID.randomUUID()));
            UnitUpdateRequest request = mock(UnitUpdateRequest.class);

            when(unitFinder.getById(unitId)).thenReturn(unit);

            // when
            UnitResponse result = unitService.updateUnit(unitId, request);

            // then
            assertThat(result).isNotNull();

            verify(unit, never()).changeTitle(anyString());
            verify(unit, never()).changeLanguage(any());
            verify(unitFinder).getById(unitId);

            verifyNoInteractions(unitRepository);
        }

        @Test
        @DisplayName("Unit 수정 시 Repository save를 별도로 호출하지 않는다")
        void updateUnit_doesNotCallSave() {
            // given
            UUID unitId = UUID.randomUUID();
            Unit unit = spy(createUnitEntity(UUID.randomUUID()));
            UnitUpdateRequest request = mock(UnitUpdateRequest.class);

            when(request.getTitle()).thenReturn("수정된 제목");
            when(unitFinder.getById(unitId)).thenReturn(unit);

            // when
            unitService.updateUnit(unitId, request);

            // then
            verify(unit).changeTitle("수정된 제목");
            verify(unitRepository, never()).save(any(Unit.class));
            verify(unitFinder).getById(unitId);

        }
    }

    @Nested
    @DisplayName("updateUnit 실패")
    class UpdateUnitFailure {

        @Test
        @DisplayName("Unit 조회에 실패하면 수정 요청을 읽지 않고 예외를 그대로 전파한다")
        void updateUnit_unitNotFound_doesNotUpdate() {
            // given
            UUID unitId = UUID.randomUUID();
            UnitUpdateRequest request = mock(UnitUpdateRequest.class);
            BusinessException exception = new BusinessException(ErrorCode.UNIT_NOT_FOUND);

            when(unitFinder.getById(unitId)).thenThrow(exception);

            // when & then
            assertThatThrownBy(() -> unitService.updateUnit(unitId, request))
                    .isSameAs(exception)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.UNIT_NOT_FOUND));

            verify(unitFinder).getById(unitId);
            verifyNoInteractions(request, unitRepository, curriculumFinder);
            verifyNoMoreInteractions(unitFinder);
        }

        @Test
        @DisplayName("UnitFinder에서 예상하지 못한 예외가 발생하면 그대로 전파한다")
        void updateUnit_finderThrowsUnexpectedException_propagatesException() {
            // given
            UUID unitId = UUID.randomUUID();
            UnitUpdateRequest request = mock(UnitUpdateRequest.class);
            RuntimeException exception = new RuntimeException("unit finder failure");

            when(unitFinder.getById(unitId)).thenThrow(exception);

            // when & then
            assertThatThrownBy(() -> unitService.updateUnit(unitId, request))
                    .isSameAs(exception);

            verify(unitFinder).getById(unitId);
            verifyNoInteractions(request, unitRepository, curriculumFinder);
            verifyNoMoreInteractions(unitFinder);
        }
    }

    @Nested
    @DisplayName("deleteUnit")
    class DeleteUnit {

        @Test
        @DisplayName("Unit을 조회한 뒤 전달받은 userId로 softDelete한다")
        void deleteUnit_existingUnit_softDeletesUnit() {
            // given
            UUID unitId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            Unit unit = spy(createUnitEntity(UUID.randomUUID()));

            when(unitFinder.getById(unitId)).thenReturn(unit);

            // when
            unitService.deleteUnit(unitId, userId);

            // then
            verify(unitFinder).getById(unitId);
            verify(unit).softDelete(userId);

            assertThat(unit.isDeleted()).isTrue();
            assertThat(unit.getDeletedBy()).isEqualTo(userId);

            verifyNoInteractions(unitRepository, curriculumFinder);
        }

        @Test
        @DisplayName("Unit 삭제 시 Repository save를 별도로 호출하지 않는다")
        void deleteUnit_doesNotCallSave() {
            // given
            UUID unitId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            Unit unit = spy(createUnitEntity(UUID.randomUUID()));

            when(unitFinder.getById(unitId)).thenReturn(unit);

            // when
            unitService.deleteUnit(unitId, userId);

            // then
            verify(unitFinder).getById(unitId);
            verify(unit).softDelete(userId);
            verify(unitRepository, never()).save(any(Unit.class));
            verifyNoInteractions(curriculumFinder);
        }

        @Test
        @DisplayName("Unit 조회에 실패하면 삭제하지 않고 예외를 그대로 전파한다")
        void deleteUnit_unitNotFound_propagatesException() {
            // given
            UUID unitId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            BusinessException exception = new BusinessException(ErrorCode.UNIT_NOT_FOUND);

            when(unitFinder.getById(unitId)).thenThrow(exception);

            // when & then
            assertThatThrownBy(() -> unitService.deleteUnit(unitId, userId))
                    .isSameAs(exception)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.UNIT_NOT_FOUND));

            verify(unitFinder).getById(unitId);
            verifyNoInteractions(unitRepository, curriculumFinder);
            verifyNoMoreInteractions(unitFinder);
        }

        @Test
        @DisplayName("UnitFinder에서 예상하지 못한 예외가 발생하면 그대로 전파한다")
        void deleteUnit_finderThrowsUnexpectedException_propagatesException() {
            // given
            UUID unitId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            RuntimeException exception = new RuntimeException("unit finder failure");

            when(unitFinder.getById(unitId)).thenThrow(exception);

            // when & then
            assertThatThrownBy(() -> unitService.deleteUnit(unitId, userId))
                    .isSameAs(exception);

            verify(unitFinder).getById(unitId);
            verifyNoInteractions(unitRepository, curriculumFinder);
            verifyNoMoreInteractions(unitFinder);
        }
    }

    private UnitCreateRequest createRequest(
            UUID curriculumId,
            String title,
            ProgrammingLanguage language
    ) {
        UnitCreateRequest request = mock(UnitCreateRequest.class);

        when(request.getCurriculumId()).thenReturn(curriculumId);
        when(request.getTitle()).thenReturn(title);
        when(request.getLanguage()).thenReturn(language);

        return request;
    }

    private Unit createUnitEntity(UUID curriculumId) {
        return Unit.create(
                curriculumId,
                "기존 제목",
                ProgrammingLanguage.JAVA,
                1
        );
    }
}
