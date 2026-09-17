package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.entity.Unit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UnitRepositoryImpl 테스트")
class UnitRepositoryImplTest {

    @Mock
    private SpringDataUnitRepository springDataUnitRepository;

    @InjectMocks
    private UnitRepositoryImpl unitRepository;

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("Unit을 저장하고 Spring Data Repository가 반환한 Unit을 반환한다")
        void save_success() {

            // given
            Unit unit = mock(Unit.class);
            Unit savedUnit = mock(Unit.class);

            when(springDataUnitRepository.save(unit))
                    .thenReturn(savedUnit);

            // when
            Unit result = unitRepository.save(unit);

            // then
            assertThat(result)
                    .isSameAs(savedUnit);

            verify(springDataUnitRepository, times(1))
                    .save(unit);

            verifyNoMoreInteractions(springDataUnitRepository);
        }

        @Test
        @DisplayName("save는 전달받은 Unit 인스턴스를 그대로 Spring Data Repository에 전달한다")
        void save_passesExactUnit() {

            // given
            Unit unit = mock(Unit.class);

            when(springDataUnitRepository.save(any(Unit.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            ArgumentCaptor<Unit> captor =
                    ArgumentCaptor.forClass(Unit.class);

            // when
            Unit result = unitRepository.save(unit);

            // then
            verify(springDataUnitRepository)
                    .save(captor.capture());

            assertThat(captor.getValue())
                    .isSameAs(unit);

            assertThat(result)
                    .isSameAs(unit);

            verifyNoMoreInteractions(springDataUnitRepository);
        }

        @Test
        @DisplayName("save는 Spring Data Repository의 반환값을 별도 변환 없이 그대로 반환한다")
        void save_returnsRepositoryResultAsIs() {

            // given
            Unit unit = mock(Unit.class);
            Unit expected = mock(Unit.class);

            when(springDataUnitRepository.save(unit))
                    .thenReturn(expected);

            // when
            Unit result = unitRepository.save(unit);

            // then
            assertThat(result)
                    .isSameAs(expected);

            verify(springDataUnitRepository)
                    .save(unit);

            verifyNoMoreInteractions(springDataUnitRepository);
        }
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("삭제되지 않은 Unit이 존재하면 Unit을 반환한다")
        void findById_exists_returnsUnit() {

            // given
            UUID unitId = UUID.randomUUID();

            Unit unit = mock(Unit.class);

            when(springDataUnitRepository
                    .findByIdAndDeletedAtIsNull(unitId))
                    .thenReturn(Optional.of(unit));

            // when
            Optional<Unit> result =
                    unitRepository.findById(unitId);

            // then
            assertThat(result)
                    .isPresent()
                    .containsSame(unit);

            verify(springDataUnitRepository, times(1))
                    .findByIdAndDeletedAtIsNull(unitId);

            verifyNoMoreInteractions(springDataUnitRepository);
        }

        @Test
        @DisplayName("삭제되지 않은 Unit이 존재하지 않으면 Optional.empty를 반환한다")
        void findById_notExists_returnsEmpty() {

            // given
            UUID unitId = UUID.randomUUID();

            when(springDataUnitRepository
                    .findByIdAndDeletedAtIsNull(unitId))
                    .thenReturn(Optional.empty());

            // when
            Optional<Unit> result =
                    unitRepository.findById(unitId);

            // then
            assertThat(result)
                    .isEmpty();

            verify(springDataUnitRepository, times(1))
                    .findByIdAndDeletedAtIsNull(unitId);

            verifyNoMoreInteractions(springDataUnitRepository);
        }

        @Test
        @DisplayName("findById는 일반 findById가 아니라 삭제되지 않은 Unit 조회 메서드를 사용한다")
        void findById_callsSoftDeleteAwareMethod() {

            // given
            UUID unitId = UUID.randomUUID();

            when(springDataUnitRepository
                    .findByIdAndDeletedAtIsNull(unitId))
                    .thenReturn(Optional.empty());

            // when
            unitRepository.findById(unitId);

            // then
            verify(springDataUnitRepository)
                    .findByIdAndDeletedAtIsNull(unitId);

            verify(springDataUnitRepository, never())
                    .findById(unitId);

            verifyNoMoreInteractions(springDataUnitRepository);
        }

        @Test
        @DisplayName("findById는 전달받은 unitId를 그대로 Spring Data Repository에 전달한다")
        void findById_passesUnitId() {

            // given
            UUID unitId = UUID.randomUUID();

            when(springDataUnitRepository
                    .findByIdAndDeletedAtIsNull(any(UUID.class)))
                    .thenReturn(Optional.empty());

            ArgumentCaptor<UUID> captor =
                    ArgumentCaptor.forClass(UUID.class);

            // when
            unitRepository.findById(unitId);

            // then
            verify(springDataUnitRepository)
                    .findByIdAndDeletedAtIsNull(captor.capture());

            assertThat(captor.getValue())
                    .isEqualTo(unitId);

            verifyNoMoreInteractions(springDataUnitRepository);
        }

        @Test
        @DisplayName("findById는 Spring Data Repository가 반환한 Optional을 그대로 반환한다")
        void findById_returnsSameOptional() {

            // given
            UUID unitId = UUID.randomUUID();
            Unit unit = mock(Unit.class);

            Optional<Unit> expected =
                    Optional.of(unit);

            when(springDataUnitRepository
                    .findByIdAndDeletedAtIsNull(unitId))
                    .thenReturn(expected);

            // when
            Optional<Unit> result =
                    unitRepository.findById(unitId);

            // then
            assertThat(result)
                    .isSameAs(expected);

            verify(springDataUnitRepository)
                    .findByIdAndDeletedAtIsNull(unitId);

            verifyNoMoreInteractions(springDataUnitRepository);
        }
    }

    @Nested
    @DisplayName("countByCurriculumId")
    class CountByCurriculumId {

        @Test
        @DisplayName("Curriculum에 삭제되지 않은 Unit이 존재하면 개수를 반환한다")
        void countByCurriculumId_unitsExist_returnsCount() {

            // given
            UUID curriculumId = UUID.randomUUID();

            when(springDataUnitRepository
                    .countByCurriculumIdAndDeletedAtIsNull(curriculumId))
                    .thenReturn(3L);

            // when
            long result =
                    unitRepository.countByCurriculumId(curriculumId);

            // then
            assertThat(result)
                    .isEqualTo(3L);

            verify(springDataUnitRepository, times(1))
                    .countByCurriculumIdAndDeletedAtIsNull(curriculumId);

            verifyNoMoreInteractions(springDataUnitRepository);
        }

        @Test
        @DisplayName("Curriculum에 삭제되지 않은 Unit이 없으면 0을 반환한다")
        void countByCurriculumId_noUnits_returnsZero() {

            // given
            UUID curriculumId = UUID.randomUUID();

            when(springDataUnitRepository
                    .countByCurriculumIdAndDeletedAtIsNull(curriculumId))
                    .thenReturn(0L);

            // when
            long result =
                    unitRepository.countByCurriculumId(curriculumId);

            // then
            assertThat(result)
                    .isZero();

            verify(springDataUnitRepository, times(1))
                    .countByCurriculumIdAndDeletedAtIsNull(curriculumId);

            verifyNoMoreInteractions(springDataUnitRepository);
        }

        @Test
        @DisplayName("countByCurriculumId는 삭제되지 않은 Unit만 세는 메서드를 호출한다")
        void countByCurriculumId_callsSoftDeleteAwareMethod() {

            // given
            UUID curriculumId = UUID.randomUUID();

            when(springDataUnitRepository
                    .countByCurriculumIdAndDeletedAtIsNull(curriculumId))
                    .thenReturn(1L);

            // when
            unitRepository.countByCurriculumId(curriculumId);

            // then
            verify(springDataUnitRepository)
                    .countByCurriculumIdAndDeletedAtIsNull(curriculumId);

            verifyNoMoreInteractions(springDataUnitRepository);
        }

        @Test
        @DisplayName("countByCurriculumId는 전달받은 curriculumId를 그대로 전달한다")
        void countByCurriculumId_passesCurriculumId() {

            // given
            UUID curriculumId = UUID.randomUUID();

            when(springDataUnitRepository
                    .countByCurriculumIdAndDeletedAtIsNull(any(UUID.class)))
                    .thenReturn(0L);

            ArgumentCaptor<UUID> captor =
                    ArgumentCaptor.forClass(UUID.class);

            // when
            unitRepository.countByCurriculumId(curriculumId);

            // then
            verify(springDataUnitRepository)
                    .countByCurriculumIdAndDeletedAtIsNull(
                            captor.capture()
                    );

            assertThat(captor.getValue())
                    .isEqualTo(curriculumId);

            verifyNoMoreInteractions(springDataUnitRepository);
        }

        @Test
        @DisplayName("countByCurriculumId는 Spring Data Repository가 반환한 long 값을 그대로 반환한다")
        void countByCurriculumId_returnsRepositoryResultAsIs() {

            // given
            UUID curriculumId = UUID.randomUUID();

            long expected = 123L;

            when(springDataUnitRepository
                    .countByCurriculumIdAndDeletedAtIsNull(curriculumId))
                    .thenReturn(expected);

            // when
            long result =
                    unitRepository.countByCurriculumId(curriculumId);

            // then
            assertThat(result)
                    .isEqualTo(expected);

            verify(springDataUnitRepository)
                    .countByCurriculumIdAndDeletedAtIsNull(curriculumId);

            verifyNoMoreInteractions(springDataUnitRepository);
        }
    }

    @Nested
    @DisplayName("searchUnits")
    class SearchUnits {

        @Test
        @DisplayName("Curriculum의 삭제되지 않은 Unit 목록을 페이지로 반환한다")
        void searchUnits_success() {

            // given
            UUID curriculumId = UUID.randomUUID();

            Pageable pageable =
                    PageRequest.of(0, 20);

            Unit first = mock(Unit.class);
            Unit second = mock(Unit.class);
            Unit third = mock(Unit.class);

            List<Unit> units =
                    List.of(
                            first,
                            second,
                            third
                    );

            Page<Unit> expected =
                    new PageImpl<>(
                            units,
                            pageable,
                            3
                    );

            when(springDataUnitRepository
                    .findByCurriculumIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(
                            curriculumId,
                            pageable
                    ))
                    .thenReturn(expected);

            // when
            Page<Unit> result =
                    unitRepository.searchUnits(
                            curriculumId,
                            pageable
                    );

            // then
            assertThat(result)
                    .isSameAs(expected);

            assertThat(result.getContent())
                    .containsExactly(
                            first,
                            second,
                            third
                    );

            assertThat(result.getNumber())
                    .isZero();

            assertThat(result.getSize())
                    .isEqualTo(20);

            assertThat(result.getTotalElements())
                    .isEqualTo(3);

            assertThat(result.getTotalPages())
                    .isEqualTo(1);

            verify(springDataUnitRepository, times(1))
                    .findByCurriculumIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(
                            curriculumId,
                            pageable
                    );

            verifyNoMoreInteractions(springDataUnitRepository);
        }

        @Test
        @DisplayName("Curriculum에 조회할 Unit이 없으면 빈 Page를 반환한다")
        void searchUnits_empty_returnsEmptyPage() {

            // given
            UUID curriculumId = UUID.randomUUID();

            Pageable pageable =
                    PageRequest.of(0, 20);

            Page<Unit> expected =
                    Page.empty(pageable);

            when(springDataUnitRepository
                    .findByCurriculumIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(
                            curriculumId,
                            pageable
                    ))
                    .thenReturn(expected);

            // when
            Page<Unit> result =
                    unitRepository.searchUnits(
                            curriculumId,
                            pageable
                    );

            // then
            assertThat(result)
                    .isNotNull()
                    .isEmpty();

            assertThat(result.getContent())
                    .isEmpty();

            assertThat(result.getNumber())
                    .isZero();

            assertThat(result.getSize())
                    .isEqualTo(20);

            assertThat(result.getTotalElements())
                    .isZero();

            assertThat(result.getTotalPages())
                    .isZero();

            verify(springDataUnitRepository, times(1))
                    .findByCurriculumIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(
                            curriculumId,
                            pageable
                    );

            verifyNoMoreInteractions(springDataUnitRepository);
        }

        @Test
        @DisplayName("searchUnits는 삭제되지 않은 Unit을 displayOrder 오름차순, id 오름차순으로 조회하는 메서드를 호출한다")
        void searchUnits_callsCorrectSpringDataMethod() {

            // given
            UUID curriculumId = UUID.randomUUID();

            Pageable pageable =
                    PageRequest.of(0, 20);

            when(springDataUnitRepository
                    .findByCurriculumIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(
                            curriculumId,
                            pageable
                    ))
                    .thenReturn(Page.empty(pageable));

            // when
            unitRepository.searchUnits(
                    curriculumId,
                    pageable
            );

            // then
            verify(springDataUnitRepository)
                    .findByCurriculumIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(
                            curriculumId,
                            pageable
                    );

            verifyNoMoreInteractions(springDataUnitRepository);
        }

        @Test
        @DisplayName("searchUnits는 curriculumId와 Pageable을 정확하게 전달한다")
        void searchUnits_passesArgumentsCorrectly() {

            // given
            UUID curriculumId = UUID.randomUUID();

            Pageable pageable =
                    PageRequest.of(
                            2,
                            5
                    );

            when(springDataUnitRepository
                    .findByCurriculumIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(
                            any(UUID.class),
                            any(Pageable.class)
                    ))
                    .thenReturn(Page.empty(pageable));

            ArgumentCaptor<UUID> curriculumIdCaptor =
                    ArgumentCaptor.forClass(UUID.class);

            ArgumentCaptor<Pageable> pageableCaptor =
                    ArgumentCaptor.forClass(Pageable.class);

            // when
            unitRepository.searchUnits(
                    curriculumId,
                    pageable
            );

            // then
            verify(springDataUnitRepository)
                    .findByCurriculumIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(
                            curriculumIdCaptor.capture(),
                            pageableCaptor.capture()
                    );

            assertThat(curriculumIdCaptor.getValue())
                    .isEqualTo(curriculumId);

            assertThat(pageableCaptor.getValue())
                    .isSameAs(pageable);

            verifyNoMoreInteractions(springDataUnitRepository);
        }

        @Test
        @DisplayName("두 번째 페이지 조회 시 Pageable을 변경하지 않고 그대로 전달한다")
        void searchUnits_secondPage_passesPageableAsIs() {

            // given
            UUID curriculumId = UUID.randomUUID();

            Pageable pageable =
                    PageRequest.of(
                            1,
                            10
                    );

            Unit unit = mock(Unit.class);

            Page<Unit> expected =
                    new PageImpl<>(
                            List.of(unit),
                            pageable,
                            11
                    );

            when(springDataUnitRepository
                    .findByCurriculumIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(
                            curriculumId,
                            pageable
                    ))
                    .thenReturn(expected);

            // when
            Page<Unit> result =
                    unitRepository.searchUnits(
                            curriculumId,
                            pageable
                    );

            // then
            assertThat(result.getNumber())
                    .isEqualTo(1);

            assertThat(result.getSize())
                    .isEqualTo(10);

            assertThat(result.getTotalElements())
                    .isEqualTo(11);

            assertThat(result.getTotalPages())
                    .isEqualTo(2);

            assertThat(result.getContent())
                    .containsExactly(unit);

            verify(springDataUnitRepository)
                    .findByCurriculumIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(
                            curriculumId,
                            pageable
                    );

            verifyNoMoreInteractions(springDataUnitRepository);
        }

        @Test
        @DisplayName("searchUnits는 Spring Data Repository가 반환한 Page 객체를 그대로 반환한다")
        @SuppressWarnings("unchecked")
        void searchUnits_returnsSamePage() {

            // given
            UUID curriculumId = UUID.randomUUID();

            Pageable pageable =
                    PageRequest.of(0, 20);

            Page<Unit> expected =
                    mock(Page.class);

            when(springDataUnitRepository
                    .findByCurriculumIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(
                            curriculumId,
                            pageable
                    ))
                    .thenReturn(expected);

            // when
            Page<Unit> result =
                    unitRepository.searchUnits(
                            curriculumId,
                            pageable
                    );

            // then
            assertThat(result)
                    .isSameAs(expected);

            verify(springDataUnitRepository)
                    .findByCurriculumIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(
                            curriculumId,
                            pageable
                    );

            verifyNoMoreInteractions(springDataUnitRepository);
        }

        @Test
        @DisplayName("마지막 페이지에서도 Spring Data Repository의 페이지 정보를 그대로 반환한다")
        void searchUnits_lastPage_returnsPageInformationAsIs() {

            // given
            UUID curriculumId = UUID.randomUUID();

            Pageable pageable =
                    PageRequest.of(
                            2,
                            10
                    );

            Unit first = mock(Unit.class);
            Unit second = mock(Unit.class);

            Page<Unit> expected =
                    new PageImpl<>(
                            List.of(
                                    first,
                                    second
                            ),
                            pageable,
                            22
                    );

            when(springDataUnitRepository
                    .findByCurriculumIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(
                            curriculumId,
                            pageable
                    ))
                    .thenReturn(expected);

            // when
            Page<Unit> result =
                    unitRepository.searchUnits(
                            curriculumId,
                            pageable
                    );

            // then
            assertThat(result.getNumber())
                    .isEqualTo(2);

            assertThat(result.getSize())
                    .isEqualTo(10);

            assertThat(result.getNumberOfElements())
                    .isEqualTo(2);

            assertThat(result.getTotalElements())
                    .isEqualTo(22);

            assertThat(result.getTotalPages())
                    .isEqualTo(3);

            assertThat(result.isFirst())
                    .isFalse();

            assertThat(result.isLast())
                    .isTrue();

            assertThat(result.getContent())
                    .containsExactly(
                            first,
                            second
                    );

            verify(springDataUnitRepository)
                    .findByCurriculumIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(
                            curriculumId,
                            pageable
                    );

            verifyNoMoreInteractions(springDataUnitRepository);
        }
    }
}