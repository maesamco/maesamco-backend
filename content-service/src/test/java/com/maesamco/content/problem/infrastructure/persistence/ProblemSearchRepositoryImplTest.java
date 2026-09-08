package com.maesamco.content.problem.infrastructure.persistence;

import com.maesamco.content.problem.domain.entity.Problem;
import com.maesamco.content.problem.domain.enums.*;
import com.maesamco.content.problem.presentation.dto.request.ProblemSearchRequest;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true",
        "spring.flyway.enabled=false"
})
@Import(ProblemSearchRepositoryImplTest.TestJpaConfig.class)
class ProblemSearchRepositoryImplTest {

    private static final UUID TEST_USER_ID = UUID.randomUUID();

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ProblemSearchRepositoryImpl problemSearchRepository;

    private Problem javaEasy;
    private Problem javaHard;
    private Problem pythonMedium;
    private Problem cppEasy;
    private Problem deletedProblem;

    @TestConfiguration
    @EnableJpaAuditing(auditorAwareRef = "testAuditorAware")
    static class TestJpaConfig {

        @Bean
        AuditorAware<UUID> testAuditorAware() {
            return () -> Optional.of(TEST_USER_ID);
        }

        @Bean
        JPAQueryFactory jpaQueryFactory(
                EntityManager entityManager
        ) {
            return new JPAQueryFactory(entityManager);
        }
    }

    @BeforeEach
    void setUp() {

        /*
         * title을 일부러 동일하게 둔 데이터가 있다.
         * 다중 정렬(title ASC + difficulty DESC)을 검증하기 위해서다.
         */
        javaEasy = saveProblem(
                "가 문제",
                ProgrammingLanguage.JAVA,
                ProblemDifficulty.EASY,
                ProblemType.CODE,
                ProblemSource.HUMAN_AUTHORED,
                ProblemStatus.DRAFT,
                TimerPolicy.APPLY60
        );

        javaHard = saveProblem(
                "가 문제",
                ProgrammingLanguage.JAVA,
                ProblemDifficulty.HARD,
                ProblemType.CODE,
                ProblemSource.AI_ASSISTED,
                ProblemStatus.PUBLISHED,
                TimerPolicy.APPLY120
        );

        pythonMedium = saveProblem(
                "나 문제",
                ProgrammingLanguage.PYTHON,
                ProblemDifficulty.MEDIUM,
                ProblemType.SHORT_ANSWER,
                ProblemSource.AI_ASSISTED,
                ProblemStatus.REVIEW_PENDING,
                TimerPolicy.APPLY180
        );

        cppEasy = saveProblem(
                "다 문제",
                ProgrammingLanguage.CPP,
                ProblemDifficulty.EASY,
                ProblemType.FILL_IN_BLANK,
                ProblemSource.AI_ASSISTED,
                ProblemStatus.PUBLISHED,
                TimerPolicy.APPLY300
        );

        deletedProblem = saveProblem(
                "삭제된 문제",
                ProgrammingLanguage.JAVA,
                ProblemDifficulty.MEDIUM,
                ProblemType.CODE,
                ProblemSource.HUMAN_AUTHORED,
                ProblemStatus.DRAFT,
                TimerPolicy.NOT_APPLY_TIMEPOLICY
        );

        /*
         * BaseEntity의 @SQLRestriction("deleted_at IS NULL")에 의해
         * 이후 검색 쿼리에서는 자동으로 제외되어야 한다.
         */
        deletedProblem.softDelete(TEST_USER_ID);

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("검색 조건이 없으면 삭제되지 않은 모든 문제를 조회한다")
    void searchProblems_withoutConditions_returnsAllProblems() {
        // given
        ProblemSearchRequest request = new ProblemSearchRequest();
        Pageable pageable = PageRequest.of(0, 20);

        // when
        Page<Problem> result =
                problemSearchRepository.searchProblems(request, pageable);

        // then
        assertThat(result.getContent()).hasSize(4);

        assertThat(result.getContent())
                .extracting(Problem::getId)
                .containsExactlyInAnyOrder(
                        javaEasy.getId(),
                        javaHard.getId(),
                        pythonMedium.getId(),
                        cppEasy.getId()
                );

        assertThat(result.getTotalElements()).isEqualTo(4);
    }

    @Test
    @DisplayName("검색 조건 객체가 null이어도 삭제되지 않은 모든 문제를 조회한다")
    void searchProblems_nullRequest_returnsAllProblems() {
        // given
        Pageable pageable = PageRequest.of(0, 20);

        // when
        Page<Problem> result =
                problemSearchRepository.searchProblems(null, pageable);

        // then
        assertThat(result.getContent()).hasSize(4);
        assertThat(result.getTotalElements()).isEqualTo(4);
    }

    @Test
    @DisplayName("프로그래밍 언어 조건으로 문제를 검색할 수 있다")
    void searchProblems_filtersByLanguage() {
        // given
        ProblemSearchRequest request =
                requestWith("language", ProgrammingLanguage.JAVA);

        Pageable pageable = PageRequest.of(0, 20);

        // when
        Page<Problem> result =
                problemSearchRepository.searchProblems(request, pageable);

        // then
        assertThat(result.getContent()).hasSize(2);

        assertThat(result.getContent())
                .extracting(Problem::getLanguage)
                .containsOnly(ProgrammingLanguage.JAVA);

        assertThat(result.getContent())
                .extracting(Problem::getId)
                .containsExactlyInAnyOrder(
                        javaEasy.getId(),
                        javaHard.getId()
                );
    }

    @Test
    @DisplayName("문제 난이도 조건으로 문제를 검색할 수 있다")
    void searchProblems_filtersByDifficulty() {
        // given
        ProblemSearchRequest request =
                requestWith(
                        "difficulty",
                        ProblemDifficulty.EASY
                );

        Pageable pageable = PageRequest.of(0, 20);

        // when
        Page<Problem> result =
                problemSearchRepository.searchProblems(request, pageable);

        // then
        assertThat(result.getContent()).hasSize(2);

        assertThat(result.getContent())
                .extracting(Problem::getDifficulty)
                .containsOnly(ProblemDifficulty.EASY);

        assertThat(result.getContent())
                .extracting(Problem::getId)
                .containsExactlyInAnyOrder(
                        javaEasy.getId(),
                        cppEasy.getId()
                );
    }

    @Test
    @DisplayName("문제 유형 조건으로 문제를 검색할 수 있다")
    void searchProblems_filtersByType() {
        // given
        ProblemSearchRequest request =
                requestWith(
                        "type",
                        ProblemType.SHORT_ANSWER
                );

        Pageable pageable = PageRequest.of(0, 20);

        // when
        Page<Problem> result =
                problemSearchRepository.searchProblems(request, pageable);

        // then
        assertThat(result.getContent()).hasSize(1);

        assertThat(result.getContent().get(0).getId())
                .isEqualTo(pythonMedium.getId());
    }

    @Test
    @DisplayName("문제 출처 조건으로 문제를 검색할 수 있다")
    void searchProblems_filtersBySource() {
        // given
        ProblemSearchRequest request =
                requestWith(
                        "source",
                        ProblemSource.HUMAN_AUTHORED
                );

        Pageable pageable = PageRequest.of(0, 20);

        // when
        Page<Problem> result =
                problemSearchRepository.searchProblems(request, pageable);

        // then
        /*
         * deletedProblem도 HUMAN_AUTHORED이지만
         * @SQLRestriction에 의해 검색 결과에서는 빠져야 한다.
         */
        assertThat(result.getContent()).hasSize(1);

        assertThat(result.getContent().get(0).getId())
                .isEqualTo(javaEasy.getId());
    }

    @Test
    @DisplayName("문제 상태 조건으로 문제를 검색할 수 있다")
    void searchProblems_filtersByProblemStatus() {
        // given
        ProblemSearchRequest request =
                requestWith(
                        "problemStatus",
                        ProblemStatus.PUBLISHED
                );

        Pageable pageable = PageRequest.of(0, 20);

        // when
        Page<Problem> result =
                problemSearchRepository.searchProblems(request, pageable);

        // then
        assertThat(result.getContent()).hasSize(2);

        assertThat(result.getContent())
                .extracting(Problem::getProblemStatus)
                .containsOnly(ProblemStatus.PUBLISHED);

        assertThat(result.getContent())
                .extracting(Problem::getId)
                .containsExactlyInAnyOrder(
                        javaHard.getId(),
                        cppEasy.getId()
                );
    }

    @Test
    @DisplayName("여러 검색 조건은 AND 조건으로 함께 적용된다")
    void searchProblems_combinesConditionsWithAnd() {
        // given
        ProblemSearchRequest request =
                new ProblemSearchRequest();

        ReflectionTestUtils.setField(
                request,
                "language",
                ProgrammingLanguage.JAVA
        );

        ReflectionTestUtils.setField(
                request,
                "difficulty",
                ProblemDifficulty.HARD
        );

        ReflectionTestUtils.setField(
                request,
                "type",
                ProblemType.CODE
        );

        ReflectionTestUtils.setField(
                request,
                "source",
                ProblemSource.AI_ASSISTED
        );

        ReflectionTestUtils.setField(
                request,
                "problemStatus",
                ProblemStatus.PUBLISHED
        );

        Pageable pageable = PageRequest.of(0, 20);

        // when
        Page<Problem> result =
                problemSearchRepository.searchProblems(request, pageable);

        // then
        assertThat(result.getContent()).hasSize(1);

        assertThat(result.getContent().get(0).getId())
                .isEqualTo(javaHard.getId());
    }

    @Test
    @DisplayName("검색 조건을 만족하는 문제가 없으면 빈 페이지를 반환한다")
    void searchProblems_returnsEmpty_whenNoProblemMatches() {
        // given
        ProblemSearchRequest request =
                new ProblemSearchRequest();

        ReflectionTestUtils.setField(
                request,
                "language",
                ProgrammingLanguage.PYTHON
        );

        ReflectionTestUtils.setField(
                request,
                "difficulty",
                ProblemDifficulty.HARD
        );

        Pageable pageable = PageRequest.of(0, 20);

        // when
        Page<Problem> result =
                problemSearchRepository.searchProblems(request, pageable);

        // then
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("제목을 오름차순으로 정렬할 수 있다")
    void searchProblems_sortsByTitleAscending() {
        // given
        ProblemSearchRequest request =
                new ProblemSearchRequest();

        Pageable pageable = PageRequest.of(
                0,
                20,
                Sort.by(Sort.Order.asc("title"))
        );

        // when
        Page<Problem> result =
                problemSearchRepository.searchProblems(request, pageable);

        // then
        assertThat(result.getContent())
                .extracting(Problem::getTitle)
                .containsExactly(
                        "가 문제",
                        "가 문제",
                        "나 문제",
                        "다 문제"
                );
    }

    @Test
    @DisplayName("난이도 오름차순 정렬은 EASY, MEDIUM, HARD 순으로 적용된다")
    void searchProblems_sortsDifficultyAscendingByBusinessOrder() {
        // given
        ProblemSearchRequest request =
                new ProblemSearchRequest();

        Pageable pageable = PageRequest.of(
                0,
                20,
                Sort.by(
                        Sort.Order.asc("difficulty")
                )
        );

        // when
        Page<Problem> result =
                problemSearchRepository.searchProblems(
                        request,
                        pageable
                );

        // then
        assertThat(result.getContent())
                .extracting(Problem::getDifficulty)
                .containsExactly(
                        ProblemDifficulty.EASY,
                        ProblemDifficulty.EASY,
                        ProblemDifficulty.MEDIUM,
                        ProblemDifficulty.HARD
                );
    }

    @Test
    @DisplayName("난이도 내림차순 정렬은 HARD, MEDIUM, EASY 순으로 적용된다")
    void searchProblems_sortsDifficultyDescendingByBusinessOrder() {
        // given
        ProblemSearchRequest request =
                new ProblemSearchRequest();

        Pageable pageable = PageRequest.of(
                0,
                20,
                Sort.by(
                        Sort.Order.desc("difficulty")
                )
        );

        // when
        Page<Problem> result =
                problemSearchRepository.searchProblems(
                        request,
                        pageable
                );

        // then
        assertThat(result.getContent())
                .extracting(Problem::getDifficulty)
                .containsExactly(
                        ProblemDifficulty.HARD,
                        ProblemDifficulty.MEDIUM,
                        ProblemDifficulty.EASY,
                        ProblemDifficulty.EASY
                );
    }

    @Test
    @DisplayName("여러 정렬 조건을 전달하면 전달된 순서대로 다중 정렬한다")
    void searchProblems_appliesMultipleSortConditions() {
        // given
        ProblemSearchRequest request =
                new ProblemSearchRequest();

        Pageable pageable = PageRequest.of(
                0,
                20,
                Sort.by(
                        Sort.Order.asc("title"),
                        Sort.Order.desc("difficulty")
                )
        );

        // when
        Page<Problem> result =
                problemSearchRepository.searchProblems(
                        request,
                        pageable
                );

        // then
        /*
         * 두 "가 문제"는 title이 같으므로
         * 두 번째 정렬 조건인 difficulty DESC가 적용된다.
         *
         * 따라서 HARD → EASY 순으로 나와야 한다.
         */
        assertThat(result.getContent())
                .extracting(
                        Problem::getTitle,
                        Problem::getDifficulty
                )
                .containsExactly(
                        tuple(
                                "가 문제",
                                ProblemDifficulty.HARD
                        ),
                        tuple(
                                "가 문제",
                                ProblemDifficulty.EASY
                        ),
                        tuple(
                                "나 문제",
                                ProblemDifficulty.MEDIUM
                        ),
                        tuple(
                                "다 문제",
                                ProblemDifficulty.EASY
                        )
                );
    }

    @Test
    @DisplayName("지원하지 않는 정렬 필드는 무시하고 기본 정렬을 적용한다")
    void searchProblems_unsupportedSort_usesDefaultSort() {
        // given
        ProblemSearchRequest request =
                new ProblemSearchRequest();

        Pageable defaultPageable =
                PageRequest.of(0, 20);

        Pageable unsupportedSortPageable =
                PageRequest.of(
                        0,
                        20,
                        Sort.by("unknownField")
                );

        // when
        Page<Problem> defaultResult =
                problemSearchRepository.searchProblems(
                        request,
                        defaultPageable
                );

        Page<Problem> unsupportedResult =
                problemSearchRepository.searchProblems(
                        request,
                        unsupportedSortPageable
                );

        // then
        assertThat(unsupportedResult.getContent())
                .extracting(Problem::getId)
                .containsExactlyElementsOf(
                        defaultResult.getContent()
                                .stream()
                                .map(Problem::getId)
                                .toList()
                );
    }

    @Test
    @DisplayName("page와 size에 따라 문제 목록을 페이징한다")
    void searchProblems_appliesPagination() {
        // given
        ProblemSearchRequest request =
                new ProblemSearchRequest();

        Pageable pageable = PageRequest.of(
                1,
                2,
                Sort.by(Sort.Order.asc("title"))
        );

        // when
        Page<Problem> result =
                problemSearchRepository.searchProblems(
                        request,
                        pageable
                );

        // then
        assertThat(result.getNumber()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(2);
        assertThat(result.getContent()).hasSize(2);

        /*
         * soft delete된 문제 하나는 제외되므로
         * 전체 검색 가능한 문제는 4개다.
         */
        assertThat(result.getTotalElements()).isEqualTo(4);
        assertThat(result.getTotalPages()).isEqualTo(2);

        assertThat(result.hasPrevious()).isTrue();
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    @DisplayName("soft delete된 문제는 SQLRestriction에 의해 검색 결과에서 제외된다")
    void searchProblems_excludesSoftDeletedProblem() {
        // given
        ProblemSearchRequest request =
                new ProblemSearchRequest();

        Pageable pageable = PageRequest.of(0, 20);

        // when
        Page<Problem> result =
                problemSearchRepository.searchProblems(
                        request,
                        pageable
                );

        // then
        assertThat(result.getContent())
                .extracting(Problem::getId)
                .doesNotContain(deletedProblem.getId());

        assertThat(result.getTotalElements()).isEqualTo(4);
    }

    private ProblemSearchRequest requestWith(
            String fieldName,
            Object value
    ) {
        ProblemSearchRequest request =
                new ProblemSearchRequest();

        ReflectionTestUtils.setField(
                request,
                fieldName,
                value
        );

        return request;
    }

    private Problem saveProblem(
            String title,
            ProgrammingLanguage language,
            ProblemDifficulty difficulty,
            ProblemType type,
            ProblemSource source,
            ProblemStatus problemStatus,
            TimerPolicy timerPolicy
    ) {
        Problem problem = Problem.create(
                title,
                language,
                difficulty,
                type,
                title + "에 대한 문제 지문입니다.",
                "public class Main {}",
                RunningTimeLimit.values()[0],
                RunningMemoryLimit.values()[0],
                timerPolicy,
                source,
                problemStatus
        );

        entityManager.persist(problem);

        return problem;
    }
}