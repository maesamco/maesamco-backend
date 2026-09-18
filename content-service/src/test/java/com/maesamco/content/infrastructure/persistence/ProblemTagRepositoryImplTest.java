package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.entity.Tag;
import com.maesamco.content.domain.entity.problem.ProblemTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProblemTagRepositoryImpl 테스트")
class ProblemTagRepositoryImplTest {

    @Mock
    private SpringDataProblemTagRepository springDataProblemTagRepository;

    @InjectMocks
    private ProblemTagRepositoryImpl problemTagRepository;

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("ProblemTag를 저장하고 Spring Data Repository가 반환한 엔티티를 반환한다")
        void save_success() {

            // given
            ProblemTag problemTag = mock(ProblemTag.class);
            ProblemTag savedProblemTag = mock(ProblemTag.class);

            when(springDataProblemTagRepository.save(problemTag))
                    .thenReturn(savedProblemTag);

            // when
            ProblemTag result = problemTagRepository.save(problemTag);

            // then
            assertThat(result)
                    .isSameAs(savedProblemTag);

            verify(springDataProblemTagRepository, times(1))
                    .save(problemTag);

            verifyNoMoreInteractions(springDataProblemTagRepository);
        }

        @Test
        @DisplayName("save 호출 시 전달받은 ProblemTag 인스턴스를 그대로 Spring Data Repository에 전달한다")
        void save_passesExactProblemTag() {

            // given
            ProblemTag problemTag = mock(ProblemTag.class);

            when(springDataProblemTagRepository.save(any(ProblemTag.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            ArgumentCaptor<ProblemTag> captor =
                    ArgumentCaptor.forClass(ProblemTag.class);

            // when
            ProblemTag result = problemTagRepository.save(problemTag);

            // then
            verify(springDataProblemTagRepository)
                    .save(captor.capture());

            assertThat(captor.getValue())
                    .isSameAs(problemTag);

            assertThat(result)
                    .isSameAs(problemTag);

            verifyNoMoreInteractions(springDataProblemTagRepository);
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("ProblemTag 삭제를 Spring Data Repository에 위임한다")
        void delete_success() {

            // given
            ProblemTag problemTag = mock(ProblemTag.class);

            // when
            problemTagRepository.delete(problemTag);

            // then
            verify(springDataProblemTagRepository, times(1))
                    .delete(problemTag);

            verifyNoMoreInteractions(springDataProblemTagRepository);
        }

        @Test
        @DisplayName("delete 호출 시 전달받은 ProblemTag 인스턴스를 그대로 전달한다")
        void delete_passesExactProblemTag() {

            // given
            ProblemTag problemTag = mock(ProblemTag.class);

            ArgumentCaptor<ProblemTag> captor =
                    ArgumentCaptor.forClass(ProblemTag.class);

            // when
            problemTagRepository.delete(problemTag);

            // then
            verify(springDataProblemTagRepository)
                    .delete(captor.capture());

            assertThat(captor.getValue())
                    .isSameAs(problemTag);

            verifyNoMoreInteractions(springDataProblemTagRepository);
        }
    }

    @Nested
    @DisplayName("existsByProblemIdAndTagId")
    class ExistsByProblemIdAndTagId {

        @Test
        @DisplayName("문제와 태그의 연결 관계가 존재하면 true를 반환한다")
        void existsByProblemIdAndTagId_exists_returnsTrue() {

            // given
            UUID problemId = UUID.randomUUID();
            UUID tagId = UUID.randomUUID();

            when(springDataProblemTagRepository
                    .existsByProblemIdAndTagId(problemId, tagId))
                    .thenReturn(true);

            // when
            boolean result =
                    problemTagRepository.existsByProblemIdAndTagId(
                            problemId,
                            tagId
                    );

            // then
            assertThat(result).isTrue();

            verify(springDataProblemTagRepository, times(1))
                    .existsByProblemIdAndTagId(problemId, tagId);

            verifyNoMoreInteractions(springDataProblemTagRepository);
        }

        @Test
        @DisplayName("문제와 태그의 연결 관계가 존재하지 않으면 false를 반환한다")
        void existsByProblemIdAndTagId_notExists_returnsFalse() {

            // given
            UUID problemId = UUID.randomUUID();
            UUID tagId = UUID.randomUUID();

            when(springDataProblemTagRepository
                    .existsByProblemIdAndTagId(problemId, tagId))
                    .thenReturn(false);

            // when
            boolean result =
                    problemTagRepository.existsByProblemIdAndTagId(
                            problemId,
                            tagId
                    );

            // then
            assertThat(result).isFalse();

            verify(springDataProblemTagRepository, times(1))
                    .existsByProblemIdAndTagId(problemId, tagId);

            verifyNoMoreInteractions(springDataProblemTagRepository);
        }

        @Test
        @DisplayName("problemId와 tagId를 순서대로 Spring Data Repository에 전달한다")
        void existsByProblemIdAndTagId_passesArgumentsCorrectly() {

            // given
            UUID problemId = UUID.randomUUID();
            UUID tagId = UUID.randomUUID();

            when(springDataProblemTagRepository
                    .existsByProblemIdAndTagId(any(UUID.class), any(UUID.class)))
                    .thenReturn(true);

            // when
            problemTagRepository.existsByProblemIdAndTagId(
                    problemId,
                    tagId
            );

            // then
            verify(springDataProblemTagRepository)
                    .existsByProblemIdAndTagId(problemId, tagId);

            verifyNoMoreInteractions(springDataProblemTagRepository);
        }
    }

    @Nested
    @DisplayName("findByProblemIdAndTagId")
    class FindByProblemIdAndTagId {

        @Test
        @DisplayName("문제와 태그의 연결 관계가 존재하면 ProblemTag를 반환한다")
        void findByProblemIdAndTagId_exists_returnsProblemTag() {

            // given
            UUID problemId = UUID.randomUUID();
            UUID tagId = UUID.randomUUID();

            ProblemTag problemTag = mock(ProblemTag.class);

            when(springDataProblemTagRepository
                    .findByProblemIdAndTagId(problemId, tagId))
                    .thenReturn(Optional.of(problemTag));

            // when
            Optional<ProblemTag> result =
                    problemTagRepository.findByProblemIdAndTagId(
                            problemId,
                            tagId
                    );

            // then
            assertThat(result)
                    .isPresent()
                    .containsSame(problemTag);

            verify(springDataProblemTagRepository, times(1))
                    .findByProblemIdAndTagId(problemId, tagId);

            verifyNoMoreInteractions(springDataProblemTagRepository);
        }

        @Test
        @DisplayName("문제와 태그의 연결 관계가 존재하지 않으면 Optional.empty를 반환한다")
        void findByProblemIdAndTagId_notExists_returnsEmpty() {

            // given
            UUID problemId = UUID.randomUUID();
            UUID tagId = UUID.randomUUID();

            when(springDataProblemTagRepository
                    .findByProblemIdAndTagId(problemId, tagId))
                    .thenReturn(Optional.empty());

            // when
            Optional<ProblemTag> result =
                    problemTagRepository.findByProblemIdAndTagId(
                            problemId,
                            tagId
                    );

            // then
            assertThat(result).isEmpty();

            verify(springDataProblemTagRepository, times(1))
                    .findByProblemIdAndTagId(problemId, tagId);

            verifyNoMoreInteractions(springDataProblemTagRepository);
        }

        @Test
        @DisplayName("Spring Data Repository가 반환한 Optional을 그대로 반환한다")
        void findByProblemIdAndTagId_returnsSameOptional() {

            // given
            UUID problemId = UUID.randomUUID();
            UUID tagId = UUID.randomUUID();

            ProblemTag problemTag = mock(ProblemTag.class);

            Optional<ProblemTag> expected =
                    Optional.of(problemTag);

            when(springDataProblemTagRepository
                    .findByProblemIdAndTagId(problemId, tagId))
                    .thenReturn(expected);

            // when
            Optional<ProblemTag> result =
                    problemTagRepository.findByProblemIdAndTagId(
                            problemId,
                            tagId
                    );

            // then
            assertThat(result)
                    .isSameAs(expected);

            verify(springDataProblemTagRepository)
                    .findByProblemIdAndTagId(problemId, tagId);

            verifyNoMoreInteractions(springDataProblemTagRepository);
        }
    }

    @Nested
    @DisplayName("findAllByProblemId")
    class FindAllByProblemId {

        @Test
        @DisplayName("문제에 연결된 모든 ProblemTag 목록을 반환한다")
        void findAllByProblemId_success() {

            // given
            UUID problemId = UUID.randomUUID();

            ProblemTag first = mock(ProblemTag.class);
            ProblemTag second = mock(ProblemTag.class);
            ProblemTag third = mock(ProblemTag.class);

            List<ProblemTag> expected =
                    List.of(first, second, third);

            when(springDataProblemTagRepository
                    .findAllByProblemId(problemId))
                    .thenReturn(expected);

            // when
            List<ProblemTag> result =
                    problemTagRepository.findAllByProblemId(problemId);

            // then
            assertThat(result)
                    .hasSize(3)
                    .containsExactly(first, second, third);

            assertThat(result)
                    .isSameAs(expected);

            verify(springDataProblemTagRepository, times(1))
                    .findAllByProblemId(problemId);

            verifyNoMoreInteractions(springDataProblemTagRepository);
        }

        @Test
        @DisplayName("문제에 연결된 태그가 없으면 빈 목록을 반환한다")
        void findAllByProblemId_empty_returnsEmptyList() {

            // given
            UUID problemId = UUID.randomUUID();

            when(springDataProblemTagRepository
                    .findAllByProblemId(problemId))
                    .thenReturn(List.of());

            // when
            List<ProblemTag> result =
                    problemTagRepository.findAllByProblemId(problemId);

            // then
            assertThat(result)
                    .isNotNull()
                    .isEmpty();

            verify(springDataProblemTagRepository, times(1))
                    .findAllByProblemId(problemId);

            verifyNoMoreInteractions(springDataProblemTagRepository);
        }

        @Test
        @DisplayName("findAllByProblemId는 전달받은 problemId를 그대로 사용한다")
        void findAllByProblemId_passesProblemId() {

            // given
            UUID problemId = UUID.randomUUID();

            when(springDataProblemTagRepository
                    .findAllByProblemId(any(UUID.class)))
                    .thenReturn(List.of());

            // when
            problemTagRepository.findAllByProblemId(problemId);

            // then
            verify(springDataProblemTagRepository)
                    .findAllByProblemId(problemId);

            verifyNoMoreInteractions(springDataProblemTagRepository);
        }
    }

    @Nested
    @DisplayName("deleteAllByTagId")
    class DeleteAllByTagId {

        @Test
        @DisplayName("특정 태그와 연결된 모든 ProblemTag 삭제를 Spring Data Repository에 위임한다")
        void deleteAllByTagId_success() {

            // given
            UUID tagId = UUID.randomUUID();

            // when
            problemTagRepository.deleteAllByTagId(tagId);

            // then
            verify(springDataProblemTagRepository, times(1))
                    .deleteAllByTagId(tagId);

            verifyNoMoreInteractions(springDataProblemTagRepository);
        }

        @Test
        @DisplayName("deleteAllByTagId는 전달받은 tagId를 그대로 사용한다")
        void deleteAllByTagId_passesTagId() {

            // given
            UUID tagId = UUID.randomUUID();

            ArgumentCaptor<UUID> captor =
                    ArgumentCaptor.forClass(UUID.class);

            // when
            problemTagRepository.deleteAllByTagId(tagId);

            // then
            verify(springDataProblemTagRepository)
                    .deleteAllByTagId(captor.capture());

            assertThat(captor.getValue())
                    .isEqualTo(tagId);

            verifyNoMoreInteractions(springDataProblemTagRepository);
        }
    }

    @Nested
    @DisplayName("searchTagsByProblemId")
    class SearchTagsByProblemId {

        @Test
        @DisplayName("문제에 연결된 태그를 페이지 단위로 조회한다")
        void searchTagsByProblemId_success() {

            // given
            UUID problemId = UUID.randomUUID();

            Pageable pageable =
                    PageRequest.of(
                            0,
                            20,
                            Sort.by(
                                    Sort.Direction.ASC,
                                    "name"
                            )
                    );

            Tag first = mock(Tag.class);
            Tag second = mock(Tag.class);
            Tag third = mock(Tag.class);

            List<Tag> tags =
                    List.of(first, second, third);

            Page<Tag> expected =
                    new PageImpl<>(
                            tags,
                            pageable,
                            3
                    );

            when(springDataProblemTagRepository
                    .findTagsByProblemId(problemId, pageable))
                    .thenReturn(expected);

            // when
            Page<Tag> result =
                    problemTagRepository.searchTagsByProblemId(
                            problemId,
                            pageable
                    );

            // then
            assertThat(result)
                    .isSameAs(expected);

            assertThat(result.getContent())
                    .containsExactly(first, second, third);

            assertThat(result.getNumber())
                    .isZero();

            assertThat(result.getSize())
                    .isEqualTo(20);

            assertThat(result.getTotalElements())
                    .isEqualTo(3);

            assertThat(result.getTotalPages())
                    .isEqualTo(1);

            verify(springDataProblemTagRepository, times(1))
                    .findTagsByProblemId(problemId, pageable);

            verifyNoMoreInteractions(springDataProblemTagRepository);
        }

        @Test
        @DisplayName("조회 결과가 없으면 빈 Page를 반환한다")
        void searchTagsByProblemId_empty_returnsEmptyPage() {

            // given
            UUID problemId = UUID.randomUUID();

            Pageable pageable =
                    PageRequest.of(0, 20);

            Page<Tag> expected =
                    Page.empty(pageable);

            when(springDataProblemTagRepository
                    .findTagsByProblemId(problemId, pageable))
                    .thenReturn(expected);

            // when
            Page<Tag> result =
                    problemTagRepository.searchTagsByProblemId(
                            problemId,
                            pageable
                    );

            // then
            assertThat(result)
                    .isNotNull()
                    .isEmpty();

            assertThat(result.getContent())
                    .isEmpty();

            assertThat(result.getTotalElements())
                    .isZero();

            verify(springDataProblemTagRepository, times(1))
                    .findTagsByProblemId(problemId, pageable);

            verifyNoMoreInteractions(springDataProblemTagRepository);
        }

        @Test
        @DisplayName("두 번째 페이지 요청도 Pageable을 변경하지 않고 그대로 전달한다")
        void searchTagsByProblemId_secondPage_passesPageableAsIs() {

            // given
            UUID problemId = UUID.randomUUID();

            Pageable pageable =
                    PageRequest.of(
                            1,
                            10,
                            Sort.by(
                                    Sort.Direction.DESC,
                                    "createdAt"
                            )
                    );

            Page<Tag> expected =
                    new PageImpl<>(
                            List.of(),
                            pageable,
                            10
                    );

            when(springDataProblemTagRepository
                    .findTagsByProblemId(problemId, pageable))
                    .thenReturn(expected);

            // when
            Page<Tag> result =
                    problemTagRepository.searchTagsByProblemId(
                            problemId,
                            pageable
                    );

            // then
            assertThat(result)
                    .isSameAs(expected);

            assertThat(result.getNumber())
                    .isEqualTo(1);

            assertThat(result.getSize())
                    .isEqualTo(10);

            verify(springDataProblemTagRepository)
                    .findTagsByProblemId(problemId, pageable);

            verifyNoMoreInteractions(springDataProblemTagRepository);
        }

        @Test
        @DisplayName("problemId와 Pageable을 정확하게 Spring Data Repository에 전달한다")
        void searchTagsByProblemId_passesArgumentsCorrectly() {

            // given
            UUID problemId = UUID.randomUUID();

            Pageable pageable =
                    PageRequest.of(2, 5);

            when(springDataProblemTagRepository
                    .findTagsByProblemId(problemId, pageable))
                    .thenReturn(Page.empty(pageable));

            // when
            problemTagRepository.searchTagsByProblemId(
                    problemId,
                    pageable
            );

            // then
            verify(springDataProblemTagRepository)
                    .findTagsByProblemId(problemId, pageable);

            verifyNoMoreInteractions(springDataProblemTagRepository);
        }

        @Test
        @DisplayName("Spring Data Repository가 반환한 Page 객체를 별도 변환 없이 그대로 반환한다")
        void searchTagsByProblemId_returnsSamePage() {

            // given
            UUID problemId = UUID.randomUUID();

            Pageable pageable =
                    PageRequest.of(0, 10);

            Page<Tag> expected = mock(Page.class);

            when(springDataProblemTagRepository
                    .findTagsByProblemId(problemId, pageable))
                    .thenReturn(expected);

            // when
            Page<Tag> result =
                    problemTagRepository.searchTagsByProblemId(
                            problemId,
                            pageable
                    );

            // then
            assertThat(result)
                    .isSameAs(expected);

            verify(springDataProblemTagRepository)
                    .findTagsByProblemId(problemId, pageable);

            verifyNoMoreInteractions(springDataProblemTagRepository);
        }
    }

    @Nested
    @DisplayName("findAllTagsByProblemId")
    class FindAllTagsByProblemId {

        @Test
        @DisplayName("문제에 연결된 모든 Tag 엔티티를 조회한다")
        void findAllTagsByProblemId_success() {

            // given
            UUID problemId = UUID.randomUUID();

            Tag first = mock(Tag.class);
            Tag second = mock(Tag.class);
            Tag third = mock(Tag.class);

            List<Tag> expected =
                    List.of(first, second, third);

            when(springDataProblemTagRepository
                    .findAllTagsByProblemId(problemId))
                    .thenReturn(expected);

            // when
            List<Tag> result =
                    problemTagRepository.findAllTagsByProblemId(
                            problemId
                    );

            // then
            assertThat(result)
                    .hasSize(3)
                    .containsExactly(
                            first,
                            second,
                            third
                    );

            assertThat(result)
                    .isSameAs(expected);

            verify(springDataProblemTagRepository, times(1))
                    .findAllTagsByProblemId(problemId);

            verifyNoMoreInteractions(springDataProblemTagRepository);
        }

        @Test
        @DisplayName("문제에 연결된 Tag가 없으면 빈 목록을 반환한다")
        void findAllTagsByProblemId_empty_returnsEmptyList() {

            // given
            UUID problemId = UUID.randomUUID();

            when(springDataProblemTagRepository
                    .findAllTagsByProblemId(problemId))
                    .thenReturn(List.of());

            // when
            List<Tag> result =
                    problemTagRepository.findAllTagsByProblemId(
                            problemId
                    );

            // then
            assertThat(result)
                    .isNotNull()
                    .isEmpty();

            verify(springDataProblemTagRepository, times(1))
                    .findAllTagsByProblemId(problemId);

            verifyNoMoreInteractions(springDataProblemTagRepository);
        }

        @Test
        @DisplayName("Spring Data Repository가 반환한 Tag 목록을 그대로 반환한다")
        void findAllTagsByProblemId_returnsSameList() {

            // given
            UUID problemId = UUID.randomUUID();

            Tag tag = mock(Tag.class);

            List<Tag> expected =
                    List.of(tag);

            when(springDataProblemTagRepository
                    .findAllTagsByProblemId(problemId))
                    .thenReturn(expected);

            // when
            List<Tag> result =
                    problemTagRepository.findAllTagsByProblemId(
                            problemId
                    );

            // then
            assertThat(result)
                    .isSameAs(expected);

            verify(springDataProblemTagRepository)
                    .findAllTagsByProblemId(problemId);

            verifyNoMoreInteractions(springDataProblemTagRepository);
        }

        @Test
        @DisplayName("findAllTagsByProblemId는 전달받은 problemId를 그대로 사용한다")
        void findAllTagsByProblemId_passesProblemId() {

            // given
            UUID problemId = UUID.randomUUID();

            when(springDataProblemTagRepository
                    .findAllTagsByProblemId(any(UUID.class)))
                    .thenReturn(List.of());

            // when
            problemTagRepository.findAllTagsByProblemId(problemId);

            // then
            verify(springDataProblemTagRepository)
                    .findAllTagsByProblemId(problemId);

            verifyNoMoreInteractions(springDataProblemTagRepository);
        }
    }
}