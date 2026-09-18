package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.entity.Curriculum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CurriculumRepositoryImpl 테스트")
class CurriculumRepositoryImplTest {

    @Mock
    private SpringDataCurriculumRepository springDataCurriculumRepository;

    @InjectMocks
    private CurriculumRepositoryImpl curriculumRepository;

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("Curriculum을 저장하고 저장된 Curriculum을 반환한다")
        void save_success() {

            // given
            Curriculum curriculum = mock(Curriculum.class);
            Curriculum savedCurriculum = mock(Curriculum.class);

            when(springDataCurriculumRepository.save(curriculum))
                    .thenReturn(savedCurriculum);

            // when
            Curriculum result =
                    curriculumRepository.save(curriculum);

            // then
            assertThat(result)
                    .isSameAs(savedCurriculum);

            verify(springDataCurriculumRepository)
                    .save(curriculum);

            verifyNoMoreInteractions(
                    springDataCurriculumRepository
            );
        }
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("삭제되지 않은 Curriculum이 존재하면 해당 Curriculum을 반환한다")
        void findById_existingCurriculum_returnsCurriculum() {

            // given
            UUID curriculumId = UUID.randomUUID();
            Curriculum curriculum = mock(Curriculum.class);

            when(
                    springDataCurriculumRepository
                            .findByIdAndDeletedAtIsNull(curriculumId)
            ).thenReturn(
                    Optional.of(curriculum)
            );

            // when
            Optional<Curriculum> result =
                    curriculumRepository.findById(curriculumId);

            // then
            assertThat(result)
                    .isPresent();

            assertThat(result.get())
                    .isSameAs(curriculum);

            verify(springDataCurriculumRepository)
                    .findByIdAndDeletedAtIsNull(curriculumId);

            verifyNoMoreInteractions(
                    springDataCurriculumRepository
            );
        }

        @Test
        @DisplayName("삭제되지 않은 Curriculum이 존재하지 않으면 빈 Optional을 반환한다")
        void findById_nonExistingCurriculum_returnsEmpty() {

            // given
            UUID curriculumId = UUID.randomUUID();

            when(
                    springDataCurriculumRepository
                            .findByIdAndDeletedAtIsNull(curriculumId)
            ).thenReturn(
                    Optional.empty()
            );

            // when
            Optional<Curriculum> result =
                    curriculumRepository.findById(curriculumId);

            // then
            assertThat(result)
                    .isEmpty();

            verify(springDataCurriculumRepository)
                    .findByIdAndDeletedAtIsNull(curriculumId);

            verifyNoMoreInteractions(
                    springDataCurriculumRepository
            );
        }
    }

    @Nested
    @DisplayName("count")
    class Count {

        @Test
        @DisplayName("삭제되지 않은 Curriculum의 개수를 반환한다")
        void count_returnsActiveCurriculumCount() {

            // given
            long expectedCount = 3L;

            when(
                    springDataCurriculumRepository
                            .countByDeletedAtIsNull()
            ).thenReturn(
                    expectedCount
            );

            // when
            long result =
                    curriculumRepository.count();

            // then
            assertThat(result)
                    .isEqualTo(expectedCount);

            verify(springDataCurriculumRepository)
                    .countByDeletedAtIsNull();

            verifyNoMoreInteractions(
                    springDataCurriculumRepository
            );
        }

        @Test
        @DisplayName("삭제되지 않은 Curriculum이 없으면 0을 반환한다")
        void count_noCurriculum_returnsZero() {

            // given
            when(
                    springDataCurriculumRepository
                            .countByDeletedAtIsNull()
            ).thenReturn(
                    0L
            );

            // when
            long result =
                    curriculumRepository.count();

            // then
            assertThat(result)
                    .isZero();

            verify(springDataCurriculumRepository)
                    .countByDeletedAtIsNull();

            verifyNoMoreInteractions(
                    springDataCurriculumRepository
            );
        }
    }

    @Nested
    @DisplayName("searchCurriculums")
    class SearchCurriculums {

        @Test
        @DisplayName("삭제되지 않은 Curriculum 목록을 displayOrder와 id 순으로 조회한다")
        void searchCurriculums_returnsCurriculumPage() {

            // given
            Pageable pageable =
                    PageRequest.of(
                            0,
                            20
                    );

            Curriculum curriculum1 = mock(Curriculum.class);
            Curriculum curriculum2 = mock(Curriculum.class);

            Page<Curriculum> expectedPage =
                    new PageImpl<>(
                            List.of(
                                    curriculum1,
                                    curriculum2
                            ),
                            pageable,
                            2
                    );

            when(
                    springDataCurriculumRepository
                            .findByDeletedAtIsNullOrderByDisplayOrderAscIdAsc(
                                    pageable
                            )
            ).thenReturn(
                    expectedPage
            );

            // when
            Page<Curriculum> result =
                    curriculumRepository.searchCurriculums(
                            pageable
                    );

            // then
            assertThat(result)
                    .isSameAs(expectedPage);

            assertThat(result.getContent())
                    .containsExactly(
                            curriculum1,
                            curriculum2
                    );

            assertThat(result.getTotalElements())
                    .isEqualTo(2);

            assertThat(result.getNumber())
                    .isZero();

            assertThat(result.getSize())
                    .isEqualTo(20);

            verify(springDataCurriculumRepository)
                    .findByDeletedAtIsNullOrderByDisplayOrderAscIdAsc(
                            pageable
                    );

            verifyNoMoreInteractions(
                    springDataCurriculumRepository
            );
        }

        @Test
        @DisplayName("조회되는 Curriculum이 없으면 빈 Page를 반환한다")
        void searchCurriculums_noCurriculum_returnsEmptyPage() {

            // given
            Pageable pageable =
                    PageRequest.of(
                            0,
                            20
                    );

            Page<Curriculum> emptyPage =
                    new PageImpl<>(
                            List.of(),
                            pageable,
                            0
                    );

            when(
                    springDataCurriculumRepository
                            .findByDeletedAtIsNullOrderByDisplayOrderAscIdAsc(
                                    pageable
                            )
            ).thenReturn(
                    emptyPage
            );

            // when
            Page<Curriculum> result =
                    curriculumRepository.searchCurriculums(
                            pageable
                    );

            // then
            assertThat(result)
                    .isEmpty();

            assertThat(result.getTotalElements())
                    .isZero();

            verify(springDataCurriculumRepository)
                    .findByDeletedAtIsNullOrderByDisplayOrderAscIdAsc(
                            pageable
                    );

            verifyNoMoreInteractions(
                    springDataCurriculumRepository
            );
        }

        @Test
        @DisplayName("전달받은 Pageable을 변경하지 않고 SpringDataCurriculumRepository에 전달한다")
        void searchCurriculums_passesSamePageable() {

            // given
            Pageable pageable =
                    PageRequest.of(
                            2,
                            10
                    );

            Page<Curriculum> expectedPage =
                    Page.empty(pageable);

            when(
                    springDataCurriculumRepository
                            .findByDeletedAtIsNullOrderByDisplayOrderAscIdAsc(
                                    pageable
                            )
            ).thenReturn(
                    expectedPage
            );

            // when
            curriculumRepository.searchCurriculums(
                    pageable
            );

            // then
            verify(springDataCurriculumRepository)
                    .findByDeletedAtIsNullOrderByDisplayOrderAscIdAsc(
                            same(pageable)
                    );

            verifyNoMoreInteractions(
                    springDataCurriculumRepository
            );
        }
    }
}