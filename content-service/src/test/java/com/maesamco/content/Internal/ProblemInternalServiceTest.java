package com.maesamco.content.Internal;

import com.maesamco.content.problem.application.port.ProblemFinder;
import com.maesamco.content.problem.application.port.ProblemTagFinder;
import com.maesamco.content.problem.application.service.ProblemInternalService;
import com.maesamco.content.problem.domain.entity.Problem;
import com.maesamco.content.problem.presentation.dto.response.InternalProblemResponse;
import com.maesamco.content.tag.domain.entity.Tag;
import com.maesamco.content.tag.domain.enums.TagAttribute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProblemInternalServiceTest {

    @Mock
    private ProblemFinder problemFinder;

    @Mock
    private ProblemTagFinder problemTagFinder;

    @Mock
    private Problem problem;

    @Mock
    private Tag conceptTag;

    @Mock
    private Tag algorithmTag;

    private ProblemInternalService problemInternalService;

    @BeforeEach
    void setUp() {
        problemInternalService = new ProblemInternalService(
                problemFinder,
                problemTagFinder
        );
    }

    @Test
    @DisplayName("내부 문제 조회 시 연결된 전체 태그 중 CONCEPT 태그만 List<String>으로 반환한다.")
    void getProblemMetaData_success() {

        // given
        UUID problemId = UUID.randomUUID();

        Tag conceptTag1 = org.mockito.Mockito.mock(Tag.class);
        Tag conceptTag2 = org.mockito.Mockito.mock(Tag.class);
        Tag algorithmTag = org.mockito.Mockito.mock(Tag.class);
        Tag dataStructureTag = org.mockito.Mockito.mock(Tag.class);

        when(problemFinder.getProblem(problemId))
                .thenReturn(problem);

        when(problem.getId())
                .thenReturn(problemId);

        when(problem.getDescription())
                .thenReturn("두 정수를 입력받아 합을 출력하세요.");

        // CONCEPT
        when(conceptTag1.getAttribute())
                .thenReturn(TagAttribute.CONCEPT);
        when(conceptTag1.getName())
                .thenReturn("반복문");

        // ALGORITHM
        when(algorithmTag.getAttribute())
                .thenReturn(TagAttribute.ALGORITHM);
        when(algorithmTag.getName())
                .thenReturn("구현");

        // CONCEPT
        when(conceptTag2.getAttribute())
                .thenReturn(TagAttribute.CONCEPT);
        when(conceptTag2.getName())
                .thenReturn("조건문");

        // DATA_STRUCTURE
        when(dataStructureTag.getAttribute())
                .thenReturn(TagAttribute.DATA_STRUCTURE);
        when(dataStructureTag.getName())
                .thenReturn("배열");

        List<Tag> linkedTags = List.of(
                conceptTag1,
                algorithmTag,
                conceptTag2,
                dataStructureTag
        );

        when(problemTagFinder.getTagsByProblemId(problemId))
                .thenReturn(linkedTags);

        // 해당 문제와 연결된 전체 태그 출력
        System.out.println("===== Problem =====");
        System.out.println("id = " + problem.getId());
        System.out.println("description = " + problem.getDescription());

        System.out.println("===== Linked Tags =====");

        for (Tag tag : linkedTags) {
            System.out.println(
                    "name = " + tag.getName()
                            + ", attribute = " + tag.getAttribute()
            );
        }

        // when
        InternalProblemResponse response =
                problemInternalService.getProblemMetaData(problemId);

        // 반환 객체 출력
        System.out.println("===== InternalProblemResponse =====");
        System.out.println("id = " + response.getId());
        System.out.println("description = " + response.getDescription());
        System.out.println("conceptTags = " + response.getConceptTags());
        System.out.println("===================================");

        // then
        assertThat(response.getId())
                .isEqualTo(problemId);

        assertThat(response.getDescription())
                .isEqualTo("두 정수를 입력받아 합을 출력하세요.");

        assertThat(response.getConceptTags())
                .containsExactly("반복문", "조건문");
    }

    @Test
    @DisplayName("내부 문제 조회 시 문제 지문과 개념 태그를 반환한다.")
    void getProblem_success() {

        // given
        UUID problemId = UUID.randomUUID();

        when(problemFinder.getProblem(problemId))
                .thenReturn(problem);

        when(problemTagFinder.getTagsByProblemId(problemId))
                .thenReturn(List.of(conceptTag, algorithmTag));

        when(problem.getId())
                .thenReturn(problemId);

        when(problem.getDescription())
                .thenReturn("두 정수를 입력받아 두 수의 합을 출력하세요.");

        when(conceptTag.getAttribute())
                .thenReturn(TagAttribute.CONCEPT);

        when(conceptTag.getName())
                .thenReturn("반복문");

        when(algorithmTag.getAttribute())
                .thenReturn(TagAttribute.ALGORITHM);

        when(algorithmTag.getName())
                .thenReturn("구현");

        // 연결된 전체 태그 출력
        List<Tag> tags = problemTagFinder.getTagsByProblemId(problemId);

        System.out.println("===== Linked Tags =====");
        for (Tag tag : tags) {
            System.out.println(
                    "name = " + tag.getName()
                            + ", attribute = " + tag.getAttribute()
            );
        }

        // when
        InternalProblemResponse response =
                problemInternalService.getProblemMetaData(problemId);

        // 반환 객체 출력
        System.out.println("===== InternalProblemResponse =====");
        System.out.println("id = " + response.getId());
        System.out.println("description = " + response.getDescription());
        System.out.println("conceptTags = " + response.getConceptTags());
        System.out.println("===================================");

        // then
        assertThat(response.getId())
                .isEqualTo(problemId);

        assertThat(response.getDescription())
                .isEqualTo("두 정수를 입력받아 두 수의 합을 출력하세요.");

        assertThat(response.getConceptTags())
                .containsExactly("반복문");
    }

    @Test
    @DisplayName("개념 태그가 없으면 빈 리스트를 반환한다.")
    void getProblem_withoutConceptTag() {

        // given
        UUID problemId = UUID.randomUUID();

        when(problemFinder.getProblem(problemId))
                .thenReturn(problem);

        when(problemTagFinder.getTagsByProblemId(problemId))
                .thenReturn(List.of(algorithmTag));

        when(problem.getId())
                .thenReturn(problemId);

        when(problem.getDescription())
                .thenReturn("문제 지문");

        when(algorithmTag.getAttribute())
                .thenReturn(TagAttribute.ALGORITHM);

        when(algorithmTag.getName())
                .thenReturn("구현");

        // 연결된 전체 태그 출력
        List<Tag> tags = problemTagFinder.getTagsByProblemId(problemId);

        System.out.println("===== Linked Tags =====");
        for (Tag tag : tags) {
            System.out.println(
                    "name = " + tag.getName()
                            + ", attribute = " + tag.getAttribute()
            );
        }

        // when
        InternalProblemResponse response =
                problemInternalService.getProblemMetaData(problemId);

        // 반환 객체 출력
        System.out.println("===== InternalProblemResponse =====");
        System.out.println("id = " + response.getId());
        System.out.println("description = " + response.getDescription());
        System.out.println("conceptTags = " + response.getConceptTags());
        System.out.println("===================================");

        // then
        assertThat(response.getId())
                .isEqualTo(problemId);

        assertThat(response.getDescription())
                .isEqualTo("문제 지문");

        assertThat(response.getConceptTags())
                .isEmpty();
    }
}