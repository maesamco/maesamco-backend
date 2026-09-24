package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.global.common.pagination.PageQuery;
import com.maesamco.content.global.common.pagination.PageResult;
import com.maesamco.content.global.common.pagination.SortOrder;
import com.maesamco.content.domain.entity.Curriculum;
import com.maesamco.content.domain.entity.Lesson;
import com.maesamco.content.domain.entity.ProgrammingLanguage;
import com.maesamco.content.domain.entity.Unit;
import com.maesamco.content.domain.entity.problem.*;
import com.maesamco.content.domain.repository.problem.ProblemQueryRepository;
import com.maesamco.content.domain.repository.problem.ProblemSearchCondition;
import com.maesamco.content.global.config.JpaAuditingConfig;
import com.maesamco.content.global.config.QuerydslConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.default_schema=content_schema",
        "spring.data.jpa.repositories.enabled=false"
})
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@ImportAutoConfiguration(
        FlywayAutoConfiguration.class
)
@Import({
        JpaAuditingConfig.class,
        QuerydslConfig.class,
        ProblemQueryRepositoryImpl.class
})
@EnableJpaRepositories(
        basePackageClasses = {
                SpringDataProblemRepository.class
        }
)
class ProblemQueryRepositoryImplTest {

    private static final UUID TEST_USER_ID =
            UUID.randomUUID();

    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    DockerImageName.parse(
                            "postgres:16-alpine"
                    )
            );

    static {
        postgres.start();
    }

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ProblemQueryRepository problemQueryRepository;

    private Problem javaEasy;
    private Problem javaHard;
    private Problem pythonMedium;
    private Problem cEasy;
    private Problem deletedProblem;

    @BeforeEach
    void setUp() {

        javaEasy = saveProblem(
                "가 문제",
                ProgrammingLanguage.JAVA,
                ProblemDifficulty.EASY,
                ProblemSource.HUMAN_AUTHORED,
                ProblemStatus.DRAFT
        );

        javaHard = saveProblem(
                "가 문제",
                ProgrammingLanguage.JAVA,
                ProblemDifficulty.HARD,
                ProblemSource.AI_ASSISTED,
                ProblemStatus.PUBLISHED
        );

        pythonMedium = saveProblem(
                "나 문제",
                ProgrammingLanguage.PYTHON,
                ProblemDifficulty.MEDIUM,
                ProblemSource.AI_ASSISTED,
                ProblemStatus.REVIEW_PENDING
        );

        cEasy = saveProblem(
                "다 문제",
                ProgrammingLanguage.C,
                ProblemDifficulty.EASY,
                ProblemSource.AI_ASSISTED,
                ProblemStatus.PUBLISHED
        );

        deletedProblem = saveProblem(
                "삭제된 문제",
                ProgrammingLanguage.JAVA,
                ProblemDifficulty.MEDIUM,
                ProblemSource.HUMAN_AUTHORED,
                ProblemStatus.DRAFT
        );

        deletedProblem.softDelete(
                TEST_USER_ID
        );

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName(
            "검색 조건이 없으면 삭제되지 않은 모든 문제를 조회한다"
    )
    void searchProblems_withoutConditions_returnsAllProblems() {
        // given
        ProblemSearchCondition condition =
                emptyCondition();

        PageQuery pageQuery =
                PageQuery.of(0, 20);

        // when
        PageResult<Problem> result =
                problemQueryRepository.searchProblems(
                        condition,
                        pageQuery
                );

        // then
        assertThat(result.content())
                .hasSize(4);

        assertThat(result.content())
                .extracting(
                        Problem::getId
                )
                .containsExactlyInAnyOrder(
                        javaEasy.getId(),
                        javaHard.getId(),
                        pythonMedium.getId(),
                        cEasy.getId()
                );

        assertThat(result.totalElements())
                .isEqualTo(4);
    }

    @Test
    @DisplayName(
            "findById는 @SQLRestriction에 의해 soft delete된 문제를 반환하지 않는다"
    )
    void findById_softDeletedProblem_returnsEmpty() {

        assertThat(
                problemQueryRepository.findById(
                        deletedProblem.getId()
                )
        ).isEmpty();
    }

    @Test
    @DisplayName(
            "findById는 삭제되지 않은 문제를 정상 조회한다"
    )
    void findById_activeProblem_returnsProblem() {

        assertThat(
                problemQueryRepository.findById(
                        javaEasy.getId()
                )
        )
                .isPresent()
                .get()
                .extracting(
                        Problem::getId
                )
                .isEqualTo(
                        javaEasy.getId()
                );
    }

    @Test
    @DisplayName(
            "프로그래밍 언어 조건으로 문제를 검색한다"
    )
    void searchProblems_filtersByLanguage() {
        // given
        ProblemSearchCondition condition =
                condition(
                        ProgrammingLanguage.JAVA,
                        null,
                        null,
                        null
                );

        PageQuery pageQuery =
                PageQuery.of(0, 20);

        // when
        PageResult<Problem> result =
                problemQueryRepository.searchProblems(
                        condition,
                        pageQuery
                );

        // then
        assertThat(result.content())
                .hasSize(2);

        assertThat(result.content())
                .extracting(
                        Problem::getLanguage
                )
                .containsOnly(
                        ProgrammingLanguage.JAVA
                );

        assertThat(result.content())
                .extracting(
                        Problem::getId
                )
                .containsExactlyInAnyOrder(
                        javaEasy.getId(),
                        javaHard.getId()
                );
    }

    @Test
    @DisplayName(
            "문제 난이도 조건으로 문제를 검색한다"
    )
    void searchProblems_filtersByDifficulty() {
        // given
        ProblemSearchCondition condition =
                condition(
                        null,
                        ProblemDifficulty.EASY,
                        null,
                        null
                );

        PageQuery pageQuery =
                PageQuery.of(0, 20);

        // when
        PageResult<Problem> result =
                problemQueryRepository.searchProblems(
                        condition,
                        pageQuery
                );

        // then
        assertThat(result.content())
                .hasSize(2);

        assertThat(result.content())
                .extracting(
                        Problem::getDifficulty
                )
                .containsOnly(
                        ProblemDifficulty.EASY
                );

        assertThat(result.content())
                .extracting(
                        Problem::getId
                )
                .containsExactlyInAnyOrder(
                        javaEasy.getId(),
                        cEasy.getId()
                );
    }

    @Test
    @DisplayName(
            "언어와 난이도 조건은 AND 조건으로 적용된다"
    )
    void searchProblems_filtersByLanguageAndDifficulty() {
        // given
        ProblemSearchCondition condition =
                condition(
                        ProgrammingLanguage.JAVA,
                        ProblemDifficulty.EASY,
                        null,
                        null
                );

        PageQuery pageQuery =
                PageQuery.of(0, 20);

        // when
        PageResult<Problem> result =
                problemQueryRepository.searchProblems(
                        condition,
                        pageQuery
                );

        // then
        assertThat(result.content())
                .hasSize(1);

        Problem found =
                result.content()
                        .get(0);

        assertThat(found.getId())
                .isEqualTo(
                        javaEasy.getId()
                );

        assertThat(found.getLanguage())
                .isEqualTo(
                        ProgrammingLanguage.JAVA
                );

        assertThat(found.getDifficulty())
                .isEqualTo(
                        ProblemDifficulty.EASY
                );

        assertThat(found.getType())
                .isEqualTo(
                        ProblemType.CODE
                );
    }

    @Test
    @DisplayName(
            "문제 출처 조건으로 문제를 검색한다"
    )
    void searchProblems_filtersBySource() {
        // given
        ProblemSearchCondition condition =
                condition(
                        null,
                        null,
                        ProblemSource.HUMAN_AUTHORED,
                        null
                );

        PageQuery pageQuery =
                PageQuery.of(0, 20);

        // when
        PageResult<Problem> result =
                problemQueryRepository.searchProblems(
                        condition,
                        pageQuery
                );

        // then
        /*
         * deletedProblem 역시 HUMAN_AUTHORED이지만
         * soft delete 상태이므로 조회 결과에서 제외된다.
         */
        assertThat(result.content())
                .hasSize(1);

        assertThat(
                result.content()
                        .get(0)
                        .getId()
        ).isEqualTo(
                javaEasy.getId()
        );

        assertThat(
                result.content()
                        .get(0)
                        .getSource()
        ).isEqualTo(
                ProblemSource.HUMAN_AUTHORED
        );
    }

    @Test
    @DisplayName(
            "문제 상태 조건으로 문제를 검색한다"
    )
    void searchProblems_filtersByProblemStatus() {
        // given
        ProblemSearchCondition condition =
                condition(
                        null,
                        null,
                        null,
                        ProblemStatus.PUBLISHED
                );

        PageQuery pageQuery =
                PageQuery.of(0, 20);

        // when
        PageResult<Problem> result =
                problemQueryRepository.searchProblems(
                        condition,
                        pageQuery
                );

        // then
        assertThat(result.content())
                .hasSize(2);

        assertThat(result.content())
                .extracting(
                        Problem::getProblemStatus
                )
                .containsOnly(
                        ProblemStatus.PUBLISHED
                );

        assertThat(result.content())
                .extracting(
                        Problem::getId
                )
                .containsExactlyInAnyOrder(
                        javaHard.getId(),
                        cEasy.getId()
                );
    }

    @Test
    @DisplayName(
            "언어, 난이도, 출처, 상태 조건을 모두 AND 조건으로 적용한다"
    )
    void searchProblems_combinesConditionsWithAnd() {
        // given
        ProblemSearchCondition condition =
                condition(
                        ProgrammingLanguage.JAVA,
                        ProblemDifficulty.HARD,
                        ProblemSource.AI_ASSISTED,
                        ProblemStatus.PUBLISHED
                );

        PageQuery pageQuery =
                PageQuery.of(0, 20);

        // when
        PageResult<Problem> result =
                problemQueryRepository.searchProblems(
                        condition,
                        pageQuery
                );

        // then
        assertThat(result.content())
                .hasSize(1);

        Problem found =
                result.content()
                        .get(0);

        assertThat(found.getId())
                .isEqualTo(
                        javaHard.getId()
                );

        assertThat(found.getLanguage())
                .isEqualTo(
                        ProgrammingLanguage.JAVA
                );

        assertThat(found.getDifficulty())
                .isEqualTo(
                        ProblemDifficulty.HARD
                );

        assertThat(found.getSource())
                .isEqualTo(
                        ProblemSource.AI_ASSISTED
                );

        assertThat(found.getProblemStatus())
                .isEqualTo(
                        ProblemStatus.PUBLISHED
                );

        assertThat(found.getType())
                .isEqualTo(
                        ProblemType.CODE
                );
    }

    @Test
    @DisplayName(
            "검색 조건을 만족하는 문제가 없으면 빈 페이지를 반환한다"
    )
    void searchProblems_returnsEmpty_whenNoProblemMatches() {
        // given
        ProblemSearchCondition condition =
                condition(
                        ProgrammingLanguage.PYTHON,
                        ProblemDifficulty.HARD,
                        null,
                        null
                );

        PageQuery pageQuery =
                PageQuery.of(0, 20);

        // when
        PageResult<Problem> result =
                problemQueryRepository.searchProblems(
                        condition,
                        pageQuery
                );

        // then
        assertThat(result.content())
                .isEmpty();

        assertThat(result.totalElements())
                .isZero();
    }

    @Test
    @DisplayName(
            "모든 조회 결과의 문제 유형은 CODE이다"
    )
    void searchProblems_returnsOnlyCodeProblems() {
        // given
        ProblemSearchCondition condition =
                emptyCondition();

        PageQuery pageQuery =
                PageQuery.of(0, 20);

        // when
        PageResult<Problem> result =
                problemQueryRepository.searchProblems(
                        condition,
                        pageQuery
                );

        // then
        assertThat(result.content())
                .isNotEmpty();

        assertThat(result.content())
                .extracting(
                        Problem::getType
                )
                .containsOnly(
                        ProblemType.CODE
                );
    }

    @Test
    @DisplayName(
            "제목을 오름차순으로 정렬한다"
    )
    void searchProblems_sortsByTitleAscending() {
        // given
        PageQuery pageQuery =
                PageQuery.of(0, 20, List.of(SortOrder.asc("title")));

        // when
        PageResult<Problem> result =
                problemQueryRepository.searchProblems(
                        emptyCondition(),
                        pageQuery
                );

        // then
        assertThat(result.content())
                .extracting(
                        Problem::getTitle
                )
                .containsExactly(
                        "가 문제",
                        "가 문제",
                        "나 문제",
                        "다 문제"
                );
    }

    @Test
    @DisplayName(
            "난이도 오름차순 정렬은 EASY, MEDIUM, HARD 순으로 적용된다"
    )
    void searchProblems_sortsDifficultyAscendingByBusinessOrder() {
        // given
        PageQuery pageQuery =
                PageQuery.of(0, 20, List.of(SortOrder.asc("difficulty")));

        // when
        PageResult<Problem> result =
                problemQueryRepository.searchProblems(
                        emptyCondition(),
                        pageQuery
                );

        // then
        assertThat(result.content())
                .extracting(
                        Problem::getDifficulty
                )
                .containsExactly(
                        ProblemDifficulty.EASY,
                        ProblemDifficulty.EASY,
                        ProblemDifficulty.MEDIUM,
                        ProblemDifficulty.HARD
                );
    }

    @Test
    @DisplayName(
            "난이도 내림차순 정렬은 HARD, MEDIUM, EASY 순으로 적용된다"
    )
    void searchProblems_sortsDifficultyDescendingByBusinessOrder() {
        // given
        PageQuery pageQuery =
                PageQuery.of(0, 20, List.of(SortOrder.desc("difficulty")));

        // when
        PageResult<Problem> result =
                problemQueryRepository.searchProblems(
                        emptyCondition(),
                        pageQuery
                );

        // then
        assertThat(result.content())
                .extracting(
                        Problem::getDifficulty
                )
                .containsExactly(
                        ProblemDifficulty.HARD,
                        ProblemDifficulty.MEDIUM,
                        ProblemDifficulty.EASY,
                        ProblemDifficulty.EASY
                );
    }

    @Test
    @DisplayName(
            "여러 정렬 조건을 전달하면 전달된 순서대로 다중 정렬한다"
    )
    void searchProblems_appliesMultipleSortConditions() {
        // given
        PageQuery pageQuery =
                PageQuery.of(0, 20, List.of(SortOrder.asc("title"), SortOrder.desc("difficulty")));

        // when
        PageResult<Problem> result =
                problemQueryRepository.searchProblems(
                        emptyCondition(),
                        pageQuery
                );

        // then
        assertThat(result.content())
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
    @DisplayName(
            "지원하지 않는 정렬 필드는 무시하고 기본 정렬을 적용한다"
    )
    void searchProblems_unsupportedSort_usesDefaultSort() {
        // given
        PageQuery defaultPageQuery =
                PageQuery.of(0, 20);

        PageQuery unsupportedPageQuery =
                PageQuery.of(0, 20, List.of(SortOrder.asc("unknownField")));

        // when
        PageResult<Problem> defaultResult =
                problemQueryRepository.searchProblems(
                        emptyCondition(),
                        defaultPageQuery
                );

        PageResult<Problem> unsupportedResult =
                problemQueryRepository.searchProblems(
                        emptyCondition(),
                        unsupportedPageQuery
                );

        // then
        assertThat(
                unsupportedResult.content()
        )
                .extracting(
                        Problem::getId
                )
                .containsExactlyElementsOf(
                        defaultResult
                                .content()
                                .stream()
                                .map(
                                        Problem::getId
                                )
                                .toList()
                );
    }

    @Test
    @DisplayName(
            "page와 size에 따라 문제 목록을 페이징한다"
    )
    void searchProblems_appliesPagination() {
        // given
        PageQuery pageQuery =
                PageQuery.of(1, 2, List.of(SortOrder.asc("title")));

        // when
        PageResult<Problem> result =
                problemQueryRepository.searchProblems(
                        emptyCondition(),
                        pageQuery
                );

        // then
        assertThat(result.page())
                .isEqualTo(1);

        assertThat(result.size())
                .isEqualTo(2);

        assertThat(result.content())
                .hasSize(2);

        assertThat(result.totalElements())
                .isEqualTo(4);

        assertThat(result.totalPages())
                .isEqualTo(2);

        assertThat(result.hasPrevious())
                .isTrue();

        assertThat(result.hasNext())
                .isFalse();
    }

    @Test
    @DisplayName(
            "soft delete된 문제는 검색 결과에서 제외한다"
    )
    void searchProblems_excludesSoftDeletedProblem() {
        // given
        ProblemSearchCondition condition =
                emptyCondition();

        PageQuery pageQuery =
                PageQuery.of(0, 20);

        // when
        PageResult<Problem> result =
                problemQueryRepository.searchProblems(
                        condition,
                        pageQuery
                );

        // then
        assertThat(result.content())
                .extracting(
                        Problem::getId
                )
                .doesNotContain(
                        deletedProblem.getId()
                );

        assertThat(result.totalElements())
                .isEqualTo(4);
    }

    @Test
    @DisplayName("이슈 #291 — 레슨 ID 조건으로 문제를 검색한다")
    void searchProblems_filtersByLessonId() {
        // given
        // FK 제약을 만족하도록 실제 Curriculum → Unit → Lesson 픽스처를 생성한다.
        Curriculum curriculum = Curriculum.create("Java 기본 과정", ProgrammingLanguage.JAVA);
        entityManager.persist(curriculum);

        Unit unit = Unit.create(curriculum.getId(), "자료구조", ProgrammingLanguage.JAVA, 1);
        entityManager.persist(unit);

        Lesson lesson = Lesson.create(
                unit.getId(), "스택과 큐", "설명", "내용", ProgrammingLanguage.JAVA, 1);
        entityManager.persist(lesson);

        entityManager.flush();

        UUID targetLessonId = lesson.getId();

        // setUp() 이후 detached 상태이므로 다시 조회해 managed 상태에서 수정한다.
        Problem managedJavaEasy = entityManager.find(Problem.class, javaEasy.getId());
        managedJavaEasy.changeLessonId(targetLessonId);
        entityManager.flush();
        entityManager.clear();

        ProblemSearchCondition condition = conditionWithLessonId(targetLessonId);

        PageQuery pageQuery = PageQuery.of(0, 20);

        // when
        PageResult<Problem> result = problemQueryRepository.searchProblems(condition, pageQuery);

        // then
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).getId()).isEqualTo(javaEasy.getId());
    }

    @Test
    @DisplayName("이슈 #291 — 레슨에 연결되지 않은 문제는 lessonId 조건에서 제외된다")
    void searchProblems_filtersByLessonId_excludesUnassignedProblems() {
        // given
        UUID targetLessonId = UUID.randomUUID();
        // javaEasy, javaHard 등 어떤 문제도 이 레슨에 연결하지 않는다.

        ProblemSearchCondition condition = conditionWithLessonId(targetLessonId);

        PageQuery pageQuery = PageQuery.of(0, 20);

        // when
        PageResult<Problem> result = problemQueryRepository.searchProblems(condition, pageQuery);

        // then
        assertThat(result.content()).isEmpty();
    }

    private ProblemSearchCondition emptyCondition() {
        return condition(
                null,
                null,
                null,
                null
        );
    }

    private ProblemSearchCondition condition(
            ProgrammingLanguage language,
            ProblemDifficulty difficulty,
            ProblemSource source,
            ProblemStatus problemStatus
    ) {
        return conditionWithLessonId(language, difficulty, source, problemStatus, null);
    }

    private ProblemSearchCondition conditionWithLessonId(UUID lessonId) {
        return conditionWithLessonId(null, null, null, null, lessonId);
    }

    private ProblemSearchCondition conditionWithLessonId(
            ProgrammingLanguage language,
            ProblemDifficulty difficulty,
            ProblemSource source,
            ProblemStatus problemStatus,
            UUID lessonId
    ) {
        return new ProblemSearchCondition(
                language,
                difficulty,
                ProblemType.CODE,
                source,
                problemStatus,
                lessonId
        );
    }

    private Problem saveProblem(
            String title,
            ProgrammingLanguage language,
            ProblemDifficulty difficulty,
            ProblemSource source,
            ProblemStatus problemStatus
    ) {
        Problem problem =
                Problem.create(
                        title,
                        language,
                        difficulty,
                        ProblemType.CODE,
                        title + "에 대한 문제 지문입니다.",
                        "public class Main {}",
                        RunningTimeLimit.SECOND_1,
                        RunningMemoryLimit.MB_128,
                        TimerPolicy.APPLY60,
                        source,
                        problemStatus
                );

        entityManager.persist(
                problem
        );

        return problem;
    }
}
