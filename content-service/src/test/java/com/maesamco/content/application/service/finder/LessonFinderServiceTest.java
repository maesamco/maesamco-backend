package com.maesamco.content.application.service.finder;

import com.maesamco.content.domain.entity.Curriculum;
import com.maesamco.content.domain.entity.Lesson;
import com.maesamco.content.domain.entity.Unit;
import com.maesamco.content.domain.repository.CurriculumRepository;
import com.maesamco.content.domain.repository.LessonRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LessonFinderService 단위 테스트")
class LessonFinderServiceTest {

    @Mock
    private LessonRepository lessonRepository;

    @Mock
    private UnitRepository unitRepository;

    @Mock
    private CurriculumRepository curriculumRepository;

    private LessonFinderService lessonFinderService;

    @BeforeEach
    void setUp() {
        lessonFinderService = new LessonFinderService(lessonRepository, unitRepository, curriculumRepository);
    }

    // ============================================================
    // 1. findLessonById - 정상 조회
    // ============================================================

    @Nested
    @DisplayName("findLessonById 성공")
    class FindLessonByIdSuccess {

        @Test
        @DisplayName("Lesson, Unit, Curriculum이 모두 존재하면 조회한 Lesson을 그대로 반환한다")
        void findLessonById_allHierarchyExists_returnsLesson() {

            // given
            UUID lessonId = UUID.randomUUID();
            UUID unitId = UUID.randomUUID();
            UUID curriculumId = UUID.randomUUID();

            Lesson lesson = mock(Lesson.class);
            Unit unit = mock(Unit.class);
            Curriculum curriculum = mock(Curriculum.class);

            when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(lesson));
            when(lesson.getUnitId()).thenReturn(unitId);
            when(unitRepository.findById(unitId)).thenReturn(Optional.of(unit));
            when(unit.getCurriculumId()).thenReturn(curriculumId);
            when(curriculumRepository.findById(curriculumId)).thenReturn(Optional.of(curriculum));

            // when
            Lesson result = lessonFinderService.getById(lessonId);

            // then
            assertThat(result).isNotNull();
            assertThat(result).isSameAs(lesson);

            InOrder inOrder = inOrder(lessonRepository, unitRepository, curriculumRepository);
            inOrder.verify(lessonRepository).findById(lessonId);
            inOrder.verify(unitRepository).findById(unitId);
            inOrder.verify(curriculumRepository).findById(curriculumId);

            verifyNoMoreInteractions(lessonRepository, unitRepository, curriculumRepository);
        }

        @Test
        @DisplayName("Lesson의 unitId와 Unit의 curriculumId를 사용해 상위 계층을 조회한다")
        void findLessonById_usesHierarchyIdsFromEntities() {

            // given
            UUID lessonId = UUID.randomUUID();
            UUID unitId = UUID.randomUUID();
            UUID curriculumId = UUID.randomUUID();

            Lesson lesson = mock(Lesson.class);
            Unit unit = mock(Unit.class);
            Curriculum curriculum = mock(Curriculum.class);

            when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(lesson));
            when(lesson.getUnitId()).thenReturn(unitId);
            when(unitRepository.findById(unitId)).thenReturn(Optional.of(unit));
            when(unit.getCurriculumId()).thenReturn(curriculumId);
            when(curriculumRepository.findById(curriculumId)).thenReturn(Optional.of(curriculum));

            // when
            Lesson result = lessonFinderService.getById(lessonId);

            // then
            assertThat(result).isSameAs(lesson);

            verify(lessonRepository).findById(lessonId);
            verify(unitRepository).findById(unitId);
            verify(curriculumRepository).findById(curriculumId);
            verifyNoMoreInteractions(lessonRepository, unitRepository, curriculumRepository);
        }
    }

    // ============================================================
    // 2. Lesson 조회 실패
    // ============================================================

    @Nested
    @DisplayName("Lesson 조회 실패")
    class LessonNotFound {

        @Test
        @DisplayName("Lesson이 존재하지 않으면 LESSON_NOT_FOUND 예외가 발생한다")
        void findLessonById_lessonNotFound_throwsLessonNotFound() {

            // given
            UUID lessonId = UUID.randomUUID();

            when(lessonRepository.findById(lessonId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> lessonFinderService.getById(lessonId))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(exception -> {
                        BusinessException businessException = (BusinessException) exception;
                        assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.LESSON_NOT_FOUND);
                    });

            verify(lessonRepository).findById(lessonId);
            verifyNoInteractions(unitRepository, curriculumRepository);
            verifyNoMoreInteractions(lessonRepository);
        }
    }

    // ============================================================
    // 3. Unit 조회 실패
    // ============================================================

    @Nested
    @DisplayName("Unit 조회 실패")
    class UnitNotFound {

        @Test
        @DisplayName("Lesson은 존재하지만 상위 Unit이 존재하지 않으면 UNIT_NOT_FOUND 예외가 발생한다")
        void findLessonById_unitNotFound_throwsUnitNotFound() {

            // given
            UUID lessonId = UUID.randomUUID();
            UUID unitId = UUID.randomUUID();
            Lesson lesson = mock(Lesson.class);

            when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(lesson));
            when(lesson.getUnitId()).thenReturn(unitId);
            when(unitRepository.findById(unitId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> lessonFinderService.getById(lessonId))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(exception -> {
                        BusinessException businessException = (BusinessException) exception;
                        assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.UNIT_NOT_FOUND);
                    });

            verify(lessonRepository).findById(lessonId);
            verify(unitRepository).findById(unitId);
            verifyNoInteractions(curriculumRepository);
            verifyNoMoreInteractions(lessonRepository, unitRepository);
        }
    }

    // ============================================================
    // 4. Curriculum 조회 실패
    // ============================================================

    @Nested
    @DisplayName("Curriculum 조회 실패")
    class CurriculumNotFound {

        @Test
        @DisplayName("Lesson과 Unit은 존재하지만 상위 Curriculum이 존재하지 않으면 CURRICULUM_NOT_FOUND 예외가 발생한다")
        void findLessonById_curriculumNotFound_throwsCurriculumNotFound() {

            // given
            UUID lessonId = UUID.randomUUID();
            UUID unitId = UUID.randomUUID();
            UUID curriculumId = UUID.randomUUID();

            Lesson lesson = mock(Lesson.class);
            Unit unit = mock(Unit.class);

            when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(lesson));
            when(lesson.getUnitId()).thenReturn(unitId);
            when(unitRepository.findById(unitId)).thenReturn(Optional.of(unit));
            when(unit.getCurriculumId()).thenReturn(curriculumId);
            when(curriculumRepository.findById(curriculumId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> lessonFinderService.getById(lessonId))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(exception -> {
                        BusinessException businessException = (BusinessException) exception;
                        assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.CURRICULUM_NOT_FOUND);
                    });

            verify(lessonRepository).findById(lessonId);
            verify(unitRepository).findById(unitId);
            verify(curriculumRepository).findById(curriculumId);
            verifyNoMoreInteractions(lessonRepository, unitRepository, curriculumRepository);
        }
    }

    // ============================================================
    // 5. Repository 예외 전파
    // ============================================================

    @Nested
    @DisplayName("Repository 예외 전파")
    class RepositoryExceptionPropagation {

        @Test
        @DisplayName("LessonRepository에서 예상하지 못한 예외가 발생하면 그대로 전파한다")
        void findLessonById_lessonRepositoryThrowsException_propagatesException() {

            // given
            UUID lessonId = UUID.randomUUID();
            RuntimeException repositoryException = new RuntimeException("lesson repository failure");

            when(lessonRepository.findById(lessonId)).thenThrow(repositoryException);

            // when & then
            assertThatThrownBy(() -> lessonFinderService.getById(lessonId))
                    .isSameAs(repositoryException);

            verify(lessonRepository).findById(lessonId);
            verifyNoInteractions(unitRepository, curriculumRepository);
            verifyNoMoreInteractions(lessonRepository);
        }

        @Test
        @DisplayName("UnitRepository에서 예상하지 못한 예외가 발생하면 그대로 전파하고 Curriculum은 조회하지 않는다")
        void findLessonById_unitRepositoryThrowsException_propagatesException() {

            // given
            UUID lessonId = UUID.randomUUID();
            UUID unitId = UUID.randomUUID();

            Lesson lesson = mock(Lesson.class);
            RuntimeException repositoryException = new RuntimeException("unit repository failure");

            when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(lesson));
            when(lesson.getUnitId()).thenReturn(unitId);
            when(unitRepository.findById(unitId)).thenThrow(repositoryException);

            // when & then
            assertThatThrownBy(() -> lessonFinderService.getById(lessonId))
                    .isSameAs(repositoryException);

            verify(lessonRepository).findById(lessonId);
            verify(unitRepository).findById(unitId);
            verifyNoInteractions(curriculumRepository);
            verifyNoMoreInteractions(lessonRepository, unitRepository);
        }

        @Test
        @DisplayName("CurriculumRepository에서 예상하지 못한 예외가 발생하면 그대로 전파한다")
        void findLessonById_curriculumRepositoryThrowsException_propagatesException() {

            // given
            UUID lessonId = UUID.randomUUID();
            UUID unitId = UUID.randomUUID();
            UUID curriculumId = UUID.randomUUID();

            Lesson lesson = mock(Lesson.class);
            Unit unit = mock(Unit.class);
            RuntimeException repositoryException = new RuntimeException("curriculum repository failure");

            when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(lesson));
            when(lesson.getUnitId()).thenReturn(unitId);
            when(unitRepository.findById(unitId)).thenReturn(Optional.of(unit));
            when(unit.getCurriculumId()).thenReturn(curriculumId);
            when(curriculumRepository.findById(curriculumId)).thenThrow(repositoryException);

            // when & then
            assertThatThrownBy(() -> lessonFinderService.getById(lessonId))
                    .isSameAs(repositoryException);

            verify(lessonRepository).findById(lessonId);
            verify(unitRepository).findById(unitId);
            verify(curriculumRepository).findById(curriculumId);
            verifyNoMoreInteractions(lessonRepository, unitRepository, curriculumRepository);
        }
    }
}