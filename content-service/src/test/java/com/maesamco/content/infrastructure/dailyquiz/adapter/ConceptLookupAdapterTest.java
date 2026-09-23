package com.maesamco.content.infrastructure.dailyquiz.adapter;

import com.maesamco.content.domain.entity.Tag;
import com.maesamco.content.domain.entity.TagAttribute;
import com.maesamco.content.domain.repository.TagRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConceptLookupAdapterTest {

    @Mock
    private TagRepository tagRepository;

    private ConceptLookupAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ConceptLookupAdapter(tagRepository);
    }

    @Test
    void 관심_태그_ID_목록이_null이면_실패한다() {
        assertThatThrownBy(() -> adapter.getConceptTags(null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE)
                );

        verifyNoInteractions(tagRepository);
    }

    @Test
    void 관심_태그_ID에_null이_포함되면_실패한다() {
        assertThatThrownBy(() -> adapter.getConceptTags(Arrays.asList(UUID.randomUUID(), null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE)
                );

        verifyNoInteractions(tagRepository);
    }

    @Test
    void 관심_태그_ID가_비어_있으면_빈_목록을_반환한다() {
        assertThat(adapter.getConceptTags(List.of())).isEmpty();

        verifyNoInteractions(tagRepository);
    }

    @Test
    void 관심_태그를_한_번에_조회하고_CONCEPT만_입력_순서대로_반환한다() {
        UUID secondConceptId = UUID.randomUUID();
        UUID missingTagId = UUID.randomUUID();
        UUID algorithmTagId = UUID.randomUUID();
        UUID firstConceptId = UUID.randomUUID();
        List<UUID> distinctTagIds = List.of(
                secondConceptId,
                missingTagId,
                algorithmTagId,
                firstConceptId
        );

        Tag firstConcept = tag(firstConceptId, "조건문", TagAttribute.CONCEPT);
        Tag algorithmTag = tag(algorithmTagId, "정렬", TagAttribute.ALGORITHM);
        Tag secondConcept = tag(secondConceptId, "반복문", TagAttribute.CONCEPT);
        when(tagRepository.findAllByIds(distinctTagIds))
                .thenReturn(List.of(firstConcept, algorithmTag, secondConcept));

        List<String> result = adapter.getConceptTags(List.of(
                secondConceptId,
                missingTagId,
                algorithmTagId,
                firstConceptId,
                secondConceptId
        ));

        assertThat(result).containsExactly("반복문", "조건문");
        verify(tagRepository).findAllByIds(distinctTagIds);
        verifyNoMoreInteractions(tagRepository);
    }

    private Tag tag(UUID tagId, String name, TagAttribute attribute) {
        Tag tag = mock(Tag.class);
        when(tag.getId()).thenReturn(tagId);
        when(tag.getAttribute()).thenReturn(attribute);
        if (attribute == TagAttribute.CONCEPT) {
            when(tag.getName()).thenReturn(name);
        }
        return tag;
    }
}
