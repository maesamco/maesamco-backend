package com.maesamco.content.application.persistence_service;

import com.maesamco.content.application.result.ConceptValidationInternalResult;
import com.maesamco.content.domain.entity.Tag;
import com.maesamco.content.domain.entity.TagAttribute;
import com.maesamco.content.domain.repository.TagRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConceptValidationInternalServiceTest {

    @Mock
    private TagRepository tagRepository;

    private ConceptValidationInternalService target() {
        return new ConceptValidationInternalService(tagRepository);
    }

    @Test
    @DisplayName("모든 ID가 존재하는 CONCEPT 태그면 valid=true를 반환한다")
    void validate_allConceptTagsExist_returnsValidTrue() {
        // given
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        Tag tag1 = mockTag(id1, TagAttribute.CONCEPT);
        Tag tag2 = mockTag(id2, TagAttribute.CONCEPT);

        when(tagRepository.findAllByIds(anyCollection()))
                .thenReturn(List.of(tag1, tag2));

        // when
        ConceptValidationInternalResult result =
                target().validate(List.of(id1, id2));

        // then
        assertThat(result.valid()).isTrue();
        assertThat(result.validConceptIds()).containsExactlyInAnyOrder(id1, id2);
        assertThat(result.invalidConceptIds()).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 ID가 섞여 있으면 valid=false이고 invalidConceptIds에 담긴다")
    void validate_someIdsNotFound_returnsValidFalse() {
        // given
        UUID existingId = UUID.randomUUID();
        UUID missingId = UUID.randomUUID();

        Tag existingTag = mockTag(existingId, TagAttribute.CONCEPT);

        when(tagRepository.findAllByIds(anyCollection()))
                .thenReturn(List.of(existingTag));

        // when
        ConceptValidationInternalResult result =
                target().validate(List.of(existingId, missingId));

        // then
        assertThat(result.valid()).isFalse();
        assertThat(result.validConceptIds()).containsExactly(existingId);
        assertThat(result.invalidConceptIds()).containsExactly(missingId);
    }

    @Test
    @DisplayName("존재는 하지만 attribute가 CONCEPT가 아닌 태그는 무효로 취급한다")
    void validate_existingTagWithDifferentAttribute_treatedAsInvalid() {
        // given
        UUID dataStructureTagId = UUID.randomUUID();

        Tag dataStructureTag = mockTag(dataStructureTagId, TagAttribute.DATA_STRUCTURE);

        when(tagRepository.findAllByIds(anyCollection()))
                .thenReturn(List.of(dataStructureTag));

        // when
        ConceptValidationInternalResult result =
                target().validate(List.of(dataStructureTagId));

        // then
        assertThat(result.valid()).isFalse();
        assertThat(result.validConceptIds()).isEmpty();
        assertThat(result.invalidConceptIds()).containsExactly(dataStructureTagId);
    }

    @Test
    @DisplayName("중복된 ID는 하나로 취급해서 검증한다")
    void validate_duplicateIds_deduplicated() {
        // given
        UUID id = UUID.randomUUID();

        Tag tag = mockTag(id, TagAttribute.CONCEPT);

        when(tagRepository.findAllByIds(anyCollection()))
                .thenReturn(List.of(tag));

        // when
        ConceptValidationInternalResult result =
                target().validate(List.of(id, id, id));

        // then
        assertThat(result.valid()).isTrue();
        assertThat(result.validConceptIds()).containsExactly(id);
        assertThat(result.invalidConceptIds()).isEmpty();
    }

    private Tag mockTag(UUID id, TagAttribute attribute) {
        Tag tag = org.mockito.Mockito.mock(Tag.class);
        // attribute가 CONCEPT가 아니면 필터링에서 걸러져 getId()가 호출되지 않으므로 lenient로 둔다.
        org.mockito.Mockito.lenient().when(tag.getId()).thenReturn(id);
        when(tag.getAttribute()).thenReturn(attribute);
        return tag;
    }
}
