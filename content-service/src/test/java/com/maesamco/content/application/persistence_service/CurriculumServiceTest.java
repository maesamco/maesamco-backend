package com.maesamco.content.application.persistence_service;

import com.maesamco.content.application.finder.CurriculumFinder;
import com.maesamco.content.domain.entity.Curriculum;
import com.maesamco.content.domain.entity.ProgrammingLanguage;
import com.maesamco.content.domain.repository.CurriculumRepository;
import com.maesamco.content.global.response.PageResponse;
import com.maesamco.content.presentation.request.CurriculumCreateRequest;
import com.maesamco.content.presentation.request.CurriculumUpdateRequest;
import com.maesamco.content.presentation.response.CurriculumCreateResponse;
import com.maesamco.content.presentation.response.CurriculumResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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
        CurriculumCreateRequest request =
                mock(CurriculumCreateRequest.class);

        Curriculum savedCurriculum =
                mock(Curriculum.class);

        when(request.getTitle())
                .thenReturn("Java Basic");

        when(request.getLanguage())
                .thenReturn(ProgrammingLanguage.JAVA);

        when(curriculumRepository.save(any(Curriculum.class)))
                .thenReturn(savedCurriculum);

        CurriculumCreateResponse result =
                curriculumService.createCurriculum(request);

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

        CurriculumResponse result =
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
        Pageable pageable =
                PageRequest.of(0, 10);

        Page<Curriculum> page =
                new PageImpl<>(
                        List.of(),
                        pageable,
                        0
                );

        when(curriculumRepository.searchCurriculums(pageable))
                .thenReturn(page);

        PageResponse<CurriculumResponse> result =
                curriculumService.searchCurriculums(pageable);

        assertThat(result)
                .isNotNull();

        verify(curriculumRepository)
                .searchCurriculums(pageable);

        verifyNoInteractions(curriculumFinder);
    }

    @Test
    @DisplayName("update curriculum")
    void updateCurriculum_success() {
        UUID curriculumId =
                UUID.randomUUID();

        Curriculum curriculum =
                mock(Curriculum.class);

        CurriculumUpdateRequest request =
                mock(CurriculumUpdateRequest.class);

        when(curriculumFinder.getById(curriculumId))
                .thenReturn(curriculum);

        when(request.getTitle())
                .thenReturn("Java Advanced");

        when(request.getLanguage())
                .thenReturn(ProgrammingLanguage.JAVA);

        CurriculumResponse result =
                curriculumService.updateCurriculum(
                        curriculumId,
                        request
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

        verifyNoInteractions(curriculumRepository);
    }
}