package com.maesamco.content.quicktest;

import com.maesamco.content.lesson.application.port.LessonFinder;
import com.maesamco.content.lesson.application.service.LessonService;
import com.maesamco.content.lesson.domain.entity.Lesson;
import com.maesamco.content.lesson.domain.enums.ProgrammingLanguage;
import com.maesamco.content.lesson.presentation.dto.request.LessonUpdateRequest;
import com.maesamco.content.lesson.presentation.dto.response.LessonResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LessonServiceTest {

    @Mock
    private LessonFinder lessonFinder;

    @InjectMocks
    private LessonService lessonService;


    @Test
    @DisplayName("레슨 단건 조회 시 레슨 정보를 반환한다.")
    void getLesson_success() {

        // given
        UUID lessonId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();

        Lesson lesson = mock(Lesson.class);

        when(lessonFinder.findLessonById(lessonId)).thenReturn(lesson);
        when(lesson.getId()).thenReturn(lessonId);
        when(lesson.getUnitId()).thenReturn(unitId);
        when(lesson.getTitle()).thenReturn("변수와 자료형");
        when(lesson.getDescription()).thenReturn("Java의 변수와 자료형을 학습합니다.");
        when(lesson.getContent()).thenReturn("int, long, double, boolean 등의 기본 자료형을 학습합니다.");
        when(lesson.getLanguage()).thenReturn(ProgrammingLanguage.JAVA);
        when(lesson.getDisplayOrder()).thenReturn(1);

        // when
        LessonResponse response = lessonService.getLesson(lessonId);

        // then
        assertThat(response.getId()).isEqualTo(lessonId);
        assertThat(response.getUnitId()).isEqualTo(unitId);
        assertThat(response.getTitle()).isEqualTo("변수와 자료형");
        assertThat(response.getDescription()).isEqualTo("Java의 변수와 자료형을 학습합니다.");
        assertThat(response.getContent()).isEqualTo("int, long, double, boolean 등의 기본 자료형을 학습합니다.");
        assertThat(response.getLanguage()).isEqualTo(ProgrammingLanguage.JAVA);
        assertThat(response.getDisplayOrder()).isEqualTo(1);

        System.out.println("===== 레슨 단건 조회 결과 =====");
        System.out.println("id = " + response.getId());
        System.out.println("unitId = " + response.getUnitId());
        System.out.println("title = " + response.getTitle());
        System.out.println("description = " + response.getDescription());
        System.out.println("content = " + response.getContent());
        System.out.println("language = " + response.getLanguage());
        System.out.println("displayOrder = " + response.getDisplayOrder());
    }


    @Test
    @DisplayName("레슨의 제목을 수정한다.")
    void updateLesson_success() {

        // given
        UUID lessonId = UUID.randomUUID();

        Lesson lesson = mock(Lesson.class);
        LessonUpdateRequest request = mock(LessonUpdateRequest.class);

        when(lessonFinder.findLessonById(lessonId)).thenReturn(lesson);
        when(request.getTitle()).thenReturn("수정된 변수와 자료형");

        // when
        lessonService.updateLesson(lessonId, request);

        // then
        verify(lesson).changeTitle("수정된 변수와 자료형");

        System.out.println("===== 레슨 수정 결과 =====");
        System.out.println("lessonId = " + lessonId);
        System.out.println("변경된 title = " + request.getTitle());
    }


    @Test
    @DisplayName("레슨을 삭제하면 Soft Delete 처리한다.")
    void deleteLesson_success() {

        // given
        UUID lessonId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Lesson lesson = mock(Lesson.class);

        when(lessonFinder.findLessonById(lessonId)).thenReturn(lesson);

        // when
        lessonService.deleteLesson(lessonId, userId);

        // then
        verify(lessonFinder).findLessonById(lessonId);
        verify(lesson).softDelete(userId);

        System.out.println("===== 레슨 삭제 결과 =====");
        System.out.println("lessonId = " + lessonId);
        System.out.println("deletedBy = " + userId);
        System.out.println("softDelete = true");
    }
}