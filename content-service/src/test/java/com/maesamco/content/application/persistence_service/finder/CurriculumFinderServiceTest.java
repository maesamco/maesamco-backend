package com.maesamco.content.application.persistence_service.finder;

import com.maesamco.content.application.finder_service.CurriculumFinderService;
import com.maesamco.content.domain.entity.Curriculum;
import com.maesamco.content.domain.repository.CurriculumRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CurriculumFinderService 단위 테스트")
class CurriculumFinderServiceTest {

    @Mock
    private CurriculumRepository curriculumRepository;

    private CurriculumFinderService curriculumFinderService;

    @BeforeEach
    void setUp() {
        curriculumFinderService = new CurriculumFinderService(curriculumRepository);
    }

    // ============================================================
    // 1. findById - 정상 조회
    // ============================================================

    @Nested
    @DisplayName("findById 성공")
    class FindByIdSuccess {

        @Test
        @DisplayName("존재하는 curriculumId로 조회하면 Repository에서 조회한 Curriculum을 그대로 반환한다")
        void findById_existingCurriculum_returnsCurriculum() {

            // given
            UUID curriculumId = UUID.randomUUID();
            Curriculum curriculum = mock(Curriculum.class);

            when(curriculumRepository.findById(curriculumId)).thenReturn(Optional.of(curriculum));

            // when
            Curriculum result = curriculumFinderService.getById(curriculumId);

            // then
            assertThat(result).isNotNull();
            assertThat(result).isSameAs(curriculum);

            verify(curriculumRepository).findById(curriculumId);
            verifyNoMoreInteractions(curriculumRepository);
        }

        @Test
        @DisplayName("서로 다른 curriculumId로 조회하면 각각 정확한 ID를 Repository에 전달한다")
        void findById_differentIds_queriesExactIds() {

            // given
            UUID firstCurriculumId = UUID.randomUUID();
            UUID secondCurriculumId = UUID.randomUUID();

            Curriculum firstCurriculum = mock(Curriculum.class);
            Curriculum secondCurriculum = mock(Curriculum.class);

            when(curriculumRepository.findById(firstCurriculumId)).thenReturn(Optional.of(firstCurriculum));
            when(curriculumRepository.findById(secondCurriculumId)).thenReturn(Optional.of(secondCurriculum));

            // when
            Curriculum firstResult = curriculumFinderService.getById(firstCurriculumId);
            Curriculum secondResult = curriculumFinderService.getById(secondCurriculumId);

            // then
            assertThat(firstResult).isSameAs(firstCurriculum);
            assertThat(secondResult).isSameAs(secondCurriculum);
            assertThat(firstResult).isNotSameAs(secondResult);

            verify(curriculumRepository).findById(firstCurriculumId);
            verify(curriculumRepository).findById(secondCurriculumId);
            verifyNoMoreInteractions(curriculumRepository);
        }
    }

    // ============================================================
    // 2. findById - 조회 실패
    // ============================================================

    @Nested
    @DisplayName("findById 실패")
    class FindByIdFailure {

        @Test
        @DisplayName("존재하지 않는 curriculumId로 조회하면 CURRICULUM_NOT_FOUND 예외가 발생한다")
        void findById_notExistingCurriculum_throwsCurriculumNotFound() {

            // given
            UUID curriculumId = UUID.randomUUID();

            when(curriculumRepository.findById(curriculumId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> curriculumFinderService.getById(curriculumId))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(exception -> {
                        BusinessException businessException = (BusinessException) exception;
                        assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.CURRICULUM_NOT_FOUND);
                    });

            verify(curriculumRepository).findById(curriculumId);
            verifyNoMoreInteractions(curriculumRepository);
        }

        @Test
        @DisplayName("존재하지 않는 Curriculum 조회 시 Repository를 한 번만 조회한다")
        void findById_notExistingCurriculum_queriesRepositoryOnlyOnce() {

            // given
            UUID curriculumId = UUID.randomUUID();

            when(curriculumRepository.findById(curriculumId)).thenReturn(Optional.empty());

            // when
            assertThatThrownBy(() -> curriculumFinderService.getById(curriculumId))
                    .isInstanceOf(BusinessException.class);

            // then
            verify(curriculumRepository, times(1)).findById(curriculumId);
            verifyNoMoreInteractions(curriculumRepository);
        }
    }

    // ============================================================
    // 3. Repository 예외 전파
    // ============================================================

    @Nested
    @DisplayName("Repository 예외 전파")
    class RepositoryExceptionPropagation {

        @Test
        @DisplayName("Repository에서 예상하지 못한 예외가 발생하면 CURRICULUM_NOT_FOUND로 변환하지 않고 그대로 전파한다")
        void findById_repositoryThrowsUnexpectedException_propagatesException() {

            // given
            UUID curriculumId = UUID.randomUUID();
            RuntimeException repositoryException = new RuntimeException("database access failure");

            when(curriculumRepository.findById(curriculumId)).thenThrow(repositoryException);

            // when & then
            assertThatThrownBy(() -> curriculumFinderService.getById(curriculumId))
                    .isSameAs(repositoryException);

            verify(curriculumRepository).findById(curriculumId);
            verifyNoMoreInteractions(curriculumRepository);
        }
    }
}