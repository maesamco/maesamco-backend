package com.maesamco.coaching.application.query_service;

import com.maesamco.coaching.domain.entity.WeakConcept;
import com.maesamco.coaching.domain.repository.WeakConceptRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class WeakConceptQueryServiceTest {

    @Mock
    private WeakConceptRepository weakConceptRepository;

    @InjectMocks
    private WeakConceptQueryService weakConceptQueryService;

    @Test
    @DisplayName("우선순위(개선 안 된 것 우선, 발견 횟수 높은 순)로 정렬된 목록을 그대로 반환한다")
    void getWeakConcepts_returnsRepositoryResultInOrder() {
        UUID userId = UUID.randomUUID();
        WeakConcept weakConcept = WeakConcept.create(userId, "재귀");
        given(weakConceptRepository.findByUserIdOrderByImprovedAscOccurrenceCountDesc(userId))
                .willReturn(List.of(weakConcept));

        List<WeakConcept> result = weakConceptQueryService.getWeakConcepts(userId);

        assertThat(result).containsExactly(weakConcept);
    }

    @Test
    @DisplayName("취약 개념이 없으면 빈 목록을 반환한다")
    void getWeakConcepts_returnsEmptyList_whenNone() {
        UUID userId = UUID.randomUUID();
        given(weakConceptRepository.findByUserIdOrderByImprovedAscOccurrenceCountDesc(userId))
                .willReturn(List.of());

        List<WeakConcept> result = weakConceptQueryService.getWeakConcepts(userId);

        assertThat(result).isEmpty();
    }
}
