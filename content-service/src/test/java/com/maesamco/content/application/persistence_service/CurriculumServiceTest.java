package com.maesamco.content.application.persistence_service;

import com.maesamco.content.application.finder.CurriculumFinder;
import com.maesamco.content.domain.entity.Curriculum;
import com.maesamco.content.domain.entity.ProgrammingLanguage;
import com.maesamco.content.domain.repository.CurriculumRepository;
import com.maesamco.content.application.command.CurriculumCreateCommand;
import com.maesamco.content.application.command.CurriculumUpdateCommand;
import com.maesamco.content.application.result.CurriculumResult;
import com.maesamco.content.global.common.pagination.PageQuery;
import com.maesamco.content.global.common.pagination.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CurriculumService")
class CurriculumServiceTest {

    @Mock
    private CurriculumRepository curriculumRepository;

    @Mock
    private CurriculumFinder curriculumFinder;

    private CurriculumService curriculumService;

    @BeforeEach
    void setUp() {
        curriculumService =
                new CurriculumService(
                        curriculumRepository,
                        curriculumFinder
                );
    }

    @Test
    @DisplayName("create curriculum")
    void createCurriculum_success() {
        CurriculumCreateCommand command =
                new CurriculumCreateCommand(
                        "Java Basic",
                        ProgrammingLanguage.JAVA
                );

        Curriculum savedCurriculum =
                mock(Curriculum.class);

        when(curriculumRepository.save(any(Curriculum.class)))
                .thenReturn(savedCurriculum);

        CurriculumResult result =
                curriculumService.createCurriculum(command);

        ArgumentCaptor<Curriculum> captor =
                ArgumentCaptor.forClass(Curriculum.class);

        verify(curriculumRepository)
                .save(captor.capture());

        Curriculum curriculum =
                captor.getValue();

        assertThat(curriculum.getTitle())
                .isEqualTo("Java Basic");

        assertThat(curriculum.getLanguage())
                .isEqualTo(ProgrammingLanguage.JAVA);

        assertThat(result)
                .isNotNull();

        verifyNoInteractions(curriculumFinder);
        verifyNoMoreInteractions(curriculumRepository);
    }

    @Test
    @DisplayName("get curriculum")
    void getCurriculum_success() {
        UUID curriculumId =
                UUID.randomUUID();

        Curriculum curriculum =
                mock(Curriculum.class);

        when(curriculumFinder.getById(curriculumId))
                .thenReturn(curriculum);

        CurriculumResult result =
                curriculumService.getCurriculum(curriculumId);

        assertThat(result)
                .isNotNull();

        verify(curriculumFinder)
                .getById(curriculumId);

        verifyNoInteractions(curriculumRepository);
    }

    @Test
    @DisplayName("search curriculums")
    void searchCurriculums_success() {
        PageQuery pageQuery =
                PageQuery.of(0, 10);

        PageResult<Curriculum> page =
                new PageResult<>(
                        List.of(),
                        0,
                        10,
                        0
                );

        when(curriculumRepository.searchCurriculums(pageQuery))
                .thenReturn(page);

        PageResult<CurriculumResult> result =
                curriculumService.searchCurriculums(pageQuery);

        assertThat(result)
                .isNotNull();

        verify(curriculumRepository)
                .searchCurriculums(pageQuery);

        verifyNoInteractions(curriculumFinder);
    }

    @Test
    @DisplayName("update curriculum")
    void updateCurriculum_success() {
        UUID curriculumId =
                UUID.randomUUID();

        Curriculum curriculum =
                mock(Curriculum.class);

        CurriculumUpdateCommand command =
                new CurriculumUpdateCommand(
                        "Java Advanced",
                        ProgrammingLanguage.JAVA
                );

        when(curriculumFinder.getById(curriculumId))
                .thenReturn(curriculum);

        CurriculumResult result =
                curriculumService.updateCurriculum(
                        curriculumId,
                        command
                );

        assertThat(result)
                .isNotNull();

        verify(curriculum)
                .changeTitle("Java Advanced");

        verify(curriculum)
                .changeLanguage(ProgrammingLanguage.JAVA);

        verify(curriculumRepository, never())
                .save(any());
    }

    @Test
    @DisplayName("delete curriculum")
    void deleteCurriculum_success() {
        UUID curriculumId =
                UUID.randomUUID();

        UUID userId =
                UUID.randomUUID();

        Curriculum curriculum =
                mock(Curriculum.class);

        when(curriculumFinder.getById(curriculumId))
                .thenReturn(curriculum);

        curriculumService.deleteCurriculum(
                curriculumId,
                userId
        );

        verify(curriculumFinder)
                .getById(curriculumId);

        verify(curriculum)
                .softDelete(userId);

        verify(curriculumFinder).lockById(curriculumId);
        verify(curriculumRepository).refresh(curriculum);
    }
}