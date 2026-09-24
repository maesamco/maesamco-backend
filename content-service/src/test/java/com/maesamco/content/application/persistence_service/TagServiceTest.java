package com.maesamco.content.application.persistence_service;

import com.maesamco.content.application.finder.TagFinder;
import com.maesamco.content.domain.entity.Tag;
import com.maesamco.content.domain.entity.TagAttribute;
import com.maesamco.content.domain.repository.TagRepository;
import com.maesamco.content.domain.repository.problem.ProblemTagRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import com.maesamco.content.global.common.pagination.PageQuery;
import com.maesamco.content.global.common.pagination.PageResult;
import com.maesamco.content.application.command.TagCreateCommand;
import com.maesamco.content.application.command.TagUpdateCommand;
import com.maesamco.content.application.result.TagResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TagService 단위 테스트")
class TagServiceTest {

    @Mock
    private TagRepository tagRepository;

    @Mock
    private ProblemTagRepository problemTagRepository;

    @Mock
    private TagFinder tagFinder;

    private TagService tagService;

    @BeforeEach
    void setUp() {
        tagService = new TagService(tagRepository, problemTagRepository, tagFinder);
    }

    // ============================================================
    // 1. createTag
    // ============================================================

    @Nested
    @DisplayName("createTag")
    class CreateTag {

        @Test
        @DisplayName("중복되지 않은 이름이면 요청 값으로 태그를 생성하고 저장한다")
        void createTag_success() {

            // given
            TagCreateCommand request = mock(TagCreateCommand.class);
            Tag savedTag = mock(Tag.class);

            when(request.getName()).thenReturn("자료구조");
            when(request.getAttribute()).thenReturn(TagAttribute.CONCEPT);
            when(tagRepository.existsByName("자료구조")).thenReturn(false);
            when(tagRepository.save(any(Tag.class))).thenReturn(savedTag);

            // when
            TagResult result = tagService.createTag(request);

            // then
            ArgumentCaptor<Tag> captor = ArgumentCaptor.forClass(Tag.class);

            verify(tagRepository).existsByName("자료구조");
            verify(tagRepository).save(captor.capture());

            Tag tag = captor.getValue();

            assertThat(tag.getName()).isEqualTo("자료구조");
            assertThat(tag.getAttribute()).isEqualTo(TagAttribute.CONCEPT);
            assertThat(result).isNotNull();

            verifyNoInteractions(tagFinder, problemTagRepository);
            verifyNoMoreInteractions(tagRepository);
        }

        @Test
        @DisplayName("이미 존재하는 이름이면 TAG_NAME_ALREADY_EXISTS 예외가 발생한다")
        void createTag_duplicateName_throwsTagNameAlreadyExists() {

            // given
            TagCreateCommand request = mock(TagCreateCommand.class);

            when(request.getName()).thenReturn("자료구조");
            when(tagRepository.existsByName("자료구조")).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> tagService.createTag(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(exception -> {
                        BusinessException businessException = (BusinessException) exception;
                        assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.TAG_NAME_ALREADY_EXISTS);
                    });

            verify(tagRepository).existsByName("자료구조");
            verify(tagRepository, never()).save(any(Tag.class));
            verifyNoInteractions(tagFinder, problemTagRepository);
            verifyNoMoreInteractions(tagRepository);
        }

        @Test
        @DisplayName("이름 중복 검사를 통과한 경우에만 Tag를 저장한다")
        void createTag_checksDuplicateBeforeSave() {

            // given
            TagCreateCommand request = mock(TagCreateCommand.class);
            Tag savedTag = mock(Tag.class);

            when(request.getName()).thenReturn("그래프");
            when(request.getAttribute()).thenReturn(TagAttribute.CONCEPT);
            when(tagRepository.existsByName("그래프")).thenReturn(false);
            when(tagRepository.save(any(Tag.class))).thenReturn(savedTag);

            // when
            tagService.createTag(request);

            // then
            InOrder inOrder = inOrder(tagRepository);
            inOrder.verify(tagRepository).existsByName("그래프");
            inOrder.verify(tagRepository).save(any(Tag.class));
        }
    }

    // ============================================================
    // 2. searchTags
    // ============================================================

    @Nested
    @DisplayName("searchTags")
    class SearchTags {

        @Test
        @DisplayName("전체 태그 조회 결과를 PageResponse로 변환하여 반환한다")
        void searchTags_success() {

            // given
            PageQuery pageQuery = PageQuery.of(0, 10);
            Tag first = mock(Tag.class);
            Tag second = mock(Tag.class);
            PageResult<Tag> page = new PageResult<>(List.of(first, second), pageQuery.page(), pageQuery.size(), 2);

            when(tagRepository.searchTags(pageQuery)).thenReturn(page);

            // when
            PageResult<TagResult> result = tagService.searchTags(pageQuery);

            // then
            assertThat(result).isNotNull();

            verify(tagRepository).searchTags(pageQuery);
            verifyNoInteractions(tagFinder, problemTagRepository);
            verifyNoMoreInteractions(tagRepository);
        }

        @Test
        @DisplayName("전체 태그 조회 결과가 비어 있어도 정상적으로 빈 PageResponse를 반환한다")
        void searchTags_emptyPage_success() {

            // given
            PageQuery pageQuery = PageQuery.of(0, 10);
            PageResult<Tag> page = new PageResult<>(List.of(), pageQuery.page(), pageQuery.size(), 0);

            when(tagRepository.searchTags(pageQuery)).thenReturn(page);

            // when
            PageResult<TagResult> result = tagService.searchTags(pageQuery);

            // then
            assertThat(result).isNotNull();

            verify(tagRepository).searchTags(pageQuery);
            verifyNoInteractions(tagFinder, problemTagRepository);
            verifyNoMoreInteractions(tagRepository);
        }

        @Test
        @DisplayName("전달받은 Pageable을 변경하지 않고 Repository에 전달한다")
        void searchTags_passesExactPageable() {

            // given
            PageQuery pageQuery = PageQuery.of(2, 20);
            PageResult<Tag> page = new PageResult<>(List.of(), pageQuery.page(), pageQuery.size(), 0);

            when(tagRepository.searchTags(pageQuery)).thenReturn(page);

            // when
            tagService.searchTags(pageQuery);

            // then
            verify(tagRepository).searchTags(same(pageQuery));
            verifyNoInteractions(tagFinder, problemTagRepository);
            verifyNoMoreInteractions(tagRepository);
        }
    }

    // ============================================================
    // 3. searchTagsByAttribute
    // ============================================================

    @Nested
    @DisplayName("searchTagsByAttribute")
    class SearchTagsByAttribute {

        @Test
        @DisplayName("지정한 속성과 Pageable로 태그를 조회한다")
        void searchTagsByAttribute_success() {

            // given
            TagAttribute attribute = TagAttribute.CONCEPT;
            PageQuery pageQuery = PageQuery.of(0, 10);

            Tag first = mock(Tag.class);
            Tag second = mock(Tag.class);
            PageResult<Tag> page = new PageResult<>(List.of(first, second), pageQuery.page(), pageQuery.size(), 2);

            when(tagRepository.searchTagsByAttribute(attribute, pageQuery)).thenReturn(page);

            // when
            PageResult<TagResult> result = tagService.searchTagsByAttribute(attribute, pageQuery);

            // then
            assertThat(result).isNotNull();

            verify(tagRepository).searchTagsByAttribute(attribute, pageQuery);
            verifyNoInteractions(tagFinder, problemTagRepository);
            verifyNoMoreInteractions(tagRepository);
        }

        @Test
        @DisplayName("지정한 속성의 태그가 없어도 정상적으로 빈 PageResponse를 반환한다")
        void searchTagsByAttribute_emptyPage_success() {

            // given
            TagAttribute attribute = TagAttribute.CONCEPT;
            PageQuery pageQuery = PageQuery.of(0, 10);
            PageResult<Tag> page = new PageResult<>(List.of(), pageQuery.page(), pageQuery.size(), 0);

            when(tagRepository.searchTagsByAttribute(attribute, pageQuery)).thenReturn(page);

            // when
            PageResult<TagResult> result = tagService.searchTagsByAttribute(attribute, pageQuery);

            // then
            assertThat(result).isNotNull();

            verify(tagRepository).searchTagsByAttribute(attribute, pageQuery);
            verifyNoInteractions(tagFinder, problemTagRepository);
            verifyNoMoreInteractions(tagRepository);
        }

        @Test
        @DisplayName("attribute와 Pageable을 변경하지 않고 Repository에 전달한다")
        void searchTagsByAttribute_passesExactArguments() {

            // given
            TagAttribute attribute = TagAttribute.CONCEPT;
            PageQuery pageQuery = PageQuery.of(3, 15);
            PageResult<Tag> page = new PageResult<>(List.of(), pageQuery.page(), pageQuery.size(), 0);

            when(tagRepository.searchTagsByAttribute(attribute, pageQuery)).thenReturn(page);

            // when
            tagService.searchTagsByAttribute(attribute, pageQuery);

            // then
            verify(tagRepository).searchTagsByAttribute(eq(attribute), same(pageQuery));
            verifyNoInteractions(tagFinder, problemTagRepository);
            verifyNoMoreInteractions(tagRepository);
        }
    }

    // ============================================================
    // 4. updateTag
    // TagFinder의 NOT_FOUND 검증은 TagFinderServiceTest에서 수행
    // ============================================================

    @Nested
    @DisplayName("updateTag")
    class UpdateTag {

        @Test
        @DisplayName("새로운 name과 attribute가 전달되면 두 값을 모두 수정한다")
        void updateTag_nameAndAttribute_success() {

            // given
            UUID tagId = UUID.randomUUID();
            Tag tag = mock(Tag.class);
            TagUpdateCommand request = mock(TagUpdateCommand.class);

            when(tagFinder.getById(tagId)).thenReturn(tag);
            when(tag.getName()).thenReturn("기존 이름");
            when(request.getName()).thenReturn("새 이름");
            when(request.getAttribute()).thenReturn(TagAttribute.CONCEPT);
            when(tagRepository.existsByName("새 이름")).thenReturn(false);

            // when
            tagService.updateTag(tagId, request);

            // then
            verify(tagFinder).getById(tagId);
            verify(tagRepository).existsByName("새 이름");
            verify(tag).changeName("새 이름");
            verify(tag).changeAttribute(TagAttribute.CONCEPT);
            verifyNoInteractions(problemTagRepository);
        }

        @Test
        @DisplayName("name만 변경되면 중복 검사 후 name만 수정한다")
        void updateTag_onlyName_changesNameOnly() {

            // given
            UUID tagId = UUID.randomUUID();
            Tag tag = mock(Tag.class);
            TagUpdateCommand request = mock(TagUpdateCommand.class);

            when(tagFinder.getById(tagId)).thenReturn(tag);
            when(tag.getName()).thenReturn("기존 이름");
            when(request.getName()).thenReturn("새 이름");
            when(request.getAttribute()).thenReturn(null);
            when(tagRepository.existsByName("새 이름")).thenReturn(false);

            // when
            tagService.updateTag(tagId, request);

            // then
            verify(tagRepository).existsByName("새 이름");
            verify(tag).changeName("새 이름");
            verify(tag, never()).changeAttribute(any());

            verifyNoInteractions(problemTagRepository);
        }

        @Test
        @DisplayName("attribute만 전달되면 이름 중복 검사 없이 attribute만 수정한다")
        void updateTag_onlyAttribute_changesAttributeOnly() {

            // given
            UUID tagId = UUID.randomUUID();
            Tag tag = mock(Tag.class);
            TagUpdateCommand request = mock(TagUpdateCommand.class);

            when(tagFinder.getById(tagId)).thenReturn(tag);
            when(request.getName()).thenReturn(null);
            when(request.getAttribute()).thenReturn(TagAttribute.CONCEPT);

            // when
            tagService.updateTag(tagId, request);

            // then
            verify(tag).changeAttribute(TagAttribute.CONCEPT);
            verify(tag, never()).changeName(anyString());
            verifyNoInteractions(tagRepository, problemTagRepository);
        }

        @Test
        @DisplayName("name과 attribute가 모두 null이면 태그를 변경하지 않는다")
        void updateTag_allFieldsNull_doesNotChangeTag() {

            // given
            UUID tagId = UUID.randomUUID();
            Tag tag = mock(Tag.class);
            TagUpdateCommand request = mock(TagUpdateCommand.class);

            when(tagFinder.getById(tagId)).thenReturn(tag);
            when(request.getName()).thenReturn(null);
            when(request.getAttribute()).thenReturn(null);

            // when
            tagService.updateTag(tagId, request);

            // then
            verify(tagFinder).getById(tagId);
            verify(tag, never()).changeName(anyString());
            verify(tag, never()).changeAttribute(any());

            verifyNoInteractions(tagRepository, problemTagRepository);
        }

        @Test
        @DisplayName("현재 이름과 동일한 name이 전달되면 중복 검사와 이름 변경을 수행하지 않는다")
        void updateTag_sameName_doesNotCheckDuplicateOrChangeName() {

            // given
            UUID tagId = UUID.randomUUID();
            Tag tag = mock(Tag.class);
            TagUpdateCommand request = mock(TagUpdateCommand.class);

            when(tagFinder.getById(tagId)).thenReturn(tag);
            when(tag.getName()).thenReturn("자료구조");
            when(request.getName()).thenReturn("자료구조");
            when(request.getAttribute()).thenReturn(null);

            // when
            tagService.updateTag(tagId, request);

            // then
            verify(tagFinder).getById(tagId);
            verify(tagRepository, never()).existsByName(anyString());
            verify(tag, never()).changeName(anyString());
            verify(tag, never()).changeAttribute(any());

            verifyNoInteractions(problemTagRepository);
        }

        @Test
        @DisplayName("변경하려는 이름이 이미 존재하면 TAG_NAME_ALREADY_EXISTS 예외가 발생한다")
        void updateTag_duplicateName_throwsTagNameAlreadyExists() {

            // given
            UUID tagId = UUID.randomUUID();
            Tag tag = mock(Tag.class);
            TagUpdateCommand request = mock(TagUpdateCommand.class);

            when(tagFinder.getById(tagId)).thenReturn(tag);
            when(tag.getName()).thenReturn("기존 이름");
            when(request.getName()).thenReturn("중복 이름");
            when(tagRepository.existsByName("중복 이름")).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> tagService.updateTag(tagId, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(exception -> {
                        BusinessException businessException = (BusinessException) exception;
                        assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.TAG_NAME_ALREADY_EXISTS);
                    });

            verify(tagFinder).getById(tagId);
            verify(tagRepository).existsByName("중복 이름");
            verify(tag, never()).changeName(anyString());
            verify(tag, never()).changeAttribute(any());

            verifyNoInteractions(problemTagRepository);
        }

        @Test
        @DisplayName("이름 중복이 발생하면 attribute 값이 전달되어도 attribute를 수정하지 않는다")
        void updateTag_duplicateName_doesNotChangeAttribute() {

            // given
            UUID tagId = UUID.randomUUID();
            Tag tag = mock(Tag.class);
            TagUpdateCommand request = mock(TagUpdateCommand.class);

            when(tagFinder.getById(tagId)).thenReturn(tag);
            when(tag.getName()).thenReturn("기존 이름");
            when(request.getName()).thenReturn("중복 이름");
            when(tagRepository.existsByName("중복 이름")).thenReturn(true);

            // when
            assertThatThrownBy(() -> tagService.updateTag(tagId, request))
                    .isInstanceOf(BusinessException.class);

            // then
            verify(tag, never()).changeName(anyString());
            verify(tag, never()).changeAttribute(any());
        }

        @Test
        @DisplayName("태그 수정 시 Repository.save를 명시적으로 호출하지 않는다")
        void updateTag_doesNotCallSave() {

            // given
            UUID tagId = UUID.randomUUID();
            Tag tag = mock(Tag.class);
            TagUpdateCommand request = mock(TagUpdateCommand.class);

            when(tagFinder.getById(tagId)).thenReturn(tag);
            when(tag.getName()).thenReturn("기존 이름");
            when(request.getName()).thenReturn("수정된 이름");
            when(tagRepository.existsByName("수정된 이름")).thenReturn(false);

            // when
            tagService.updateTag(tagId, request);

            // then
            verify(tag).changeName("수정된 이름");
            verify(tagRepository, never()).save(any(Tag.class));
        }
    }

    // ============================================================
    // 5. deleteTag
    // TagFinder의 NOT_FOUND 검증은 TagFinderServiceTest에서 수행
    // ============================================================

    @Nested
    @DisplayName("deleteTag")
    class DeleteTag {

        @Test
        @DisplayName("태그 삭제 시 ProblemTag 연결을 삭제하고 요청 사용자 ID로 태그를 softDelete한다")
        void deleteTag_success() {

            // given
            UUID tagId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            Tag tag = mock(Tag.class);

            when(tagFinder.getById(tagId)).thenReturn(tag);

            // when
            tagService.deleteTag(tagId, userId);

            // then
            verify(tagFinder).getById(tagId);
            verify(problemTagRepository).deleteAllByTagId(tagId);
            verify(tag).softDelete(userId);

            verifyNoInteractions(tagRepository);
            verifyNoMoreInteractions(tagFinder, problemTagRepository);
        }

        @Test
        @DisplayName("태그 삭제 시 ProblemTag 연결 삭제 후 Tag softDelete를 수행한다")
        void deleteTag_deletesProblemTagsBeforeSoftDelete() {

            // given
            UUID tagId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            Tag tag = mock(Tag.class);

            when(tagFinder.getById(tagId)).thenReturn(tag);

            // when
            tagService.deleteTag(tagId, userId);

            // then
            InOrder inOrder = inOrder(problemTagRepository, tag);
            inOrder.verify(problemTagRepository).deleteAllByTagId(tagId);
            inOrder.verify(tag).softDelete(userId);
        }

        @Test
        @DisplayName("태그 삭제 시 TagRepository의 delete나 save를 직접 호출하지 않는다")
        void deleteTag_doesNotCallTagRepositoryModification() {

            // given
            UUID tagId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            Tag tag = mock(Tag.class);

            when(tagFinder.getById(tagId)).thenReturn(tag);

            // when
            tagService.deleteTag(tagId, userId);

            // then
            verify(problemTagRepository).deleteAllByTagId(tagId);
            verify(tag).softDelete(userId);
            verifyNoInteractions(tagRepository);
        }
    }
}