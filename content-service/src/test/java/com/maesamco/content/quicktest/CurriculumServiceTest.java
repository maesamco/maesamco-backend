package com.maesamco.content.quicktest;

import com.maesamco.content.curriculum.application.port.CurriculumFinder;
import com.maesamco.content.curriculum.application.service.CurriculumService;
import com.maesamco.content.curriculum.domain.entity.Curriculum;
import com.maesamco.content.curriculum.domain.enums.ProgrammingLanguage;
import com.maesamco.content.curriculum.domain.repository.CurriculumRepository;
import com.maesamco.content.curriculum.presentation.dto.request.CurriculumCreateRequest;
import com.maesamco.content.curriculum.presentation.dto.request.CurriculumUpdateRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CurriculumServiceTest {

    @Mock
    private CurriculumRepository curriculumRepository;

    @Mock
    private CurriculumFinder curriculumFinder;

    @InjectMocks
    private CurriculumService curriculumService;


    @Test
    @DisplayName("커리큘럼을 생성하면 기존 개수 다음 순서로 저장한다.")
    void createCurriculum_success() {

        // given
        CurriculumCreateRequest request = mock(CurriculumCreateRequest.class);

        when(request.getLanguage())
                .thenReturn(ProgrammingLanguage.JAVA);

        when(request.getTitle())
                .thenReturn("Java 기초");

        when(curriculumRepository.count())
                .thenReturn(2L);

        when(curriculumRepository.save(any(Curriculum.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        curriculumService.createCurriculum(request);

        // then
        ArgumentCaptor<Curriculum> captor =
                ArgumentCaptor.forClass(Curriculum.class);

        verify(curriculumRepository).save(captor.capture());

        Curriculum savedCurriculum = captor.getValue();

        assertThat(savedCurriculum.getLanguage())
                .isEqualTo(ProgrammingLanguage.JAVA);

        assertThat(savedCurriculum.getTitle())
                .isEqualTo("Java 기초");

        assertThat(savedCurriculum.getDisplayOrder())
                .isEqualTo(3);

        // 결과 출력
        System.out.println("===== 커리큘럼 생성 결과 =====");
        System.out.println("language = " + savedCurriculum.getLanguage());
        System.out.println("title = " + savedCurriculum.getTitle());
        System.out.println("displayOrder = " + savedCurriculum.getDisplayOrder());
    }


    @Test
    @DisplayName("커리큘럼 수정 시 전달된 값만 변경한다.")
    void updateCurriculum_success() {

        // given
        UUID curriculumId = UUID.randomUUID();

        Curriculum curriculum = mock(Curriculum.class);
        CurriculumUpdateRequest request = mock(CurriculumUpdateRequest.class);

        when(curriculumFinder.findById(curriculumId))
                .thenReturn(curriculum);

        when(request.getLanguage())
                .thenReturn(null);

        when(request.getTitle())
                .thenReturn("수정된 Java 기초");

        // when
        curriculumService.updateCurriculum(curriculumId, request);

        // then
        verify(curriculum)
                .changeTitle("수정된 Java 기초");

        verify(curriculum, never())
                .changeLanguage(any());

        // 결과 출력
        System.out.println("===== 커리큘럼 수정 결과 =====");
        System.out.println("curriculumId = " + curriculumId);
        System.out.println("수정 title = " + request.getTitle());
        System.out.println("language 변경 여부 = false");
    }


    @Test
    @DisplayName("커리큘럼 삭제 시 Soft Delete 처리한다.")
    void deleteCurriculum_success() {

        // given
        UUID curriculumId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Curriculum curriculum = mock(Curriculum.class);

        when(curriculumFinder.findById(curriculumId))
                .thenReturn(curriculum);

        // when
        curriculumService.deleteCurriculum(curriculumId, userId);

        // then
        verify(curriculumFinder)
                .findById(curriculumId);

        verify(curriculum)
                .softDelete(userId);

        // 결과 출력
        System.out.println("===== 커리큘럼 삭제 결과 =====");
        System.out.println("curriculumId = " + curriculumId);
        System.out.println("deletedBy = " + userId);
        System.out.println("softDelete 호출 여부 = true");
    }
}