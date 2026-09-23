package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.entity.ProgrammingLanguage;
import com.maesamco.content.domain.entity.problem.Problem;
import com.maesamco.content.domain.entity.problem.ProblemDifficulty;
import com.maesamco.content.domain.entity.problem.ProblemSource;
import com.maesamco.content.domain.entity.problem.ProblemStatus;
import com.maesamco.content.domain.entity.problem.ProblemType;
import com.maesamco.content.domain.entity.problem.RunningMemoryLimit;
import com.maesamco.content.domain.entity.problem.RunningTimeLimit;
import com.maesamco.content.domain.entity.problem.TimerPolicy;
import com.maesamco.content.domain.repository.problem.ProblemQueryRepository;
import com.maesamco.content.domain.repository.problem.ProblemSearchCondition;
import com.maesamco.content.global.config.JpaAuditingConfig;
import com.maesamco.content.global.config.QuerydslConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.mock;

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

        Pageable pageable =
                PageRequest.of(
                        0,
                        20
                );

        // when
        Page<Problem> result =
                problemQueryRepository.searchProblems(
                        condition,
                        pageable
                );

        // then
        assertThat(result.getContent())
                .hasSize(4);

        assertThat(result.getContent())
                .extracting(
                        Problem::getId
                )
                .containsExactlyInAnyOrder(
                        javaEasy.getId(),
                        javaHard.getId(),
                        pythonMedium.getId(),
                        cEasy.getId()
                );

        assertThat(result.getTotalElements())
                .isEqualTo(4);
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

        Pageable pageable =
                PageRequest.of(
                        0,
                        20
                );

        // when
        Page<Problem> result =
                problemQueryRepository.searchProblems(
                        condition,
                        pageable
                );

        // then
        assertThat(result.getContent())
                .hasSize(2);

        assertThat(result.getContent())
                .extracting(
                        Problem::getLanguage
                )
                .containsOnly(
                        ProgrammingLanguage.JAVA
                );

        assertThat(result.getContent())
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

        Pageable pageable =
                PageRequest.of(
                        0,
                        20
                );

        // when
        Page<Problem> result =
                problemQueryRepository.searchProblems(
                        condition,
                        pageable
                );

        // then
        assertThat(result.getContent())
                .hasSize(2);

        assertThat(result.getContent())
                .extracting(
                        Problem::getDifficulty
                )
                .containsOnly(
                        ProblemDifficulty.EASY
                );

        assertThat(result.getContent())
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

        Pageable pageable =
                PageRequest.of(
                        0,
                        20
                );

        // when
        Page<Problem> result =
                problemQueryRepository.searchProblems(
                        condition,
                        pageable
                );

        // then
        assertThat(result.getContent())
                .hasSize(1);

        Problem found =
                result.getContent()
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

        Pageable pageable =
                PageRequest.of(
                        0,
                        20
                );

        // when
        Page<Problem> result =
                problemQueryRepository.searchProblems(
                        condition,
                        pageable
                );

        // then
        /*
         * deletedProblem 역시 HUMAN_AUTHORED이지만
         * soft delete 상태이므로 조회 결과에서 제외된다.
         */
        assertThat(result.getContent())
                .hasSize(1);

        assertThat(
                result.getContent()
                        .get(0)
                        .getId()
        ).isEqualTo(
                javaEasy.getId()
        );

        assertThat(
                result.getContent()
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

        Pageable pageable =
                PageRequest.of(
                        0,
                        20
                );

        // when
        Page<Problem> result =
                problemQueryRepository.searchProblems(
                        condition,
                        pageable
                );

        // then
        assertThat(result.getContent())
                .hasSize(2);

        assertThat(result.getContent())
                .extracting(
                        Problem::getProblemStatus
                )
                .containsOnly(
                        ProblemStatus.PUBLISHED
                );

        assertThat(result.getContent())
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

        Pageable pageable =
                PageRequest.of(
                        0,
                        20
                );

        // when
        Page<Problem> result =
                problemQueryRepository.searchProblems(
                        condition,
                        pageable
                );

        // then
        assertThat(result.getContent())
                .hasSize(1);

        Problem found =
                result.getContent()
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

        Pageable pageable =
                PageRequest.of(
                        0,
                        20
                );

        // when
        Page<Problem> result =
                problemQueryRepository.searchProblems(
                        condition,
                        pageable
                );

        // then
        assertThat(result.getContent())
                .isEmpty();

        assertThat(result.getTotalElements())
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

        Pageable pageable =
                PageRequest.of(
                        0,
                        20
                );

        // when
        Page<Problem> result =
                problemQueryRepository.searchProblems(
                        condition,
                        pageable
                );

        // then
        assertThat(result.getContent())
                .isNotEmpty();

        assertThat(result.getContent())
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
        Pageable pageable =
                PageRequest.of(
                        0,
                        20,
                        Sort.by(
                                Sort.Order.asc(
                                        "title"
                                )
                        )
                );

        // when
        Page<Problem> result =
                problemQueryRepository.searchProblems(
                        emptyCondition(),
                        pageable
                );

        // then
        assertThat(result.getContent())
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
        Pageable pageable =
                PageRequest.of(
                        0,
                        20,
                        Sort.by(
                                Sort.Order.asc(
                                        "difficulty"
                                )
                        )
                );

        // when
        Page<Problem> result =
                problemQueryRepository.searchProblems(
                        emptyCondition(),
                        pageable
                );

        // then
        assertThat(result.getContent())
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
        Pageable pageable =
                PageRequest.of(
                        0,
                        20,
                        Sort.by(
                                Sort.Order.desc(
                                        "difficulty"
                                )
                        )
                );

        // when
        Page<Problem> result =
                problemQueryRepository.searchProblems(
                        emptyCondition(),
                        pageable
                );

        // then
        assertThat(result.getContent())
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
        Pageable pageable =
                PageRequest.of(
                        0,
                        20,
                        Sort.by(
                                Sort.Order.asc(
                                        "title"
                                ),
                                Sort.Order.desc(
                                        "difficulty"
                                )
                        )
                );

        // when
        Page<Problem> result =
                problemQueryRepository.searchProblems(
                        emptyCondition(),
                        pageable
                );

        // then
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
    @DisplayName(
            "지원하지 않는 정렬 필드는 무시하고 기본 정렬을 적용한다"
    )
    void searchProblems_unsupportedSort_usesDefaultSort() {
        // given
        Pageable defaultPageable =
                PageRequest.of(
                        0,
                        20
                );

        Pageable unsupportedPageable =
                PageRequest.of(
                        0,
                        20,
                        Sort.by(
                                "unknownField"
                        )
                );

        // when
        Page<Problem> defaultResult =
                problemQueryRepository.searchProblems(
                        emptyCondition(),
                        defaultPageable
                );

        Page<Problem> unsupportedResult =
                problemQueryRepository.searchProblems(
                        emptyCondition(),
                        unsupportedPageable
                );

        // then
        assertThat(
                unsupportedResult.getContent()
        )
                .extracting(
                        Problem::getId
                )
                .containsExactlyElementsOf(
                        defaultResult
                                .getContent()
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
        Pageable pageable =
                PageRequest.of(
                        1,
                        2,
                        Sort.by(
                                Sort.Order.asc(
                                        "title"
                                )
                        )
                );

        // when
        Page<Problem> result =
                problemQueryRepository.searchProblems(
                        emptyCondition(),
                        pageable
                );

        // then
        assertThat(result.getNumber())
                .isEqualTo(1);

        assertThat(result.getSize())
                .isEqualTo(2);

        assertThat(result.getContent())
                .hasSize(2);

        assertThat(result.getTotalElements())
                .isEqualTo(4);

        assertThat(result.getTotalPages())
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

        Pageable pageable =
                PageRequest.of(
                        0,
                        20
                );

        // when
        Page<Problem> result =
                problemQueryRepository.searchProblems(
                        condition,
                        pageable
                );

        // then
        assertThat(result.getContent())
                .extracting(
                        Problem::getId
                )
                .doesNotContain(
                        deletedProblem.getId()
                );

        assertThat(result.getTotalElements())
                .isEqualTo(4);
    }

    @Test
    @DisplayName("이슈 #291 — 레슨 ID 조건으로 문제를 검색한다")
    void searchProblems_filtersByLessonId() {
        // given
        UUID targetLessonId = UUID.randomUUID();

        javaEasy.changeLessonId(targetLessonId);
        entityManager.flush();
        entityManager.clear();

        ProblemSearchCondition condition = conditionWithLessonId(targetLessonId);

        Pageable pageable = PageRequest.of(0, 20);

        // when
        Page<Problem> result = problemQueryRepository.searchProblems(condition, pageable);

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(javaEasy.getId());
    }

    @Test
    @DisplayName("이슈 #291 — 레슨에 연결되지 않은 문제는 lessonId 조건에서 제외된다")
    void searchProblems_filtersByLessonId_excludesUnassignedProblems() {
        // given
        UUID targetLessonId = UUID.randomUUID();
        // javaEasy, javaHard 등 어떤 문제도 이 레슨에 연결하지 않는다.

        ProblemSearchCondition condition = conditionWithLessonId(targetLessonId);

        Pageable pageable = PageRequest.of(0, 20);

        // when
        Page<Problem> result = problemQueryRepository.searchProblems(condition, pageable);

        // then
        assertThat(result.getContent()).isEmpty();
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
        return mock(
                ProblemSearchCondition.class,
                invocation -> {

                    String methodName =
                            invocation
                                    .getMethod()
                                    .getName();

                    return switch (methodName) {

                        case "language",
                             "getLanguage" ->
                                language;

                        case "difficulty",
                             "getDifficulty" ->
                                difficulty;

                        /*
                         * 현재 ProblemType은 CODE만 사용하므로
                         * 모든 검색 조건에서도 CODE를 반환한다.
                         */
                        case "type",
                             "getType" ->
                                ProblemType.CODE;

                        case "source",
                             "getSource" ->
                                source;

                        case "problemStatus",
                             "getProblemStatus",
                             "status",
                             "getStatus" ->
                                problemStatus;

                        case "lessonId",
                             "getLessonId" ->
                                lessonId;

                        default ->
                                Answers.RETURNS_DEFAULTS
                                        .answer(
                                                invocation
                                        );
                    };
                }
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