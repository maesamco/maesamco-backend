package com.maesamco.content.integration;

import com.maesamco.content.application.command.CurriculumUpdateCommand;
import com.maesamco.content.application.finder_service.CurriculumFinderService;
import com.maesamco.content.application.finder_service.LessonFinderService;
import com.maesamco.content.application.finder_service.UnitFinderService;
import com.maesamco.content.application.persistence_service.CurriculumService;
import com.maesamco.content.application.persistence_service.LessonService;
import com.maesamco.content.application.persistence_service.UnitService;
import com.maesamco.content.domain.entity.Curriculum;
import com.maesamco.content.domain.entity.Lesson;
import com.maesamco.content.domain.entity.ProgrammingLanguage;
import com.maesamco.content.domain.entity.Unit;
import com.maesamco.content.domain.repository.problem.ProblemQueryRepository;
import com.maesamco.content.domain.repository.problem.ProblemTagRepository;
import com.maesamco.content.global.config.JpaAuditingConfig;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import com.maesamco.content.infrastructure.persistence.CurriculumRepositoryImpl;
import com.maesamco.content.infrastructure.persistence.LessonRepositoryImpl;
import com.maesamco.content.infrastructure.persistence.UnitRepositoryImpl;
import com.maesamco.content.presentation.request.LessonUpdateRequest;
import com.maesamco.content.presentation.request.UnitUpdateRequest;
import jakarta.persistence.EntityManager;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 학습 콘텐츠 쓰기 경로(수정·공개 전환·삭제)가 서로의 변경을 덮어쓰지 않는지 실제 PostgreSQL에서 검증합니다(#366 리뷰 P2).
 *
 * <p>먼저 시작한 쓰기(first)가 트랜잭션을 연 채 잠시 머무는 동안 두 번째 쓰기(second)를 실행합니다.
 * 쓰기 경로가 부모 락을 잡지 않으면 second가 먼저 커밋되고, 이후 first가 락 없이 읽어 둔 오래된 값
 * (공개 상태, deleted_at 등)으로 행 전체를 다시 써서 second의 변경이 사라집니다.
 * 부모 락 + 잠금 이후 재조회가 있으면 second는 first의 커밋을 기다렸다가 최신 상태 위에서 실행됩니다.</p>
 */
@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.default_schema=content_schema",
        "spring.data.jpa.repositories.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import({
        JpaAuditingConfig.class,
        CurriculumService.class,
        UnitService.class,
        LessonService.class,
        CurriculumFinderService.class,
        UnitFinderService.class,
        LessonFinderService.class,
        CurriculumRepositoryImpl.class,
        UnitRepositoryImpl.class,
        LessonRepositoryImpl.class
})
@EnableJpaRepositories(basePackages = "com.maesamco.content.infrastructure.persistence")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("학습 콘텐츠 쓰기 경로 동시성 통합 테스트 (#366)")
class ContentWriteConcurrencyIntegrationTest {

    /** first가 트랜잭션을 연 채 머무는 시간. 락이 없으면 이 사이에 second가 먼저 커밋된다. */
    private static final long HOLD_MILLIS = 700;

    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

    static {
        postgres.start();
    }

    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private DataSource dataSource;
    @Autowired private CurriculumService curriculumService;
    @Autowired private UnitService unitService;
    @Autowired private LessonService lessonService;

    // LessonService의 개념 조회용 의존성 — 이 테스트에서는 사용하지 않습니다.
    @MockitoBean private ProblemQueryRepository problemQueryRepository;
    @MockitoBean private ProblemTagRepository problemTagRepository;

    @Test
    @DisplayName("레슨 수정 중에 비공개가 들어오면, 비공개는 수정 커밋을 기다렸다가 적용되어 두 변경이 모두 남는다")
    void unpublishDuringLessonUpdate_keepsBothChanges() throws Exception {
        // given
        Ids ids = createHierarchy(true);

        // when
        Outcome outcome = runWhileFirstHoldsTransaction(
                () -> lessonService.updateLesson(ids.lessonId(), lessonTitleRequest("수정본")),
                () -> lessonService.unpublishLesson(ids.lessonId())
        );

        // then — 락이 없으면 수정 트랜잭션이 읽어 둔 PUBLISHED로 되돌려 쓴다
        outcome.assertNoFailures();
        Map<String, Object> lesson = row("p_lessons", "lesson_id", ids.lessonId());
        assertThat(lesson.get("status")).isEqualTo("DRAFT");
        assertThat(lesson.get("title")).isEqualTo("수정본");
    }

    @Test
    @DisplayName("레슨 공개 중에 삭제가 들어오면, 삭제는 공개 커밋을 기다렸다가 적용되어 삭제된 행이 되살아나지 않는다")
    void deleteDuringLessonPublish_doesNotResurrectRow() throws Exception {
        // given
        Ids ids = createHierarchy(false);

        // when
        Outcome outcome = runWhileFirstHoldsTransaction(
                () -> lessonService.publishLesson(ids.lessonId()),
                () -> lessonService.deleteLesson(ids.lessonId(), UUID.randomUUID())
        );

        // then — 락이 없으면 공개 트랜잭션이 읽어 둔 deleted_at=NULL로 되돌려 쓴다
        outcome.assertNoFailures();
        Map<String, Object> lesson = row("p_lessons", "lesson_id", ids.lessonId());
        assertThat(lesson.get("deleted_at")).isNotNull();
        assertThat(lesson.get("status")).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("레슨 삭제 중에 공개가 들어오면, 공개는 삭제 커밋 후 최신 상태를 다시 읽어 LESSON_NOT_FOUND로 거절된다")
    void publishDuringLessonDelete_isRejectedAfterRefresh() throws Exception {
        // given
        Ids ids = createHierarchy(false);

        // when
        Outcome outcome = runWhileFirstHoldsTransaction(
                () -> lessonService.deleteLesson(ids.lessonId(), UUID.randomUUID()),
                () -> lessonService.publishLesson(ids.lessonId())
        );

        // then
        assertThat(outcome.firstFailure()).isNull();
        assertBusinessError(outcome.secondFailure(), ErrorCode.LESSON_NOT_FOUND);
        Map<String, Object> lesson = row("p_lessons", "lesson_id", ids.lessonId());
        assertThat(lesson.get("deleted_at")).isNotNull();
        assertThat(lesson.get("status")).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("유닛 수정 중에 비공개가 들어오면 두 변경이 모두 남는다")
    void unpublishDuringUnitUpdate_keepsBothChanges() throws Exception {
        // given
        Ids ids = createHierarchy(true);

        // when
        Outcome outcome = runWhileFirstHoldsTransaction(
                () -> unitService.updateUnit(ids.unitId(), unitTitleRequest("수정된 유닛")),
                () -> unitService.unpublishUnit(ids.unitId())
        );

        // then
        outcome.assertNoFailures();
        Map<String, Object> unit = row("p_units", "unit_id", ids.unitId());
        assertThat(unit.get("status")).isEqualTo("DRAFT");
        assertThat(unit.get("title")).isEqualTo("수정된 유닛");
    }

    @Test
    @DisplayName("커리큘럼 수정 중에 비공개가 들어오면 두 변경이 모두 남는다")
    void unpublishDuringCurriculumUpdate_keepsBothChanges() throws Exception {
        // given
        Ids ids = createHierarchy(true);

        // when
        Outcome outcome = runWhileFirstHoldsTransaction(
                () -> curriculumService.updateCurriculum(
                        ids.curriculumId(), new CurriculumUpdateCommand("수정된 커리큘럼", null)),
                () -> curriculumService.unpublishCurriculum(ids.curriculumId())
        );

        // then
        outcome.assertNoFailures();
        Map<String, Object> curriculum = row("p_curriculums", "curriculum_id", ids.curriculumId());
        assertThat(curriculum.get("status")).isEqualTo("DRAFT");
        assertThat(curriculum.get("title")).isEqualTo("수정된 커리큘럼");
    }

    /**
     * first를 트랜잭션 안에서 실행하고 커밋하지 않은 채 {@link #HOLD_MILLIS}만큼 머무는 동안 second를 실행합니다.
     * second는 서비스의 자체 트랜잭션으로 실행되어 커밋됩니다.
     */
    private Outcome runWhileFirstHoldsTransaction(Runnable first, Runnable second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstWrote = new CountDownLatch(1);

        try {
            // 블록 람다로 값을 반환해 submit(Callable)이 선택되도록 한다.
            Future<Throwable> firstResult = executor.submit(() -> {
                return capture(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    try {
                        first.run();
                    } finally {
                        firstWrote.countDown();
                    }
                    sleepQuietly(HOLD_MILLIS);
                }));
            });

            assertThat(firstWrote.await(10, TimeUnit.SECONDS)).isTrue();
            Future<Throwable> secondResult = executor.submit(() -> {
                return capture(second);
            });

            return new Outcome(
                    firstResult.get(30, TimeUnit.SECONDS),
                    secondResult.get(30, TimeUnit.SECONDS)
            );
        } finally {
            executor.shutdownNow();
        }
    }

    private Ids createHierarchy(boolean lessonPublished) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Curriculum curriculum = Curriculum.create("커리큘럼", ProgrammingLanguage.JAVA);
            curriculum.publish();
            entityManager.persist(curriculum);

            Unit unit = Unit.create(curriculum.getId(), "유닛", ProgrammingLanguage.JAVA, 1);
            unit.publish();
            entityManager.persist(unit);

            Lesson lesson = Lesson.create(unit.getId(), "레슨", "설명", "내용", ProgrammingLanguage.JAVA, 1);
            if (lessonPublished) {
                lesson.publish();
            }
            entityManager.persist(lesson);

            entityManager.flush();
            return new Ids(curriculum.getId(), unit.getId(), lesson.getId());
        });
    }

    private static LessonUpdateRequest lessonTitleRequest(String title) {
        LessonUpdateRequest request = new LessonUpdateRequest();
        ReflectionTestUtils.setField(request, "title", title);
        return request;
    }

    private static UnitUpdateRequest unitTitleRequest(String title) {
        UnitUpdateRequest request = new UnitUpdateRequest();
        ReflectionTestUtils.setField(request, "title", title);
        return request;
    }

    private Map<String, Object> row(String table, String idColumn, UUID id) {
        // 테이블명은 테스트의 고정 문자열만 사용합니다.
        return new JdbcTemplate(dataSource).queryForMap(
                "select status, title, deleted_at from content_schema." + table + " where " + idColumn + " = ?",
                id
        );
    }

    private static Throwable capture(Runnable action) {
        try {
            action.run();
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void assertBusinessError(Throwable failure, ErrorCode expected) {
        assertThat(failure).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }

    private record Ids(UUID curriculumId, UUID unitId, UUID lessonId) {}

    private record Outcome(Throwable firstFailure, Throwable secondFailure) {
        void assertNoFailures() {
            assertThat(firstFailure).as("first 실패").isNull();
            assertThat(secondFailure).as("second 실패").isNull();
        }
    }
}
