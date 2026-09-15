package com.maesamco.content.application.service;

import com.maesamco.content.application.input_port.CurriculumFinder;
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
import org.junit.jupiter.api.Nested;
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
@DisplayName("CurriculumService 단위 테스트")
class CurriculumServiceTest {

    @Mock
    private CurriculumRepository curriculumRepository;

    @Mock
    private CurriculumFinder curriculumFinder;

    private CurriculumService curriculumService;

    @BeforeEach
    void setUp() {
        curriculumService = new CurriculumService(curriculumRepository, curriculumFinder);
    }

    // ============================================================
    // 1. createCurriculum
    // ============================================================

    @Nested
    @DisplayName("createCurriculum")
    class CreateCurriculum {

        @Test
        @DisplayName("커리큘럼 생성 시 현재 개수 + 1을 displayOrder로 지정하여 저장한다")
        void createCurriculum_success() {

            // given
            CurriculumCreateRequest request = mock(CurriculumCreateRequest.class);
            Curriculum savedCurriculum = mock(Curriculum.class);

            when(request.getTitle()).thenReturn("Java 기초");
            when(request.getLanguage()).thenReturn(ProgrammingLanguage.JAVA);
            when(curriculumRepository.count()).thenReturn(3L);
            when(curriculumRepository.save(any(Curriculum.class))).thenReturn(savedCurriculum);

            // when
            CurriculumCreateResponse result = curriculumService.createCurriculum(request);

            // then
            ArgumentCaptor<Curriculum> captor = ArgumentCaptor.forClass(Curriculum.class);
            verify(curriculumRepository).count();
            verify(curriculumRepository).save(captor.capture());

            Curriculum curriculum = captor.getValue();

            assertThat(curriculum.getTitle()).isEqualTo("Java 기초");
            assertThat(curriculum.getLanguage()).isEqualTo(ProgrammingLanguage.JAVA);
            assertThat(curriculum.getDisplayOrder()).isEqualTo(4);
            assertThat(result).isNotNull();

            verifyNoInteractions(curriculumFinder);
            verifyNoMoreInteractions(curriculumRepository);
        }

        @Test
        @DisplayName("첫 번째 커리큘럼을 생성하면 displayOrder는 1이다")
        void createCurriculum_firstCurriculum_displayOrderIsOne() {

            // given
            CurriculumCreateRequest request = mock(CurriculumCreateRequest.class);
            Curriculum savedCurriculum = mock(Curriculum.class);

            when(request.getTitle()).thenReturn("첫 커리큘럼");
            when(request.getLanguage()).thenReturn(ProgrammingLanguage.JAVA);
            when(curriculumRepository.count()).thenReturn(0L);
            when(curriculumRepository.save(any(Curriculum.class))).thenReturn(savedCurriculum);

            // when
            curriculumService.createCurriculum(request);

            // then
            ArgumentCaptor<Curriculum> captor = ArgumentCaptor.forClass(Curriculum.class);
            verify(curriculumRepository).save(captor.capture());

            assertThat(captor.getValue().getDisplayOrder()).isEqualTo(1);

            verify(curriculumRepository).count();
            verifyNoInteractions(curriculumFinder);
            verifyNoMoreInteractions(curriculumRepository);
        }

        @Test
        @DisplayName("커리큘럼 생성 요청의 title과 language를 그대로 엔티티에 반영한다")
        void createCurriculum_usesRequestValues() {

            // given
            CurriculumCreateRequest request = mock(CurriculumCreateRequest.class);
            Curriculum savedCurriculum = mock(Curriculum.class);

            when(request.getTitle()).thenReturn("C++ 알고리즘");
            when(request.getLanguage()).thenReturn(ProgrammingLanguage.CPP);
            when(curriculumRepository.count()).thenReturn(7L);
            when(curriculumRepository.save(any(Curriculum.class))).thenReturn(savedCurriculum);

            // when
            curriculumService.createCurriculum(request);

            // then
            ArgumentCaptor<Curriculum> captor = ArgumentCaptor.forClass(Curriculum.class);
            verify(curriculumRepository).save(captor.capture());

            Curriculum curriculum = captor.getValue();

            assertThat(curriculum.getTitle()).isEqualTo("C++ 알고리즘");
            assertThat(curriculum.getLanguage()).isEqualTo(ProgrammingLanguage.CPP);
            assertThat(curriculum.getDisplayOrder()).isEqualTo(8);
        }
    }

    // ============================================================
    // 2. getCurriculum
    // Finder의 성공/실패 정책은 CurriculumFinderServiceTest에서 검증
    // ============================================================

    @Nested
    @DisplayName("getCurriculum")
    class GetCurriculum {

        @Test
        @DisplayName("커리큘럼 단건 조회 시 CurriculumFinder를 통해 조회한다")
        void getCurriculum_success() {

            // given
            UUID curriculumId = UUID.randomUUID();
            Curriculum curriculum = mock(Curriculum.class);

            when(curriculumFinder.findById(curriculumId)).thenReturn(curriculum);

            // when
            CurriculumResponse result = curriculumService.getCurriculum(curriculumId);

            // then
            assertThat(result).isNotNull();

            verify(curriculumFinder).findById(curriculumId);
            verifyNoInteractions(curriculumRepository);
            verifyNoMoreInteractions(curriculumFinder);
        }
    }

    // ============================================================
    // 3. searchCurriculums
    // ============================================================

    @Nested
    @DisplayName("searchCurriculums")
    class SearchCurriculums {

        @Test
        @DisplayName("Repository 조회 결과를 PageResponse로 변환하여 반환한다")
        void searchCurriculums_success() {

            // given
            Pageable pageable = PageRequest.of(0, 10);

            Curriculum first = mock(Curriculum.class);
            Curriculum second = mock(Curriculum.class);

            Page<Curriculum> page = new PageImpl<>(List.of(first, second), pageable, 2);

            when(curriculumRepository.searchCurriculums(pageable)).thenReturn(page);

            // when
            PageResponse<CurriculumResponse> result = curriculumService.searchCurriculums(pageable);

            // then
            assertThat(result).isNotNull();

            verify(curriculumRepository).searchCurriculums(pageable);
            verifyNoInteractions(curriculumFinder);
            verifyNoMoreInteractions(curriculumRepository);
        }

        @Test
        @DisplayName("조회 결과가 비어 있어도 정상적으로 빈 PageResponse를 반환한다")
        void searchCurriculums_emptyPage_success() {

            // given
            Pageable pageable = PageRequest.of(0, 10);
            Page<Curriculum> emptyPage = new PageImpl<>(List.of(), pageable, 0);

            when(curriculumRepository.searchCurriculums(pageable)).thenReturn(emptyPage);

            // when
            PageResponse<CurriculumResponse> result = curriculumService.searchCurriculums(pageable);

            // then
            assertThat(result).isNotNull();

            verify(curriculumRepository).searchCurriculums(pageable);
            verifyNoInteractions(curriculumFinder);
            verifyNoMoreInteractions(curriculumRepository);
        }

        @Test
        @DisplayName("전달받은 Pageable을 변경하지 않고 Repository에 전달한다")
        void searchCurriculums_passesExactPageable() {

            // given
            Pageable pageable = PageRequest.of(2, 20);
            Page<Curriculum> page = new PageImpl<>(List.of(), pageable, 0);

            when(curriculumRepository.searchCurriculums(pageable)).thenReturn(page);

            // when
            curriculumService.searchCurriculums(pageable);

            // then
            verify(curriculumRepository).searchCurriculums(same(pageable));
            verifyNoMoreInteractions(curriculumRepository);
            verifyNoInteractions(curriculumFinder);
        }
    }

    // ============================================================
    // 4. updateCurriculum
    // Finder의 NOT_FOUND 검증은 하지 않음
    // ============================================================

    @Nested
    @DisplayName("updateCurriculum")
    class UpdateCurriculum {

        @Test
        @DisplayName("language와 title이 모두 전달되면 두 값을 모두 수정한다")
        void updateCurriculum_languageAndTitle_success() {

            // given
            UUID curriculumId = UUID.randomUUID();
            Curriculum curriculum = mock(Curriculum.class);
            CurriculumUpdateRequest request = mock(CurriculumUpdateRequest.class);

            when(curriculumFinder.findById(curriculumId)).thenReturn(curriculum);
            when(request.getLanguage()).thenReturn(ProgrammingLanguage.PYTHON);
            when(request.getTitle()).thenReturn("Python 심화");

            // when
            CurriculumResponse result = curriculumService.updateCurriculum(curriculumId, request);

            // then
            assertThat(result).isNotNull();

            verify(curriculumFinder).findById(curriculumId);
            verify(curriculum).changeLanguage(ProgrammingLanguage.PYTHON);
            verify(curriculum).changeTitle("Python 심화");

            verifyNoInteractions(curriculumRepository);
            verifyNoMoreInteractions(curriculumFinder);
        }

        @Test
        @DisplayName("language만 전달되면 language만 수정한다")
        void updateCurriculum_onlyLanguage_changesLanguageOnly() {

            // given
            UUID curriculumId = UUID.randomUUID();
            Curriculum curriculum = mock(Curriculum.class);
            CurriculumUpdateRequest request = mock(CurriculumUpdateRequest.class);

            when(curriculumFinder.findById(curriculumId)).thenReturn(curriculum);
            when(request.getLanguage()).thenReturn(ProgrammingLanguage.PYTHON);
            when(request.getTitle()).thenReturn(null);

            // when
            curriculumService.updateCurriculum(curriculumId, request);

            // then
            verify(curriculumFinder).findById(curriculumId);
            verify(curriculum).changeLanguage(ProgrammingLanguage.PYTHON);
            verify(curriculum, never()).changeTitle(anyString());

            verifyNoInteractions(curriculumRepository);
        }

        @Test
        @DisplayName("title만 전달되면 title만 수정한다")
        void updateCurriculum_onlyTitle_changesTitleOnly() {

            // given
            UUID curriculumId = UUID.randomUUID();
            Curriculum curriculum = mock(Curriculum.class);
            CurriculumUpdateRequest request = mock(CurriculumUpdateRequest.class);

            when(curriculumFinder.findById(curriculumId)).thenReturn(curriculum);
            when(request.getLanguage()).thenReturn(null);
            when(request.getTitle()).thenReturn("변경된 제목");

            // when
            curriculumService.updateCurriculum(curriculumId, request);

            // then
            verify(curriculumFinder).findById(curriculumId);
            verify(curriculum, never()).changeLanguage(any());
            verify(curriculum).changeTitle("변경된 제목");

            verifyNoInteractions(curriculumRepository);
        }

        @Test
        @DisplayName("language와 title이 모두 null이면 엔티티의 값을 변경하지 않는다")
        void updateCurriculum_allFieldsNull_doesNotChangeCurriculum() {

            // given
            UUID curriculumId = UUID.randomUUID();
            Curriculum curriculum = mock(Curriculum.class);
            CurriculumUpdateRequest request = mock(CurriculumUpdateRequest.class);

            when(curriculumFinder.findById(curriculumId)).thenReturn(curriculum);
            when(request.getLanguage()).thenReturn(null);
            when(request.getTitle()).thenReturn(null);

            // when
            curriculumService.updateCurriculum(curriculumId, request);

            // then
            verify(curriculumFinder).findById(curriculumId);
            verify(curriculum, never()).changeLanguage(any());
            verify(curriculum, never()).changeTitle(anyString());

            verifyNoInteractions(curriculumRepository);
        }

        @Test
        @DisplayName("수정 시 CurriculumRepository.save를 명시적으로 호출하지 않는다")
        void updateCurriculum_doesNotCallSave() {

            // given
            UUID curriculumId = UUID.randomUUID();
            Curriculum curriculum = mock(Curriculum.class);
            CurriculumUpdateRequest request = mock(CurriculumUpdateRequest.class);

            when(curriculumFinder.findById(curriculumId)).thenReturn(curriculum);
            when(request.getTitle()).thenReturn("수정된 제목");

            // when
            curriculumService.updateCurriculum(curriculumId, request);

            // then
            verify(curriculum).changeTitle("수정된 제목");
            verifyNoInteractions(curriculumRepository);
        }
    }

    // ============================================================
    // 5. deleteCurriculum
    // Finder의 NOT_FOUND 검증은 하지 않음
    // ============================================================

    @Nested
    @DisplayName("deleteCurriculum")
    class DeleteCurriculum {

        @Test
        @DisplayName("커리큘럼 삭제 시 조회한 Curriculum을 요청한 userId로 softDelete한다")
        void deleteCurriculum_success() {

            // given
            UUID curriculumId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            Curriculum curriculum = mock(Curriculum.class);

            when(curriculumFinder.findById(curriculumId)).thenReturn(curriculum);

            // when
            curriculumService.deleteCurriculum(curriculumId, userId);

            // then
            verify(curriculumFinder).findById(curriculumId);
            verify(curriculum).softDelete(userId);

            verifyNoInteractions(curriculumRepository);
            verifyNoMoreInteractions(curriculumFinder);
        }

        @Test
        @DisplayName("삭제 시 CurriculumRepository.delete나 save를 직접 호출하지 않는다")
        void deleteCurriculum_doesNotCallRepositoryModification() {

            // given
            UUID curriculumId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            Curriculum curriculum = mock(Curriculum.class);

            when(curriculumFinder.findById(curriculumId)).thenReturn(curriculum);

            // when
            curriculumService.deleteCurriculum(curriculumId, userId);

            // then
            verify(curriculum).softDelete(userId);
            verifyNoInteractions(curriculumRepository);
        }
    }
}