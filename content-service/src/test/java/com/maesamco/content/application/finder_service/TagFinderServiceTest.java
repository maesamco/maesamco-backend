package com.maesamco.content.application.finder_service;

import com.maesamco.content.application.finder_service.TagFinderService;
import com.maesamco.content.domain.entity.Tag;
import com.maesamco.content.domain.repository.TagRepository;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TagFinderService 단위 테스트")
class TagFinderServiceTest {

    @Mock
    private TagRepository tagRepository;

    private TagFinderService tagFinderService;

    @BeforeEach
    void setUp() {
        tagFinderService = new TagFinderService(tagRepository);
    }

    // -------------------------------------------------------------------------
    // 1. getTag - 정상 조회
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("getTag 성공")
    class GetTagSuccess {

        @Test
        @DisplayName("존재하는 태그 ID를 조회하면 해당 태그를 반환한다")
        void getTag_existingTag_returnsTag() {
            // given
            UUID tagId = UUID.randomUUID();
            Tag tag = mock(Tag.class);

            when(tagRepository.findById(tagId))
                    .thenReturn(Optional.of(tag));

            // when
            Tag result = tagFinderService.getById(tagId);

            // then
            assertThat(result).isNotNull();
            assertThat(result).isSameAs(tag);

            verify(tagRepository, times(1))
                    .findById(tagId);

            verifyNoMoreInteractions(tagRepository);
        }

        @Test
        @DisplayName("Repository에서 조회한 태그 객체를 변환하지 않고 그대로 반환한다")
        void getTag_repositoryReturnsTag_preservesSameInstance() {
            // given
            UUID tagId = UUID.randomUUID();
            Tag tag = mock(Tag.class);

            when(tagRepository.findById(tagId))
                    .thenReturn(Optional.of(tag));

            // when
            Tag result = tagFinderService.getById(tagId);

            // then
            assertThat(result)
                    .as("Finder는 Repository가 반환한 Tag 인스턴스를 그대로 반환해야 한다")
                    .isSameAs(tag);

            verify(tagRepository).findById(tagId);
            verifyNoMoreInteractions(tagRepository);
        }

        @Test
        @DisplayName("전달받은 태그 ID를 변경하지 않고 Repository에 그대로 전달한다")
        void getTag_givenTagId_queriesExactId() {
            // given
            UUID tagId = UUID.randomUUID();
            Tag tag = mock(Tag.class);

            when(tagRepository.findById(tagId))
                    .thenReturn(Optional.of(tag));

            // when
            tagFinderService.getById(tagId);

            // then
            verify(tagRepository, times(1))
                    .findById(tagId);

            verifyNoMoreInteractions(tagRepository);
        }

        @Test
        @DisplayName("서로 다른 태그 ID를 조회하면 각각의 ID로 Repository를 조회한다")
        void getTag_differentIds_queriesExactIds() {
            // given
            UUID firstTagId = UUID.randomUUID();
            UUID secondTagId = UUID.randomUUID();

            Tag firstTag = mock(Tag.class);
            Tag secondTag = mock(Tag.class);

            when(tagRepository.findById(firstTagId))
                    .thenReturn(Optional.of(firstTag));

            when(tagRepository.findById(secondTagId))
                    .thenReturn(Optional.of(secondTag));

            // when
            Tag firstResult = tagFinderService.getById(firstTagId);
            Tag secondResult = tagFinderService.getById(secondTagId);

            // then
            assertThat(firstResult).isSameAs(firstTag);
            assertThat(secondResult).isSameAs(secondTag);

            verify(tagRepository, times(1))
                    .findById(firstTagId);

            verify(tagRepository, times(1))
                    .findById(secondTagId);

            verifyNoMoreInteractions(tagRepository);
        }
    }

    // -------------------------------------------------------------------------
    // 2. getTag - 존재하지 않는 태그
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("getTag 실패 - 태그 없음")
    class GetTagNotFound {

        @Test
        @DisplayName("존재하지 않는 태그 ID를 조회하면 BusinessException을 던진다")
        void getTag_nonExistingTag_throwsBusinessException() {
            // given
            UUID tagId = UUID.randomUUID();

            when(tagRepository.findById(tagId))
                    .thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> tagFinderService.getById(tagId))
                    .isInstanceOf(BusinessException.class);

            verify(tagRepository, times(1))
                    .findById(tagId);

            verifyNoMoreInteractions(tagRepository);
        }

        @Test
        @DisplayName("존재하지 않는 태그를 조회하면 TAG_NOT_FOUND ErrorCode를 가진다")
        void getTag_nonExistingTag_hasTagNotFoundErrorCode() {
            // given
            UUID tagId = UUID.randomUUID();

            when(tagRepository.findById(tagId))
                    .thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> tagFinderService.getById(tagId))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(exception -> {
                        BusinessException businessException =
                                (BusinessException) exception;

                        assertThat(businessException.getErrorCode())
                                .isEqualTo(ErrorCode.TAG_NOT_FOUND);
                    });

            verify(tagRepository, times(1))
                    .findById(tagId);

            verifyNoMoreInteractions(tagRepository);
        }

        @Test
        @DisplayName("TAG_NOT_FOUND 예외는 ErrorCode에 정의된 메시지를 사용한다")
        void getTag_nonExistingTag_hasCorrectErrorMessage() {
            // given
            UUID tagId = UUID.randomUUID();

            when(tagRepository.findById(tagId))
                    .thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> tagFinderService.getById(tagId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(ErrorCode.TAG_NOT_FOUND.getMessage());

            verify(tagRepository, times(1))
                    .findById(tagId);

            verifyNoMoreInteractions(tagRepository);
        }

        @Test
        @DisplayName("태그가 존재하지 않아도 Repository 조회는 정확히 한 번만 수행한다")
        void getTag_nonExistingTag_queriesRepositoryOnlyOnce() {
            // given
            UUID tagId = UUID.randomUUID();

            when(tagRepository.findById(tagId))
                    .thenReturn(Optional.empty());

            // when
            try {
                tagFinderService.getById(tagId);
            } catch (BusinessException ignored) {
            }

            // then
            verify(tagRepository, times(1))
                    .findById(tagId);

            verifyNoMoreInteractions(tagRepository);
        }
    }

    // -------------------------------------------------------------------------
    // 3. Repository 상호작용
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Repository 상호작용")
    class RepositoryInteraction {

        @Test
        @DisplayName("getTag은 TagRepository.findById 이외의 추가 Repository 동작을 수행하지 않는다")
        void getTag_performsOnlyFindById() {
            // given
            UUID tagId = UUID.randomUUID();
            Tag tag = mock(Tag.class);

            when(tagRepository.findById(tagId))
                    .thenReturn(Optional.of(tag));

            // when
            tagFinderService.getById(tagId);

            // then
            verify(tagRepository, times(1))
                    .findById(tagId);

            verifyNoMoreInteractions(tagRepository);
        }
    }
}