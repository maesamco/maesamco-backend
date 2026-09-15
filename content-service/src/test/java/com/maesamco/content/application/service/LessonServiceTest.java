package com.maesamco.content.application.service;

import com.maesamco.content.application.input_port.LessonFinder;
import com.maesamco.content.application.input_port.UnitFinder;
import com.maesamco.content.domain.entity.Lesson;
import com.maesamco.content.domain.entity.ProgrammingLanguage;
import com.maesamco.content.domain.entity.Unit;
import com.maesamco.content.domain.repository.LessonRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import com.maesamco.content.global.response.PageResponse;
import com.maesamco.content.presentation.request.LessonCreateRequest;
import com.maesamco.content.presentation.request.LessonUpdateRequest;
import com.maesamco.content.presentation.response.LessonCreateResponse;
import com.maesamco.content.presentation.response.LessonResponse;
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
@DisplayName("LessonService 단위 테스트")
class LessonServiceTest {

    @Mock
    private LessonRepository lessonRepository;

    @Mock
    private LessonFinder lessonFinder;

    @Mock
    private UnitFinder unitFinder;

    private LessonService lessonService;

    @BeforeEach
    void setUp() {
        lessonService = new LessonService(lessonRepository, lessonFinder, unitFinder);
    }

    @Nested
    @DisplayName("createLesson 성공")
    class CreateLessonSuccess {

        @Test
        @DisplayName("상위 Unit이 존재하면 현재 레슨 수를 기준으로 displayOrder를 계산하고 Lesson을 저장한다")
        void createLesson_validRequest_savesLesson() {
            // given
            UUID unitId = UUID.randomUUID();
            LessonCreateRequest request = createRequest(
                    unitId,
                    "조건문",
                    "조건문에 대해 학습합니다.",
                    "if문과 switch문",
                    ProgrammingLanguage.JAVA
            );

            when(unitFinder.findById(unitId)).thenReturn(mock(Unit.class));
            when(lessonRepository.countByUnitId(unitId)).thenReturn(2L);
            when(lessonRepository.save(any(Lesson.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            LessonCreateResponse result = lessonService.createLesson(request);

            // then
            assertThat(result).isNotNull();

            ArgumentCaptor<Lesson> captor = ArgumentCaptor.forClass(Lesson.class);
            verify(lessonRepository).save(captor.capture());

            Lesson savedLesson = captor.getValue();

            assertThat(savedLesson.getUnitId()).isEqualTo(unitId);
            assertThat(savedLesson.getTitle()).isEqualTo("조건문");
            assertThat(savedLesson.getDescription()).isEqualTo("조건문에 대해 학습합니다.");
            assertThat(savedLesson.getContent()).isEqualTo("if문과 switch문");
            assertThat(savedLesson.getLanguage()).isEqualTo(ProgrammingLanguage.JAVA);
            assertThat(savedLesson.getDisplayOrder()).isEqualTo(3);

            verify(unitFinder).findById(unitId);
            verify(lessonRepository).countByUnitId(unitId);
            verifyNoInteractions(lessonFinder);
            verifyNoMoreInteractions(unitFinder, lessonRepository);
        }

        @Test
        @DisplayName("Lesson 생성은 Unit 검증 -> 기존 레슨 수 조회 -> 저장 순서로 수행한다")
        void createLesson_performsOperationsInOrder() {
            // given
            UUID unitId = UUID.randomUUID();
            LessonCreateRequest request = createRequest(
                    unitId,
                    "반복문",
                    "반복문 설명",
                    "for, while",
                    ProgrammingLanguage.JAVA
            );

            when(unitFinder.findById(unitId)).thenReturn(mock(Unit.class));
            when(lessonRepository.countByUnitId(unitId)).thenReturn(0L);
            when(lessonRepository.save(any(Lesson.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            lessonService.createLesson(request);

            // then
            InOrder inOrder = inOrder(unitFinder, lessonRepository);
            inOrder.verify(unitFinder).findById(unitId);
            inOrder.verify(lessonRepository).countByUnitId(unitId);
            inOrder.verify(lessonRepository).save(any(Lesson.class));
            inOrder.verifyNoMoreInteractions();

            verifyNoInteractions(lessonFinder);
        }

        @Test
        @DisplayName("기존 Lesson 개수에 1을 더한 값을 displayOrder로 사용한다")
        void createLesson_calculatesDisplayOrderFromCount() {
            // given
            UUID unitId = UUID.randomUUID();
            LessonCreateRequest request = createRequest(
                    unitId,
                    "배열",
                    "배열 설명",
                    "array",
                    ProgrammingLanguage.JAVA
            );

            when(unitFinder.findById(unitId)).thenReturn(mock(Unit.class));
            when(lessonRepository.countByUnitId(unitId)).thenReturn(7L);
            when(lessonRepository.save(any(Lesson.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ArgumentCaptor<Lesson> captor = ArgumentCaptor.forClass(Lesson.class);

            // when
            lessonService.createLesson(request);

            // then
            verify(lessonRepository).save(captor.capture());
            assertThat(captor.getValue().getDisplayOrder()).isEqualTo(8);

            verify(unitFinder).findById(unitId);
            verify(lessonRepository).countByUnitId(unitId);
            verifyNoInteractions(lessonFinder);
            verifyNoMoreInteractions(unitFinder, lessonRepository);
        }
    }

    @Nested
    @DisplayName("createLesson 실패")
    class CreateLessonFailure {

        @Test
        @DisplayName("상위 Unit이 존재하지 않으면 예외를 그대로 전파하고 Lesson을 생성하지 않는다")
        void createLesson_unitNotFound_doesNotCreateLesson() {
            // given
            UUID unitId = UUID.randomUUID();
            LessonCreateRequest request = mock(LessonCreateRequest.class);
            BusinessException exception = new BusinessException(ErrorCode.UNIT_NOT_FOUND);

            when(request.getUnitId()).thenReturn(unitId);
            when(unitFinder.findById(unitId)).thenThrow(exception);

            // when & then
            assertThatThrownBy(() -> lessonService.createLesson(request))
                    .isSameAs(exception)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.UNIT_NOT_FOUND));

            verify(request).getUnitId();
            verify(unitFinder).findById(unitId);
            verifyNoInteractions(lessonRepository, lessonFinder);
            verifyNoMoreInteractions(request, unitFinder);
        }

        @Test
        @DisplayName("Lesson 개수 조회 중 예외가 발생하면 저장하지 않고 그대로 전파한다")
        void createLesson_countThrowsException_doesNotSave() {
            // given
            UUID unitId = UUID.randomUUID();
            LessonCreateRequest request = mock(LessonCreateRequest.class);
            RuntimeException exception = new RuntimeException("count failure");

            when(request.getUnitId()).thenReturn(unitId);
            when(unitFinder.findById(unitId)).thenReturn(mock(Unit.class));
            when(lessonRepository.countByUnitId(unitId)).thenThrow(exception);

            // when & then
            assertThatThrownBy(() -> lessonService.createLesson(request)).isSameAs(exception);

            verify(request, times(2)).getUnitId();
            verify(unitFinder).findById(unitId);
            verify(lessonRepository).countByUnitId(unitId);
            verify(lessonRepository, never()).save(any(Lesson.class));
            verifyNoInteractions(lessonFinder);
            verifyNoMoreInteractions(request, unitFinder, lessonRepository);
        }

        @Test
        @DisplayName("Lesson 저장 중 예외가 발생하면 그대로 전파한다")
        void createLesson_saveThrowsException_propagatesException() {
            // given
            UUID unitId = UUID.randomUUID();
            LessonCreateRequest request = createRequest(
                    unitId,
                    "조건문",
                    "설명",
                    "내용",
                    ProgrammingLanguage.JAVA
            );

            RuntimeException exception = new RuntimeException("save failure");

            when(unitFinder.findById(unitId)).thenReturn(mock(Unit.class));
            when(lessonRepository.countByUnitId(unitId)).thenReturn(0L);
            when(lessonRepository.save(any(Lesson.class))).thenThrow(exception);

            // when & then
            assertThatThrownBy(() -> lessonService.createLesson(request)).isSameAs(exception);

            verify(unitFinder).findById(unitId);
            verify(lessonRepository).countByUnitId(unitId);
            verify(lessonRepository).save(any(Lesson.class));
            verifyNoInteractions(lessonFinder);
            verifyNoMoreInteractions(unitFinder, lessonRepository);
        }

        @Test
        @DisplayName("displayOrder가 int 범위를 초과하면 ArithmeticException이 발생하고 저장하지 않는다")
        void createLesson_displayOrderOverflowsInt_doesNotSave() {
            // given
            UUID unitId = UUID.randomUUID();
            LessonCreateRequest request = mock(LessonCreateRequest.class);

            when(request.getUnitId()).thenReturn(unitId);
            when(unitFinder.findById(unitId)).thenReturn(mock(Unit.class));
            when(lessonRepository.countByUnitId(unitId)).thenReturn((long) Integer.MAX_VALUE);

            // when & then
            assertThatThrownBy(() -> lessonService.createLesson(request))
                    .isInstanceOf(ArithmeticException.class);

            verify(request, times(2)).getUnitId();
            verify(unitFinder).findById(unitId);
            verify(lessonRepository).countByUnitId(unitId);
            verify(lessonRepository, never()).save(any(Lesson.class));
            verifyNoInteractions(lessonFinder);
            verifyNoMoreInteractions(request, unitFinder, lessonRepository);
        }
    }

    @Nested
    @DisplayName("getLesson")
    class GetLesson {

        @Test
        @DisplayName("존재하는 Lesson을 조회하면 LessonResponse를 반환한다")
        void getLesson_existingLesson_returnsResponse() {
            // given
            UUID lessonId = UUID.randomUUID();
            Lesson lesson = createLessonEntity(UUID.randomUUID());

            when(lessonFinder.findLessonById(lessonId)).thenReturn(lesson);

            // when
            LessonResponse result = lessonService.getLesson(lessonId);

            // then
            assertThat(result).isNotNull();

            verify(lessonFinder).findLessonById(lessonId);
            verifyNoInteractions(lessonRepository, unitFinder);
            verifyNoMoreInteractions(lessonFinder);
        }

        @Test
        @DisplayName("LessonFinder에서 예외가 발생하면 그대로 전파한다")
        void getLesson_finderThrowsException_propagatesException() {
            // given
            UUID lessonId = UUID.randomUUID();
            BusinessException exception = new BusinessException(ErrorCode.LESSON_NOT_FOUND);

            when(lessonFinder.findLessonById(lessonId)).thenThrow(exception);

            // when & then
            assertThatThrownBy(() -> lessonService.getLesson(lessonId)).isSameAs(exception);

            verify(lessonFinder).findLessonById(lessonId);
            verifyNoInteractions(lessonRepository, unitFinder);
            verifyNoMoreInteractions(lessonFinder);
        }
    }

    @Nested
    @DisplayName("searchLessons 성공")
    class SearchLessonsSuccess {

        @Test
        @DisplayName("Unit 존재 확인 후 동일한 unitId와 Pageable로 Lesson 목록을 조회한다")
        void searchLessons_validUnit_returnsPagedLessons() {
            // given
            UUID unitId = UUID.randomUUID();
            Pageable pageable = PageRequest.of(1, 2);

            Lesson first = Lesson.create(
                    unitId,
                    "조건문",
                    "조건문 설명",
                    "if",
                    ProgrammingLanguage.JAVA,
                    1
            );

            Lesson second = Lesson.create(
                    unitId,
                    "반복문",
                    "반복문 설명",
                    "for",
                    ProgrammingLanguage.JAVA,
                    2
            );

            Page<Lesson> page = new PageImpl<>(List.of(first, second), pageable, 5);

            when(unitFinder.findById(unitId)).thenReturn(mock(Unit.class));
            when(lessonRepository.searchLessons(unitId, pageable)).thenReturn(page);

            // when
            PageResponse<LessonResponse> result = lessonService.searchLessons(unitId, pageable);

            // then
            assertThat(result.content()).hasSize(2);
            assertThat(result.page()).isEqualTo(1);
            assertThat(result.size()).isEqualTo(2);
            assertThat(result.totalElements()).isEqualTo(5);
            assertThat(result.totalPages()).isEqualTo(3);
            assertThat(result.hasNext()).isTrue();

            verify(unitFinder).findById(unitId);
            verify(lessonRepository).searchLessons(unitId, pageable);
            verifyNoInteractions(lessonFinder);
            verifyNoMoreInteractions(unitFinder, lessonRepository);
        }

        @Test
        @DisplayName("빈 조회 결과는 빈 PageResponse로 반환한다")
        void searchLessons_emptyPage_returnsEmptyResponse() {
            // given
            UUID unitId = UUID.randomUUID();
            Pageable pageable = PageRequest.of(0, 20);
            Page<Lesson> page = new PageImpl<>(List.of(), pageable, 0);

            when(unitFinder.findById(unitId)).thenReturn(mock(Unit.class));
            when(lessonRepository.searchLessons(unitId, pageable)).thenReturn(page);

            // when
            PageResponse<LessonResponse> result = lessonService.searchLessons(unitId, pageable);

            // then
            assertThat(result.content()).isEmpty();
            assertThat(result.page()).isZero();
            assertThat(result.size()).isEqualTo(20);
            assertThat(result.totalElements()).isZero();
            assertThat(result.totalPages()).isZero();
            assertThat(result.hasNext()).isFalse();

            verify(unitFinder).findById(unitId);
            verify(lessonRepository).searchLessons(unitId, pageable);
        }

        @Test
        @DisplayName("Lesson 목록 조회는 Unit 검증 후 Repository 조회 순서로 수행한다")
        void searchLessons_checksUnitBeforeSearching() {
            // given
            UUID unitId = UUID.randomUUID();
            Pageable pageable = PageRequest.of(0, 20);

            when(unitFinder.findById(unitId)).thenReturn(mock(Unit.class));
            when(lessonRepository.searchLessons(unitId, pageable))
                    .thenReturn(new PageImpl<>(List.of(), pageable, 0));

            // when
            lessonService.searchLessons(unitId, pageable);

            // then
            InOrder inOrder = inOrder(unitFinder, lessonRepository);
            inOrder.verify(unitFinder).findById(unitId);
            inOrder.verify(lessonRepository).searchLessons(unitId, pageable);
            inOrder.verifyNoMoreInteractions();
        }
    }

    @Nested
    @DisplayName("searchLessons 실패")
    class SearchLessonsFailure {

        @Test
        @DisplayName("Unit이 존재하지 않으면 LessonRepository를 조회하지 않는다")
        void searchLessons_unitNotFound_doesNotSearchLessons() {
            // given
            UUID unitId = UUID.randomUUID();
            Pageable pageable = PageRequest.of(0, 20);
            BusinessException exception = new BusinessException(ErrorCode.UNIT_NOT_FOUND);

            when(unitFinder.findById(unitId)).thenThrow(exception);

            // when & then
            assertThatThrownBy(() -> lessonService.searchLessons(unitId, pageable))
                    .isSameAs(exception);

            verify(unitFinder).findById(unitId);
            verifyNoInteractions(lessonRepository, lessonFinder);
            verifyNoMoreInteractions(unitFinder);
        }

        @Test
        @DisplayName("LessonRepository 조회 중 예외가 발생하면 그대로 전파한다")
        void searchLessons_repositoryThrowsException_propagatesException() {
            // given
            UUID unitId = UUID.randomUUID();
            Pageable pageable = PageRequest.of(0, 20);
            RuntimeException exception = new RuntimeException("search failure");

            when(unitFinder.findById(unitId)).thenReturn(mock(Unit.class));
            when(lessonRepository.searchLessons(unitId, pageable)).thenThrow(exception);

            // when & then
            assertThatThrownBy(() -> lessonService.searchLessons(unitId, pageable))
                    .isSameAs(exception);

            verify(unitFinder).findById(unitId);
            verify(lessonRepository).searchLessons(unitId, pageable);
            verifyNoInteractions(lessonFinder);
        }
    }

    @Nested
    @DisplayName("updateLesson 성공")
    class UpdateLessonSuccess {

        @Test
        @DisplayName("모든 수정 필드가 존재하면 모든 변경 메서드를 호출한다")
        void updateLesson_allFieldsPresent_changesAllFields() {
            // given
            UUID lessonId = UUID.randomUUID();
            Lesson lesson = spy(createLessonEntity(UUID.randomUUID()));
            LessonUpdateRequest request = mock(LessonUpdateRequest.class);

            when(request.getTitle()).thenReturn("수정된 제목");
            when(request.getDescription()).thenReturn("수정된 설명");
            when(request.getContent()).thenReturn("수정된 내용");
            when(request.getLanguage()).thenReturn(ProgrammingLanguage.PYTHON);
            when(request.getDisplayOrder()).thenReturn(10);
            when(lessonFinder.findLessonById(lessonId)).thenReturn(lesson);

            // when
            LessonResponse result = lessonService.updateLesson(lessonId, request);

            // then
            assertThat(result).isNotNull();
            assertThat(lesson.getTitle()).isEqualTo("수정된 제목");
            assertThat(lesson.getDescription()).isEqualTo("수정된 설명");
            assertThat(lesson.getContent()).isEqualTo("수정된 내용");
            assertThat(lesson.getLanguage()).isEqualTo(ProgrammingLanguage.PYTHON);
            assertThat(lesson.getDisplayOrder()).isEqualTo(10);

            verify(lesson).changeTitle("수정된 제목");
            verify(lesson).changeDescription("수정된 설명");
            verify(lesson).changeContent("수정된 내용");
            verify(lesson).changeLanguage(ProgrammingLanguage.PYTHON);
            verify(lesson).changeDisplayOrder(10);

            verify(lessonFinder).findLessonById(lessonId);
            verifyNoInteractions(lessonRepository, unitFinder);
        }

        @Test
        @DisplayName("title만 존재하면 title만 수정한다")
        void updateLesson_onlyTitle_changesOnlyTitle() {
            // given
            UUID lessonId = UUID.randomUUID();
            Lesson lesson = spy(createLessonEntity(UUID.randomUUID()));
            LessonUpdateRequest request = mock(LessonUpdateRequest.class);

            when(request.getTitle()).thenReturn("새로운 제목");
            when(request.getDisplayOrder()).thenReturn(null);
            when(lessonFinder.findLessonById(lessonId)).thenReturn(lesson);

            // when
            lessonService.updateLesson(lessonId, request);

            // then
            assertThat(lesson.getTitle()).isEqualTo("새로운 제목");

            verify(lesson).changeTitle("새로운 제목");
            verify(lesson, never()).changeDescription(anyString());
            verify(lesson, never()).changeContent(anyString());
            verify(lesson, never()).changeLanguage(any());
            verify(lesson, never()).changeDisplayOrder(anyInt());

            verifyNoInteractions(lessonRepository, unitFinder);
        }

        @Test
        @DisplayName("description만 존재하면 description만 수정한다")
        void updateLesson_onlyDescription_changesOnlyDescription() {
            // given
            UUID lessonId = UUID.randomUUID();
            Lesson lesson = spy(createLessonEntity(UUID.randomUUID()));
            LessonUpdateRequest request = mock(LessonUpdateRequest.class);

            when(request.getDescription()).thenReturn("새로운 설명");
            when(request.getDisplayOrder()).thenReturn(null);
            when(lessonFinder.findLessonById(lessonId)).thenReturn(lesson);

            // when
            lessonService.updateLesson(lessonId, request);

            // then
            assertThat(lesson.getDescription()).isEqualTo("새로운 설명");

            verify(lesson).changeDescription("새로운 설명");
            verify(lesson, never()).changeTitle(anyString());
            verify(lesson, never()).changeContent(anyString());
            verify(lesson, never()).changeLanguage(any());
            verify(lesson, never()).changeDisplayOrder(anyInt());
        }

        @Test
        @DisplayName("content만 존재하면 content만 수정한다")
        void updateLesson_onlyContent_changesOnlyContent() {
            // given
            UUID lessonId = UUID.randomUUID();
            Lesson lesson = spy(createLessonEntity(UUID.randomUUID()));
            LessonUpdateRequest request = mock(LessonUpdateRequest.class);

            when(request.getContent()).thenReturn("새로운 내용");
            when(request.getDisplayOrder()).thenReturn(null);
            when(lessonFinder.findLessonById(lessonId)).thenReturn(lesson);

            // when
            lessonService.updateLesson(lessonId, request);

            // then
            assertThat(lesson.getContent()).isEqualTo("새로운 내용");

            verify(lesson).changeContent("새로운 내용");
            verify(lesson, never()).changeTitle(anyString());
            verify(lesson, never()).changeDescription(anyString());
            verify(lesson, never()).changeLanguage(any());
            verify(lesson, never()).changeDisplayOrder(anyInt());
        }

        @Test
        @DisplayName("language만 존재하면 language만 수정한다")
        void updateLesson_onlyLanguage_changesOnlyLanguage() {
            // given
            UUID lessonId = UUID.randomUUID();
            Lesson lesson = spy(createLessonEntity(UUID.randomUUID()));
            LessonUpdateRequest request = mock(LessonUpdateRequest.class);

            when(request.getLanguage()).thenReturn(ProgrammingLanguage.PYTHON);
            when(request.getDisplayOrder()).thenReturn(null);
            when(lessonFinder.findLessonById(lessonId)).thenReturn(lesson);

            // when
            lessonService.updateLesson(lessonId, request);

            // then
            assertThat(lesson.getLanguage()).isEqualTo(ProgrammingLanguage.PYTHON);

            verify(lesson).changeLanguage(ProgrammingLanguage.PYTHON);
            verify(lesson, never()).changeTitle(anyString());
            verify(lesson, never()).changeDescription(anyString());
            verify(lesson, never()).changeContent(anyString());
            verify(lesson, never()).changeDisplayOrder(anyInt());
        }

        @Test
        @DisplayName("displayOrder만 존재하면 displayOrder만 수정한다")
        void updateLesson_onlyDisplayOrder_changesOnlyDisplayOrder() {
            // given
            UUID lessonId = UUID.randomUUID();
            Lesson lesson = spy(createLessonEntity(UUID.randomUUID()));
            LessonUpdateRequest request = mock(LessonUpdateRequest.class);

            when(request.getDisplayOrder()).thenReturn(5);
            when(lessonFinder.findLessonById(lessonId)).thenReturn(lesson);

            // when
            lessonService.updateLesson(lessonId, request);

            // then
            assertThat(lesson.getDisplayOrder()).isEqualTo(5);

            verify(lesson).changeDisplayOrder(5);
            verify(lesson, never()).changeTitle(anyString());
            verify(lesson, never()).changeDescription(anyString());
            verify(lesson, never()).changeContent(anyString());
            verify(lesson, never()).changeLanguage(any());
        }

        @Test
        @DisplayName("모든 수정 필드가 null이면 변경 메서드를 호출하지 않는다")
        void updateLesson_allFieldsNull_doesNotChangeLesson() {
            // given
            UUID lessonId = UUID.randomUUID();
            Lesson lesson = spy(createLessonEntity(UUID.randomUUID()));
            LessonUpdateRequest request = mock(LessonUpdateRequest.class);

            when(request.getDisplayOrder()).thenReturn(null);
            when(lessonFinder.findLessonById(lessonId)).thenReturn(lesson);

            // when
            LessonResponse result = lessonService.updateLesson(lessonId, request);

            // then
            assertThat(result).isNotNull();

            verify(lesson, never()).changeTitle(anyString());
            verify(lesson, never()).changeDescription(anyString());
            verify(lesson, never()).changeContent(anyString());
            verify(lesson, never()).changeLanguage(any());
            verify(lesson, never()).changeDisplayOrder(anyInt());

            verify(lessonFinder).findLessonById(lessonId);
            verifyNoInteractions(lessonRepository, unitFinder);
        }
    }

    @Nested
    @DisplayName("updateLesson 실패")
    class UpdateLessonFailure {

        @Test
        @DisplayName("Lesson 조회에 실패하면 수정 로직을 수행하지 않고 예외를 그대로 전파한다")
        void updateLesson_finderThrowsException_propagatesException() {
            // given
            UUID lessonId = UUID.randomUUID();
            LessonUpdateRequest request = mock(LessonUpdateRequest.class);
            BusinessException exception = new BusinessException(ErrorCode.LESSON_NOT_FOUND);

            when(lessonFinder.findLessonById(lessonId)).thenThrow(exception);

            // when & then
            assertThatThrownBy(() -> lessonService.updateLesson(lessonId, request))
                    .isSameAs(exception);

            verify(lessonFinder).findLessonById(lessonId);
            verifyNoInteractions(request, lessonRepository, unitFinder);
        }
    }

    @Nested
    @DisplayName("deleteLesson")
    class DeleteLesson {

        @Test
        @DisplayName("Lesson을 조회한 뒤 전달받은 userId로 softDelete한다")
        void deleteLesson_existingLesson_softDeletesLesson() {
            // given
            UUID lessonId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            Lesson lesson = spy(createLessonEntity(UUID.randomUUID()));

            when(lessonFinder.findLessonById(lessonId)).thenReturn(lesson);

            // when
            lessonService.deleteLesson(lessonId, userId);

            // then
            verify(lessonFinder).findLessonById(lessonId);
            verify(lesson).softDelete(userId);

            assertThat(lesson.isDeleted()).isTrue();
            assertThat(lesson.getDeletedBy()).isEqualTo(userId);

            verifyNoInteractions(lessonRepository, unitFinder);
        }

        @Test
        @DisplayName("Lesson 조회에 실패하면 삭제하지 않고 예외를 그대로 전파한다")
        void deleteLesson_lessonNotFound_propagatesException() {
            // given
            UUID lessonId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            BusinessException exception = new BusinessException(ErrorCode.LESSON_NOT_FOUND);

            when(lessonFinder.findLessonById(lessonId)).thenThrow(exception);

            // when & then
            assertThatThrownBy(() -> lessonService.deleteLesson(lessonId, userId))
                    .isSameAs(exception);

            verify(lessonFinder).findLessonById(lessonId);
            verifyNoInteractions(lessonRepository, unitFinder);
        }
    }

    private LessonCreateRequest createRequest(
            UUID unitId,
            String title,
            String description,
            String content,
            ProgrammingLanguage language
    ) {
        LessonCreateRequest request = mock(LessonCreateRequest.class);

        when(request.getUnitId()).thenReturn(unitId);
        when(request.getTitle()).thenReturn(title);
        when(request.getDescription()).thenReturn(description);
        when(request.getContent()).thenReturn(content);
        when(request.getLanguage()).thenReturn(language);

        return request;
    }

    private Lesson createLessonEntity(UUID unitId) {
        return Lesson.create(
                unitId,
                "기존 제목",
                "기존 설명",
                "기존 내용",
                ProgrammingLanguage.JAVA,
                1
        );
    }
}