package com.maesamco.content.integration;

import com.maesamco.content.application.finder_service.CurriculumFinderService;
import com.maesamco.content.application.finder_service.LessonFinderService;
import com.maesamco.content.application.finder_service.UnitFinderService;
import com.maesamco.content.application.persistence_service.LessonService;
import com.maesamco.content.application.persistence_service.UnitService;
import com.maesamco.content.domain.entity.Curriculum;
import com.maesamco.content.domain.entity.ProgrammingLanguage;
import com.maesamco.content.domain.repository.problem.ProblemQueryRepository;
import com.maesamco.content.domain.repository.problem.ProblemTagRepository;
import com.maesamco.content.global.config.JpaAuditingConfig;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import com.maesamco.content.infrastructure.persistence.CurriculumRepositoryImpl;
import com.maesamco.content.infrastructure.persistence.LessonRepositoryImpl;
import com.maesamco.content.infrastructure.persistence.UnitRepositoryImpl;
import com.maesamco.content.presentation.request.LessonCreateRequest;
import com.maesamco.content.presentation.request.LessonUpdateRequest;
import com.maesamco.content.presentation.request.UnitCreateRequest;
import com.maesamco.content.presentation.request.UnitUpdateRequest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit/Lesson displayOrder의 동시 생성과 서버 측 재정렬을 실제 PostgreSQL에서 검증합니다(#324).
 *
 * <p>V21의 부분 UNIQUE 인덱스(활성 형제끼리 display_order 중복 금지)가 걸린 상태에서
 * 서비스 트랜잭션을 그대로 커밋해 확인합니다.</p>
 */
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
        UnitService.class,
        LessonService.class,
        CurriculumFinderService.class,
        UnitFinderService.class,
        LessonFinderService.class,
        CurriculumRepositoryImpl.class,
        UnitRepositoryImpl.class,
        LessonRepositoryImpl.class
})
@EnableJpaRepositories(
        basePackages =
                "com.maesamco.content.infrastructure.persistence"
)
@Transactional(
        propagation = Propagation.NOT_SUPPORTED
)
@DisplayName("Unit/Lesson displayOrder 동시 생성·재정렬 통합 테스트 (#324)")
class UnitLessonDisplayOrderIntegrationTest {

    private static final int CONCURRENT_REQUESTS = 10;

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
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private UnitService unitService;

    @Autowired
    private LessonService lessonService;

    // LessonService의 개념 조회용 의존성 — 이 테스트에서는 사용하지 않습니다.
    @MockitoBean
    private ProblemQueryRepository problemQueryRepository;

    @MockitoBean
    private ProblemTagRepository problemTagRepository;

    @Nested
    @DisplayName("동시 생성")
    class ConcurrentCreate {

        @Test
        @DisplayName("같은 Curriculum 아래 Unit N개를 동시에 생성하면 display_order가 1..N으로 중복 없이 저장된다")
        void createUnits_concurrently_assignsSequentialOrders() throws Exception {
            // given
            UUID curriculumId = createCurriculum();

            // when
            List<Throwable> failures = runConcurrently(
                    CONCURRENT_REQUESTS,
                    index -> () -> unitService.createUnit(
                            unitCreateRequest(curriculumId, "동시 유닛 " + index)
                    )
            );

            // then
            assertThat(failures).isEmpty();
            assertThat(activeUnitOrders(curriculumId))
                    .containsExactlyElementsOf(sequence(CONCURRENT_REQUESTS));
        }

        @Test
        @DisplayName("같은 Unit 아래 Lesson N개를 동시에 생성하면 display_order가 1..N으로 중복 없이 저장된다")
        void createLessons_concurrently_assignsSequentialOrders() throws Exception {
            // given
            UUID curriculumId = createCurriculum();
            UUID unitId = createUnit(curriculumId, "레슨 부모 유닛");

            // when
            List<Throwable> failures = runConcurrently(
                    CONCURRENT_REQUESTS,
                    index -> () -> lessonService.createLesson(
                            lessonCreateRequest(unitId, "동시 레슨 " + index)
                    )
            );

            // then
            assertThat(failures).isEmpty();
            assertThat(activeLessonOrders(unitId))
                    .containsExactlyElementsOf(sequence(CONCURRENT_REQUESTS));
        }
    }

    @Nested
    @DisplayName("Unit 순서 변경")
    class MoveUnit {

        @Test
        @DisplayName("Unit 삭제 직후 활성 번호를 압축하고, 응답 번호를 옮길 자리로 사용할 수 있다")
        void deleteUnit_compactsOrdersBeforeNextMoveAndCreate() {
            UUID curriculumId = createCurriculum();
            List<UUID> units = createUnits(curriculumId, 4);

            unitService.deleteUnit(units.get(1), UUID.randomUUID());

            assertThat(activeUnitIdsInOrder(curriculumId))
                    .containsExactly(units.get(0), units.get(2), units.get(3));
            assertThat(activeUnitOrders(curriculumId)).containsExactly(1, 2, 3);
            assertThat(displayOrderOf("p_units", "unit_id", units.get(1))).isEqualTo(2);

            // D의 응답 번호 3을 그대로 사용하면 A가 D 뒤로 이동한다.
            unitService.updateUnit(units.get(0), unitDisplayOrderRequest(3));
            assertThat(activeUnitIdsInOrder(curriculumId))
                    .containsExactly(units.get(2), units.get(3), units.get(0));
            assertThat(activeUnitOrders(curriculumId)).containsExactly(1, 2, 3);

            createUnit(curriculumId, "새 유닛");
            assertThat(activeUnitOrders(curriculumId)).containsExactly(1, 2, 3, 4);
        }

        @Test
        @DisplayName("다른 Unit이 쓰고 있는 번호로 옮기면 사이의 Unit이 한 칸씩 밀려 1..N으로 저장된다")
        void moveUnit_toOccupiedOrder_shiftsSiblings() {
            // given — [A1 B2 C3 D4 E5]
            UUID curriculumId = createCurriculum();
            List<UUID> units = createUnits(curriculumId, 5);

            // when — E를 2로
            unitService.updateUnit(units.get(4), unitDisplayOrderRequest(2));

            // then — [A1 E2 B3 C4 D5]
            assertThat(activeUnitIdsInOrder(curriculumId)).containsExactly(
                    units.get(0), units.get(4), units.get(1), units.get(2), units.get(3)
            );
            assertThat(activeUnitOrders(curriculumId)).containsExactlyElementsOf(sequence(5));
        }

        @Test
        @DisplayName("뒤쪽 번호로 옮기면 사이의 Unit이 한 칸씩 당겨진다")
        void moveUnit_backward_pullsSiblings() {
            // given — [A1 B2 C3 D4]
            UUID curriculumId = createCurriculum();
            List<UUID> units = createUnits(curriculumId, 4);

            // when — A를 3으로
            unitService.updateUnit(units.get(0), unitDisplayOrderRequest(3));

            // then — [B1 C2 A3 D4]
            assertThat(activeUnitIdsInOrder(curriculumId)).containsExactly(
                    units.get(1), units.get(2), units.get(0), units.get(3)
            );
        }

        @Test
        @DisplayName("범위를 벗어나면 UNIT_DISPLAY_ORDER_OUT_OF_RANGE로 거절되고, 같은 요청의 제목 변경까지 롤백된다")
        void moveUnit_outOfRange_rollsBackWholeRequest() {
            // given
            UUID curriculumId = createCurriculum();
            List<UUID> units = createUnits(curriculumId, 3);

            UnitUpdateRequest request = unitDisplayOrderRequest(4);
            ReflectionTestUtils.setField(request, "title", "롤백되어야 하는 제목");

            // when & then
            assertThatThrownBy(() -> unitService.updateUnit(units.get(0), request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(exception -> ((BusinessException) exception).getErrorCode())
                    .isEqualTo(ErrorCode.UNIT_DISPLAY_ORDER_OUT_OF_RANGE);

            assertThat(activeUnitIdsInOrder(curriculumId)).containsExactlyElementsOf(units);
            assertThat(unitTitle(units.get(0))).isEqualTo("유닛 1");
        }

        @Test
        @DisplayName("삭제된 Unit은 재정렬 대상에서 빠지고 번호가 유지되며, 활성 Unit의 빈 번호는 1..N으로 채워진다")
        void moveUnit_withSoftDeletedSibling_compactsActiveOnly() {
            // given — [A1 B2 C3 D4] 에서 B 삭제 → 활성 [A1 C3 D4]
            UUID curriculumId = createCurriculum();
            List<UUID> units = createUnits(curriculumId, 4);
            unitService.deleteUnit(units.get(1), UUID.randomUUID());

            // when — D를 1로
            unitService.updateUnit(units.get(3), unitDisplayOrderRequest(1));

            // then — 활성 [D1 A2 C3], 삭제된 B는 2 그대로
            assertThat(activeUnitIdsInOrder(curriculumId)).containsExactly(
                    units.get(3), units.get(0), units.get(2)
            );
            assertThat(activeUnitOrders(curriculumId)).containsExactlyElementsOf(sequence(3));
            assertThat(displayOrderOf("p_units", "unit_id", units.get(1))).isEqualTo(2);
        }

        @Test
        @DisplayName("같은 Curriculum에서 순서 변경이 동시에 들어와도 부모 잠금으로 직렬화되어 UNIQUE 충돌 없이 1..N을 유지한다")
        void moveUnits_concurrently_keepsUniqueSequentialOrders() throws Exception {
            // given
            UUID curriculumId = createCurriculum();
            List<UUID> units = createUnits(curriculumId, 5);

            // when — 서로 다른 Unit을 동시에 앞/뒤로 옮긴다
            List<Throwable> failures = runConcurrently(
                    4,
                    index -> () -> unitService.updateUnit(
                            units.get(index),
                            unitDisplayOrderRequest(5 - index)
                    )
            );

            // then
            assertThat(failures).isEmpty();
            assertThat(activeUnitOrders(curriculumId)).containsExactlyElementsOf(sequence(5));
            assertThat(activeUnitIdsInOrder(curriculumId)).containsExactlyInAnyOrderElementsOf(units);
        }
    }

    @Nested
    @DisplayName("Lesson 순서 변경")
    class MoveLesson {

        @Test
        @DisplayName("Lesson 삭제 직후 활성 번호를 압축하고, 응답 번호를 옮길 자리로 사용할 수 있다")
        void deleteLesson_compactsOrdersBeforeNextMoveAndCreate() {
            UUID unitId = createUnit(createCurriculum(), "레슨 부모 유닛");
            List<UUID> lessons = createLessons(unitId, 4);

            lessonService.deleteLesson(lessons.get(1), UUID.randomUUID());

            assertThat(activeLessonIdsInOrder(unitId))
                    .containsExactly(lessons.get(0), lessons.get(2), lessons.get(3));
            assertThat(activeLessonOrders(unitId)).containsExactly(1, 2, 3);
            assertThat(displayOrderOf("p_lessons", "lesson_id", lessons.get(1))).isEqualTo(2);

            lessonService.updateLesson(lessons.get(0), lessonDisplayOrderRequest(3));
            assertThat(activeLessonIdsInOrder(unitId))
                    .containsExactly(lessons.get(2), lessons.get(3), lessons.get(0));
            assertThat(activeLessonOrders(unitId)).containsExactly(1, 2, 3);

            lessonService.createLesson(lessonCreateRequest(unitId, "새 레슨"));
            assertThat(activeLessonOrders(unitId)).containsExactly(1, 2, 3, 4);
        }

        @Test
        @DisplayName("다른 Lesson이 쓰고 있는 번호로 옮기면 사이의 Lesson이 한 칸씩 밀려 1..N으로 저장된다")
        void moveLesson_toOccupiedOrder_shiftsSiblings() {
            // given — [A1 B2 C3 D4]
            UUID unitId = createUnit(createCurriculum(), "레슨 부모 유닛");
            List<UUID> lessons = createLessons(unitId, 4);

            // when — D를 1로
            lessonService.updateLesson(lessons.get(3), lessonDisplayOrderRequest(1));

            // then — [D1 A2 B3 C4]
            assertThat(activeLessonIdsInOrder(unitId)).containsExactly(
                    lessons.get(3), lessons.get(0), lessons.get(1), lessons.get(2)
            );
            assertThat(activeLessonOrders(unitId)).containsExactlyElementsOf(sequence(4));
        }

        @Test
        @DisplayName("범위를 벗어나면 LESSON_DISPLAY_ORDER_OUT_OF_RANGE로 거절되고, 같은 요청의 제목 변경까지 롤백된다")
        void moveLesson_outOfRange_rollsBackWholeRequest() {
            // given
            UUID unitId = createUnit(createCurriculum(), "레슨 부모 유닛");
            List<UUID> lessons = createLessons(unitId, 2);

            LessonUpdateRequest request = lessonDisplayOrderRequest(3);
            ReflectionTestUtils.setField(request, "title", "롤백되어야 하는 제목");

            // when & then
            assertThatThrownBy(() -> lessonService.updateLesson(lessons.get(1), request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(exception -> ((BusinessException) exception).getErrorCode())
                    .isEqualTo(ErrorCode.LESSON_DISPLAY_ORDER_OUT_OF_RANGE);

            assertThat(activeLessonIdsInOrder(unitId)).containsExactlyElementsOf(lessons);
            assertThat(lessonTitle(lessons.get(1))).isEqualTo("레슨 2");
        }
    }

    // ---------------------------------------------------------------------
    // fixtures
    // ---------------------------------------------------------------------

    private UUID createCurriculum() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Curriculum curriculum = Curriculum.create("커리큘럼", ProgrammingLanguage.JAVA);
            entityManager.persist(curriculum);
            entityManager.flush();
            return curriculum.getId();
        });
    }

    private UUID createUnit(UUID curriculumId, String title) {
        return unitService.createUnit(unitCreateRequest(curriculumId, title)).getId();
    }

    private List<UUID> createUnits(UUID curriculumId, int count) {
        List<UUID> ids = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            ids.add(createUnit(curriculumId, "유닛 " + index));
        }
        return ids;
    }

    private List<UUID> createLessons(UUID unitId, int count) {
        List<UUID> ids = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            ids.add(lessonService.createLesson(lessonCreateRequest(unitId, "레슨 " + index)).getId());
        }
        return ids;
    }

    private UnitCreateRequest unitCreateRequest(UUID curriculumId, String title) {
        UnitCreateRequest request = new UnitCreateRequest();
        ReflectionTestUtils.setField(request, "curriculumId", curriculumId);
        ReflectionTestUtils.setField(request, "title", title);
        ReflectionTestUtils.setField(request, "language", ProgrammingLanguage.JAVA);
        ReflectionTestUtils.setField(request, "displayOrder", 1);
        return request;
    }

    private LessonCreateRequest lessonCreateRequest(UUID unitId, String title) {
        LessonCreateRequest request = new LessonCreateRequest();
        ReflectionTestUtils.setField(request, "unitId", unitId);
        ReflectionTestUtils.setField(request, "title", title);
        ReflectionTestUtils.setField(request, "description", "설명");
        ReflectionTestUtils.setField(request, "content", "내용");
        ReflectionTestUtils.setField(request, "language", ProgrammingLanguage.JAVA);
        return request;
    }

    private UnitUpdateRequest unitDisplayOrderRequest(int displayOrder) {
        UnitUpdateRequest request = new UnitUpdateRequest();
        ReflectionTestUtils.setField(request, "displayOrder", displayOrder);
        return request;
    }

    private LessonUpdateRequest lessonDisplayOrderRequest(int displayOrder) {
        LessonUpdateRequest request = new LessonUpdateRequest();
        ReflectionTestUtils.setField(request, "displayOrder", displayOrder);
        return request;
    }

    // ---------------------------------------------------------------------
    // concurrency
    // ---------------------------------------------------------------------

    private List<Throwable> runConcurrently(
            int count,
            java.util.function.IntFunction<Callable<?>> taskFactory
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<Throwable>> futures = new ArrayList<>();

            for (int index = 0; index < count; index++) {
                Callable<?> task = taskFactory.apply(index);

                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        task.call();
                        return null;
                    } catch (Throwable failure) {
                        return failure;
                    }
                }));
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Throwable> failures = new ArrayList<>();
            for (Future<Throwable> future : futures) {
                Throwable failure = future.get(30, TimeUnit.SECONDS);
                if (failure != null) {
                    failures.add(failure);
                }
            }
            return failures;
        } finally {
            executor.shutdownNow();
        }
    }

    // ---------------------------------------------------------------------
    // assertions
    // ---------------------------------------------------------------------

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource);
    }

    private List<Integer> activeUnitOrders(UUID curriculumId) {
        return jdbc().queryForList(
                """
                SELECT display_order FROM content_schema.p_units
                WHERE curriculum_id = ? AND deleted_at IS NULL
                ORDER BY display_order
                """,
                Integer.class,
                curriculumId
        );
    }

    private List<UUID> activeUnitIdsInOrder(UUID curriculumId) {
        return jdbc().queryForList(
                """
                SELECT unit_id FROM content_schema.p_units
                WHERE curriculum_id = ? AND deleted_at IS NULL
                ORDER BY display_order
                """,
                UUID.class,
                curriculumId
        );
    }

    private List<Integer> activeLessonOrders(UUID unitId) {
        return jdbc().queryForList(
                """
                SELECT display_order FROM content_schema.p_lessons
                WHERE unit_id = ? AND deleted_at IS NULL
                ORDER BY display_order
                """,
                Integer.class,
                unitId
        );
    }

    private List<UUID> activeLessonIdsInOrder(UUID unitId) {
        return jdbc().queryForList(
                """
                SELECT lesson_id FROM content_schema.p_lessons
                WHERE unit_id = ? AND deleted_at IS NULL
                ORDER BY display_order
                """,
                UUID.class,
                unitId
        );
    }

    private Integer displayOrderOf(String table, String idColumn, UUID id) {
        return jdbc().queryForObject(
                "SELECT display_order FROM content_schema." + table + " WHERE " + idColumn + " = ?",
                Integer.class,
                id
        );
    }

    private String unitTitle(UUID unitId) {
        return jdbc().queryForObject(
                "SELECT title FROM content_schema.p_units WHERE unit_id = ?",
                String.class,
                unitId
        );
    }

    private String lessonTitle(UUID lessonId) {
        return jdbc().queryForObject(
                "SELECT title FROM content_schema.p_lessons WHERE lesson_id = ?",
                String.class,
                lessonId
        );
    }

    private static List<Integer> sequence(int count) {
        return IntStream.rangeClosed(1, count).boxed().toList();
    }
}
