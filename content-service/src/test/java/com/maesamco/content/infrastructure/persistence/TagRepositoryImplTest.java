package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.entity.Tag;
import com.maesamco.content.domain.entity.TagAttribute;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.maesamco.content.global.common.pagination.PageQuery;
import com.maesamco.content.global.common.pagination.PageResult;
import com.maesamco.content.global.common.pagination.SortOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TagRepositoryImpl 테스트")
class TagRepositoryImplTest {

    @Mock
    private SpringDataTagRepository springDataTagRepository;

    @InjectMocks
    private TagRepositoryImpl tagRepository;

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("Tag를 저장하고 Spring Data Repository가 반환한 Tag를 반환한다")
        void save_success() {

            // given
            Tag tag = mock(Tag.class);
            Tag savedTag = mock(Tag.class);

            when(springDataTagRepository.save(tag))
                    .thenReturn(savedTag);

            // when
            Tag result = tagRepository.save(tag);

            // then
            assertThat(result)
                    .isSameAs(savedTag);

            verify(springDataTagRepository, times(1))
                    .save(tag);

            verifyNoMoreInteractions(springDataTagRepository);
        }

        @Test
        @DisplayName("save는 전달받은 Tag 인스턴스를 그대로 Spring Data Repository에 전달한다")
        void save_passesExactTag() {

            // given
            Tag tag = mock(Tag.class);

            when(springDataTagRepository.save(any(Tag.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            ArgumentCaptor<Tag> captor =
                    ArgumentCaptor.forClass(Tag.class);

            // when
            Tag result = tagRepository.save(tag);

            // then
            verify(springDataTagRepository)
                    .save(captor.capture());

            assertThat(captor.getValue())
                    .isSameAs(tag);

            assertThat(result)
                    .isSameAs(tag);

            verifyNoMoreInteractions(springDataTagRepository);
        }
    }

    @Nested
    @DisplayName("existsByName")
    class ExistsByName {

        @Test
        @DisplayName("동일한 이름의 태그가 존재하면 true를 반환한다")
        void existsByName_exists_returnsTrue() {

            // given
            String name = "Java";

            when(springDataTagRepository.existsByName(name))
                    .thenReturn(true);

            // when
            boolean result = tagRepository.existsByName(name);

            // then
            assertThat(result).isTrue();

            verify(springDataTagRepository, times(1))
                    .existsByName(name);

            verifyNoMoreInteractions(springDataTagRepository);
        }

        @Test
        @DisplayName("동일한 이름의 태그가 존재하지 않으면 false를 반환한다")
        void existsByName_notExists_returnsFalse() {

            // given
            String name = "Java";

            when(springDataTagRepository.existsByName(name))
                    .thenReturn(false);

            // when
            boolean result = tagRepository.existsByName(name);

            // then
            assertThat(result).isFalse();

            verify(springDataTagRepository, times(1))
                    .existsByName(name);

            verifyNoMoreInteractions(springDataTagRepository);
        }

        @Test
        @DisplayName("existsByName은 전달받은 태그 이름을 그대로 Spring Data Repository에 전달한다")
        void existsByName_passesName() {

            // given
            String name = "Spring";

            when(springDataTagRepository.existsByName(anyString()))
                    .thenReturn(false);

            ArgumentCaptor<String> captor =
                    ArgumentCaptor.forClass(String.class);

            // when
            tagRepository.existsByName(name);

            // then
            verify(springDataTagRepository)
                    .existsByName(captor.capture());

            assertThat(captor.getValue())
                    .isEqualTo(name);

            verifyNoMoreInteractions(springDataTagRepository);
        }
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("삭제되지 않은 태그가 존재하면 Tag를 반환한다")
        void findById_exists_returnsTag() {

            // given
            UUID tagId = UUID.randomUUID();
            Tag tag = mock(Tag.class);

            when(springDataTagRepository
                    .findByIdAndDeletedAtIsNull(tagId))
                    .thenReturn(Optional.of(tag));

            // when
            Optional<Tag> result =
                    tagRepository.findById(tagId);

            // then
            assertThat(result)
                    .isPresent()
                    .containsSame(tag);

            verify(springDataTagRepository, times(1))
                    .findByIdAndDeletedAtIsNull(tagId);

            verifyNoMoreInteractions(springDataTagRepository);
        }

        @Test
        @DisplayName("삭제되지 않은 태그가 존재하지 않으면 Optional.empty를 반환한다")
        void findById_notExists_returnsEmpty() {

            // given
            UUID tagId = UUID.randomUUID();

            when(springDataTagRepository
                    .findByIdAndDeletedAtIsNull(tagId))
                    .thenReturn(Optional.empty());

            // when
            Optional<Tag> result =
                    tagRepository.findById(tagId);

            // then
            assertThat(result).isEmpty();

            verify(springDataTagRepository, times(1))
                    .findByIdAndDeletedAtIsNull(tagId);

            verifyNoMoreInteractions(springDataTagRepository);
        }

        @Test
        @DisplayName("findById는 일반 findById가 아니라 삭제되지 않은 태그 조회 메서드를 호출한다")
        void findById_callsSoftDeleteAwareMethod() {

            // given
            UUID tagId = UUID.randomUUID();

            when(springDataTagRepository
                    .findByIdAndDeletedAtIsNull(tagId))
                    .thenReturn(Optional.empty());

            // when
            tagRepository.findById(tagId);

            // then
            verify(springDataTagRepository)
                    .findByIdAndDeletedAtIsNull(tagId);

            verify(springDataTagRepository, never())
                    .findById(tagId);

            verifyNoMoreInteractions(springDataTagRepository);
        }

        @Test
        @DisplayName("findById는 Spring Data Repository가 반환한 Optional을 그대로 반환한다")
        void findById_returnsSameOptional() {

            // given
            UUID tagId = UUID.randomUUID();
            Tag tag = mock(Tag.class);

            Optional<Tag> expected =
                    Optional.of(tag);

            when(springDataTagRepository
                    .findByIdAndDeletedAtIsNull(tagId))
                    .thenReturn(expected);

            // when
            Optional<Tag> result =
                    tagRepository.findById(tagId);

            // then
            assertThat(result)
                    .isSameAs(expected);

            verify(springDataTagRepository)
                    .findByIdAndDeletedAtIsNull(tagId);

            verifyNoMoreInteractions(springDataTagRepository);
        }

        @Test
        @DisplayName("findById는 전달받은 tagId를 그대로 전달한다")
        void findById_passesTagId() {

            // given
            UUID tagId = UUID.randomUUID();

            when(springDataTagRepository
                    .findByIdAndDeletedAtIsNull(any(UUID.class)))
                    .thenReturn(Optional.empty());

            ArgumentCaptor<UUID> captor =
                    ArgumentCaptor.forClass(UUID.class);

            // when
            tagRepository.findById(tagId);

            // then
            verify(springDataTagRepository)
                    .findByIdAndDeletedAtIsNull(captor.capture());

            assertThat(captor.getValue())
                    .isEqualTo(tagId);

            verifyNoMoreInteractions(springDataTagRepository);
        }
    }

    @Nested
    @DisplayName("searchTags")
    class SearchTags {

        @Test
        @DisplayName("태그 목록을 생성일 내림차순과 ID 내림차순 조회 메서드를 통해 반환한다")
        void searchTags_success() {

            // given
            Pageable pageable =
                    PageRequest.of(0, 20);

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

            when(springDataTagRepository
                    .findAllByOrderByCreatedAtDescIdDesc(pageable))
                    .thenReturn(expected);

            // when
            PageResult<Tag> result =
                    tagRepository.searchTags(toQuery(pageable));

            // then
            assertThat(result.content())
                    .isEqualTo(expected.getContent());

            assertThat(result.content())
                    .containsExactly(
                            first,
                            second,
                            third
                    );

            assertThat(result.page())
                    .isZero();

            assertThat(result.size())
                    .isEqualTo(20);

            assertThat(result.totalElements())
                    .isEqualTo(3);

            assertThat(result.totalPages())
                    .isEqualTo(1);

            verify(springDataTagRepository, times(1))
                    .findAllByOrderByCreatedAtDescIdDesc(pageable);

            verifyNoMoreInteractions(springDataTagRepository);
        }

        @Test
        @DisplayName("조회되는 태그가 없으면 빈 Page를 반환한다")
        void searchTags_empty_returnsEmptyPage() {

            // given
            Pageable pageable =
                    PageRequest.of(0, 20);

            Page<Tag> expected =
                    Page.empty(pageable);

            when(springDataTagRepository
                    .findAllByOrderByCreatedAtDescIdDesc(pageable))
                    .thenReturn(expected);

            // when
            PageResult<Tag> result =
                    tagRepository.searchTags(toQuery(pageable));

            // then
            assertThat(result)
                    .isNotNull();

            assertThat(result.content())
                    .isEmpty();

            assertThat(result.page())
                    .isZero();

            assertThat(result.size())
                    .isEqualTo(20);

            assertThat(result.totalElements())
                    .isZero();

            assertThat(result.totalPages())
                    .isZero();

            verify(springDataTagRepository, times(1))
                    .findAllByOrderByCreatedAtDescIdDesc(pageable);

            verifyNoMoreInteractions(springDataTagRepository);
        }

        @Test
        @DisplayName("두 번째 페이지 요청의 Pageable을 변경하지 않고 그대로 전달한다")
        void searchTags_secondPage_passesPageableAsIs() {

            // given
            Pageable pageable =
                    PageRequest.of(1, 10);

            Tag tag = mock(Tag.class);

            Page<Tag> expected =
                    new PageImpl<>(
                            List.of(tag),
                            pageable,
                            11
                    );

            when(springDataTagRepository
                    .findAllByOrderByCreatedAtDescIdDesc(pageable))
                    .thenReturn(expected);

            // when
            PageResult<Tag> result =
                    tagRepository.searchTags(toQuery(pageable));

            // then
            assertThat(result.content())
                    .isEqualTo(expected.getContent());

            assertThat(result.page())
                    .isEqualTo(1);

            assertThat(result.size())
                    .isEqualTo(10);

            assertThat(result.totalElements())
                    .isEqualTo(11);

            assertThat(result.totalPages())
                    .isEqualTo(2);

            verify(springDataTagRepository)
                    .findAllByOrderByCreatedAtDescIdDesc(pageable);

            verifyNoMoreInteractions(springDataTagRepository);
        }

        @Test
        @DisplayName("searchTags는 전달받은 Pageable 객체를 그대로 전달한다")
        void searchTags_passesExactPageable() {

            // given
            Pageable pageable =
                    PageRequest.of(
                            2,
                            5,
                            Sort.by(
                                    Sort.Direction.ASC,
                                    "name"
                            )
                    );

            when(springDataTagRepository
                    .findAllByOrderByCreatedAtDescIdDesc(any(Pageable.class)))
                    .thenReturn(Page.empty(pageable));

            ArgumentCaptor<Pageable> captor =
                    ArgumentCaptor.forClass(Pageable.class);

            // when
            tagRepository.searchTags(toQuery(pageable));

            // then
            verify(springDataTagRepository)
                    .findAllByOrderByCreatedAtDescIdDesc(
                            captor.capture()
                    );

            assertThat(captor.getValue())
                    .isEqualTo(pageable);

            verifyNoMoreInteractions(springDataTagRepository);
        }

        @Test
        @DisplayName("searchTags는 Spring Data Repository가 반환한 Page를 자체 Pagination 계약(PageResult)으로 변환해 반환한다")
        void searchTags_returnsSamePage() {

            // given
            Pageable pageable =
                    PageRequest.of(0, 20);

            Page<Tag> expected =
                    new PageImpl<>(List.of(mock(Tag.class)), pageable, 1);

            when(springDataTagRepository
                    .findAllByOrderByCreatedAtDescIdDesc(pageable))
                    .thenReturn(expected);

            // when
            PageResult<Tag> result =
                    tagRepository.searchTags(toQuery(pageable));

            // then
            assertThat(result.content())
                    .isEqualTo(expected.getContent());

            verify(springDataTagRepository)
                    .findAllByOrderByCreatedAtDescIdDesc(pageable);

            verifyNoMoreInteractions(springDataTagRepository);
        }
    }

    @Nested
    @DisplayName("searchTagsByAttribute")
    class SearchTagsByAttribute {

        @Test
        @DisplayName("특정 속성에 해당하는 태그 목록을 반환한다")
        void searchTagsByAttribute_success() {

            // given
            TagAttribute attribute =
                    TagAttribute.values()[0];

            Pageable pageable =
                    PageRequest.of(0, 20);

            Tag first = mock(Tag.class);
            Tag second = mock(Tag.class);
            Tag third = mock(Tag.class);

            List<Tag> tags =
                    List.of(
                            first,
                            second,
                            third
                    );

            Page<Tag> expected =
                    new PageImpl<>(
                            tags,
                            pageable,
                            3
                    );

            when(springDataTagRepository
                    .findByAttributeOrderByCreatedAtDescIdDesc(
                            attribute,
                            pageable
                    ))
                    .thenReturn(expected);

            // when
            PageResult<Tag> result =
                    tagRepository.searchTagsByAttribute(
                            attribute,
                            toQuery(pageable)
                    );

            // then
            assertThat(result.content())
                    .isEqualTo(expected.getContent());

            assertThat(result.content())
                    .containsExactly(
                            first,
                            second,
                            third
                    );

            assertThat(result.page())
                    .isZero();

            assertThat(result.size())
                    .isEqualTo(20);

            assertThat(result.totalElements())
                    .isEqualTo(3);

            assertThat(result.totalPages())
                    .isEqualTo(1);

            verify(springDataTagRepository, times(1))
                    .findByAttributeOrderByCreatedAtDescIdDesc(
                            attribute,
                            pageable
                    );

            verifyNoMoreInteractions(springDataTagRepository);
        }

        @Test
        @DisplayName("해당 속성에 속하는 태그가 없으면 빈 Page를 반환한다")
        void searchTagsByAttribute_empty_returnsEmptyPage() {

            // given
            TagAttribute attribute =
                    TagAttribute.values()[0];

            Pageable pageable =
                    PageRequest.of(0, 20);

            Page<Tag> expected =
                    Page.empty(pageable);

            when(springDataTagRepository
                    .findByAttributeOrderByCreatedAtDescIdDesc(
                            attribute,
                            pageable
                    ))
                    .thenReturn(expected);

            // when
            PageResult<Tag> result =
                    tagRepository.searchTagsByAttribute(
                            attribute,
                            toQuery(pageable)
                    );

            // then
            assertThat(result)
                    .isNotNull();

            assertThat(result.content())
                    .isEmpty();

            assertThat(result.totalElements())
                    .isZero();

            assertThat(result.totalPages())
                    .isZero();

            verify(springDataTagRepository, times(1))
                    .findByAttributeOrderByCreatedAtDescIdDesc(
                            attribute,
                            pageable
                    );

            verifyNoMoreInteractions(springDataTagRepository);
        }

        @Test
        @DisplayName("attribute와 Pageable을 Spring Data Repository에 정확하게 전달한다")
        void searchTagsByAttribute_passesArgumentsCorrectly() {

            // given
            TagAttribute attribute =
                    TagAttribute.values()[0];

            Pageable pageable =
                    PageRequest.of(
                            1,
                            10
                    );

            when(springDataTagRepository
                    .findByAttributeOrderByCreatedAtDescIdDesc(
                            any(TagAttribute.class),
                            any(Pageable.class)
                    ))
                    .thenReturn(Page.empty(pageable));

            ArgumentCaptor<TagAttribute> attributeCaptor =
                    ArgumentCaptor.forClass(TagAttribute.class);

            ArgumentCaptor<Pageable> pageableCaptor =
                    ArgumentCaptor.forClass(Pageable.class);

            // when
            tagRepository.searchTagsByAttribute(
                    attribute,
                    toQuery(pageable)
            );

            // then
            verify(springDataTagRepository)
                    .findByAttributeOrderByCreatedAtDescIdDesc(
                            attributeCaptor.capture(),
                            pageableCaptor.capture()
                    );

            assertThat(attributeCaptor.getValue())
                    .isSameAs(attribute);

            assertThat(pageableCaptor.getValue())
                    .isEqualTo(pageable);

            verifyNoMoreInteractions(springDataTagRepository);
        }

        @Test
        @DisplayName("두 번째 페이지 요청도 attribute와 Pageable을 변경하지 않고 전달한다")
        void searchTagsByAttribute_secondPage_success() {

            // given
            TagAttribute attribute =
                    TagAttribute.values()[0];

            Pageable pageable =
                    PageRequest.of(
                            1,
                            5
                    );

            Tag tag = mock(Tag.class);

            Page<Tag> expected =
                    new PageImpl<>(
                            List.of(tag),
                            pageable,
                            6
                    );

            when(springDataTagRepository
                    .findByAttributeOrderByCreatedAtDescIdDesc(
                            attribute,
                            pageable
                    ))
                    .thenReturn(expected);

            // when
            PageResult<Tag> result =
                    tagRepository.searchTagsByAttribute(
                            attribute,
                            toQuery(pageable)
                    );

            // then
            assertThat(result.page())
                    .isEqualTo(1);

            assertThat(result.size())
                    .isEqualTo(5);

            assertThat(result.totalElements())
                    .isEqualTo(6);

            assertThat(result.totalPages())
                    .isEqualTo(2);

            assertThat(result.content())
                    .containsExactly(tag);

            verify(springDataTagRepository)
                    .findByAttributeOrderByCreatedAtDescIdDesc(
                            attribute,
                            pageable
                    );

            verifyNoMoreInteractions(springDataTagRepository);
        }

        @Test
        @DisplayName("Spring Data Repository가 반환한 Page를 자체 Pagination 계약(PageResult)으로 변환해 반환한다")
        void searchTagsByAttribute_returnsSamePage() {

            // given
            TagAttribute attribute =
                    TagAttribute.values()[0];

            Pageable pageable =
                    PageRequest.of(0, 10);

            Page<Tag> expected =
                    new PageImpl<>(List.of(mock(Tag.class)), pageable, 1);

            when(springDataTagRepository
                    .findByAttributeOrderByCreatedAtDescIdDesc(
                            attribute,
                            pageable
                    ))
                    .thenReturn(expected);

            // when
            PageResult<Tag> result =
                    tagRepository.searchTagsByAttribute(
                            attribute,
                            toQuery(pageable)
                    );

            // then
            assertThat(result.content())
                    .isEqualTo(expected.getContent());

            verify(springDataTagRepository)
                    .findByAttributeOrderByCreatedAtDescIdDesc(
                            attribute,
                            pageable
                    );

            verifyNoMoreInteractions(springDataTagRepository);
        }
    }

    private static PageQuery toQuery(Pageable pageable) {
        return PageQuery.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                pageable.getSort().stream()
                        .map(order -> order.isAscending()
                                ? SortOrder.asc(order.getProperty())
                                : SortOrder.desc(order.getProperty()))
                        .toList()
        );
    }
}
