package com.maesamco.content.global.hierarchy;

import com.maesamco.content.application.lesson.service.LessonFinderService;
import com.maesamco.content.application.unit.service.UnitFinderService;
import com.maesamco.content.domain.curriculum.repository.CurriculumRepository;
import com.maesamco.content.domain.lesson.entity.Lesson;
import com.maesamco.content.domain.lesson.repository.LessonRepository;
import com.maesamco.content.domain.unit.entity.Unit;
import com.maesamco.content.domain.unit.repository.UnitRepository;
import com.maesamco.content.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HierarchyFinderVisibilityTest {

    @Mock
    private UnitRepository unitRepository;

    @Mock
    private LessonRepository lessonRepository;

    @Mock
    private CurriculumRepository curriculumRepository;

    @InjectMocks
    private UnitFinderService unitFinderService;

    @InjectMocks
    private LessonFinderService lessonFinderService;

    @Test
    @DisplayName("삭제된 Curriculum의 Unit은 단건 조회할 수 없다")
    void findUnit_deletedCurriculum_throwsNotFound() {

        // given
        UUID unitId = UUID.randomUUID();
        UUID curriculumId = UUID.randomUUID();

        Unit unit = org.mockito.Mockito.mock(Unit.class);

        when(unitRepository.findById(unitId))
                .thenReturn(Optional.of(unit));

        when(unit.getCurriculumId())
                .thenReturn(curriculumId);

        when(curriculumRepository.findById(curriculumId))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(
                () -> unitFinderService.findById(unitId)
        )
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("삭제된 Curriculum의 Lesson은 단건 조회할 수 없다")
    void findLesson_deletedCurriculum_throwsNotFound() {

        // given
        UUID lessonId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        UUID curriculumId = UUID.randomUUID();

        Lesson lesson = org.mockito.Mockito.mock(Lesson.class);
        Unit unit = org.mockito.Mockito.mock(Unit.class);

        when(lessonRepository.findById(lessonId))
                .thenReturn(Optional.of(lesson));

        when(lesson.getUnitId())
                .thenReturn(unitId);

        when(unitRepository.findById(unitId))
                .thenReturn(Optional.of(unit));

        when(unit.getCurriculumId())
                .thenReturn(curriculumId);

        when(curriculumRepository.findById(curriculumId))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(
                () -> lessonFinderService.findLessonById(lessonId)
        )
                .isInstanceOf(BusinessException.class);
    }
}
