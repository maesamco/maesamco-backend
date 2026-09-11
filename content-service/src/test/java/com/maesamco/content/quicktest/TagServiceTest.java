package com.maesamco.content.quicktest;

import com.maesamco.content.problem.domain.repository.ProblemTagRepository;
import com.maesamco.content.tag.application.port.TagFinder;
import com.maesamco.content.tag.application.service.TagService;
import com.maesamco.content.tag.domain.entity.Tag;
import com.maesamco.content.tag.domain.enums.TagAttribute;
import com.maesamco.content.tag.domain.repository.TagRepository;
import com.maesamco.content.tag.presentation.dto.request.TagCreateRequest;
import com.maesamco.content.tag.presentation.dto.request.TagUpdateRequest;
import com.maesamco.content.tag.presentation.dto.response.TagCreateResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock
    private TagRepository tagRepository;

    @Mock
    private ProblemTagRepository problemTagRepository;

    @Mock
    private TagFinder tagFinder;

    @InjectMocks
    private TagService tagService;

    @Test
    @DisplayName(
            "태그를 생성하면 태그 정보를 반환한다"
    )
    void createTag_success() {
        // given
        UUID tagId = UUID.randomUUID();

        TagCreateRequest request =
                mock(TagCreateRequest.class);

        when(request.getName())
                .thenReturn("반복문");

        when(request.getAttribute())
                .thenReturn(TagAttribute.CONCEPT);

        when(tagRepository.save(any(Tag.class)))
                .thenAnswer(invocation -> {
                    Tag tag = invocation.getArgument(0);

                    ReflectionTestUtils.setField(
                            tag,
                            "id",
                            tagId
                    );

                    return tag;
                });

        // when
        TagCreateResponse response =
                tagService.createTag(request);

        // then
        ArgumentCaptor<Tag> captor =
                ArgumentCaptor.forClass(Tag.class);

        verify(tagRepository)
                .save(captor.capture());

        Tag savedTag = captor.getValue();

        assertThat(response.getId())
                .isEqualTo(tagId);

        assertThat(savedTag.getName())
                .isEqualTo("반복문");

        assertThat(savedTag.getAttribute())
                .isEqualTo(TagAttribute.CONCEPT);
    }

    @Test
    @DisplayName(
            "태그 정보를 수정한다"
    )
    void updateTag_success() {
        // given
        UUID tagId = UUID.randomUUID();

        Tag tag = Tag.create(
                "반복문",
                TagAttribute.CONCEPT
        );

        ReflectionTestUtils.setField(
                tag,
                "id",
                tagId
        );

        TagUpdateRequest request =
                mock(TagUpdateRequest.class);

        when(tagFinder.getTag(tagId))
                .thenReturn(tag);

        when(request.getName())
                .thenReturn("배열");

        when(request.getAttribute())
                .thenReturn(
                        TagAttribute.DATA_STRUCTURE
                );

        // when
        tagService.updateTag(
                tagId,
                request
        );

        // then
        assertThat(tag.getName())
                .isEqualTo("배열");

        assertThat(tag.getAttribute())
                .isEqualTo(
                        TagAttribute.DATA_STRUCTURE
                );
    }

    @Test
    @DisplayName(
            "태그를 삭제하면 연결 데이터를 제거하고 요청 사용자 ID를 deletedBy에 기록한다"
    )
    void deleteTag_deletesRelationsAndRecordsActor() {
        // given
        UUID tagId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        Tag tag = Tag.create(
                "반복문",
                TagAttribute.CONCEPT
        );

        ReflectionTestUtils.setField(
                tag,
                "id",
                tagId
        );

        when(tagFinder.getTag(tagId))
                .thenReturn(tag);

        // when
        tagService.deleteTag(
                tagId,
                adminId
        );

        // then
        verify(problemTagRepository)
                .deleteAllByTagId(tagId);

        assertThat(tag.isDeleted())
                .isTrue();

        assertThat(tag.getDeletedAt())
                .isNotNull();

        assertThat(tag.getDeletedBy())
                .isEqualTo(adminId);

        assertThat(tag.getDeletedBy())
                .isNotEqualTo(tagId);
    }
}
