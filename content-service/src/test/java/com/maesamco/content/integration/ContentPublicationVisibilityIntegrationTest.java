package com.maesamco.content.integration;

import com.maesamco.content.application.finder_service.CurriculumFinderService;
import com.maesamco.content.application.finder_service.LessonFinderService;
import com.maesamco.content.application.finder_service.UnitFinderService;
import com.maesamco.content.application.persistence_service.CurriculumService;
import com.maesamco.content.application.persistence_service.LessonService;
import com.maesamco.content.application.persistence_service.UnitService;
import com.maesamco.content.application.result.CurriculumResult;
import com.maesamco.content.domain.entity.ContentStatus;
import com.maesamco.content.domain.entity.Curriculum;
import com.maesamco.content.domain.entity.Lesson;
import com.maesamco.content.domain.entity.ProgrammingLanguage;
import com.maesamco.content.domain.entity.Unit;
import com.maesamco.content.domain.repository.CurriculumRepository;
import com.maesamco.content.domain.repository.LessonRepository;
import com.maesamco.content.domain.repository.UnitRepository;
import com.maesamco.content.domain.repository.problem.ProblemQueryRepository;
import com.maesamco.content.domain.repository.problem.ProblemTagRepository;
import com.maesamco.content.global.common.pagination.PageQuery;
import com.maesamco.content.global.config.JpaAuditingConfig;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import com.maesamco.content.infrastructure.persistence.CurriculumRepositoryImpl;
import com.maesamco.content.infrastructure.persistence.LessonRepositoryImpl;
import com.maesamco.content.infrastructure.persistence.UnitRepositoryImpl;
import com.maesamco.content.presentation.response.LessonResponse;
import com.maesamco.content.presentation.response.UnitResponse;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * 학습 콘텐츠 공개 상태에 따른 학습자 조회 노출 규칙 통합 테스트(#359).
 *
 * <p>학습자 조회(...ForUser)는 본인과 상위가 모두 PUBLISHED일 때만 노출하고,
 * 그렇지 않으면 삭제와 같은 *_NOT_FOUND로 응답합니다. 상위 비공개는 하위 상태값을 바꾸지 않고
 * 조회 시점에 계산하므로, 상위를 다시 공개하면 하위 노출이 원래대로 돌아옵니다.
 * 관리자 경로(기존 메서드)는 상태와 관계없이 조회합니다.</p>
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
        CurriculumRepositoryImpl.class,
        UnitRepositoryImpl.class,
        LessonRepositoryImpl.class,
        CurriculumFinderService.class,
        UnitFinderService.class,
        LessonFinderService.class
})
@EnableJpaRepositories(basePackages = "com.maesamco.content.infrastructure.persistence")
@DisplayName("학습 콘텐츠 공개 상태 노출 규칙 통합 테스트 (#359)")
class ContentPublicationVisibilityIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

    static {
        postgres.start();
    }

    private static final PageRequest FIRST_PAGE = PageRequest.of(0, 50);

    @Autowired private EntityManager entityManager;
    @Autowired private CurriculumRepository curriculumRepository;
    @Autowired private UnitRepository unitRepository;
    @Autowired private LessonRepository lessonRepository;
    @Autowired private CurriculumFinderService curriculumFinder;
    @Autowired private UnitFinderService unitFinder;
    @Autowired private LessonFinderService lessonFinder;

    private CurriculumService curriculums;
    private UnitService units;
    private LessonService lessons;

    @BeforeEach
    void setUp() {
        curriculums = new CurriculumService(curriculumRepository, curriculumFinder);
        units = new UnitService(unitRepository, unitFinder, curriculumFinder);
        lessons = new LessonService(lessonRepository, lessonFinder, unitFinder,
                mock(ProblemQueryRepository.class), mock(ProblemTagRepository.class));
    }

    @Test
    @DisplayName("새로 만든 콘텐츠는 DRAFT라 학습자 조회에서는 숨겨지고, 관리자 조회에서는 보인다")
    void newContentIsDraftAndHiddenFromUsersButVisibleToAdmins() {
        // given
        Hierarchy ids = createHierarchy(false, false, false);

        // when & then: 학습자 조회는 모두 NOT_FOUND 또는 목록에서 제외
        assertError(ErrorCode.CURRICULUM_NOT_FOUND, () -> curriculums.getCurriculumForUser(ids.curriculumId()));
        assertThat(publishedCurriculumIds()).doesNotContain(ids.curriculumId());
        assertError(ErrorCode.CURRICULUM_NOT_FOUND, () -> units.getUnitForUser(ids.unitId()));
        assertError(ErrorCode.CURRICULUM_NOT_FOUND, () -> units.searchUnitsForUser(ids.curriculumId(), FIRST_PAGE));
        assertError(ErrorCode.CURRICULUM_NOT_FOUND, () -> lessons.getLessonForUser(ids.lessonId()));
        assertError(ErrorCode.CURRICULUM_NOT_FOUND, () -> lessons.searchLessonsForUser(ids.unitId(), FIRST_PAGE));
        assertError(ErrorCode.CURRICULUM_NOT_FOUND, () -> lessons.getLessonConceptsForUser(ids.lessonId()));

        // when & then: 관리자 경로(기존 메서드)는 상태와 관계없이 조회
        assertThat(curriculums.getCurriculum(ids.curriculumId()).getId()).isEqualTo(ids.curriculumId());
        assertThat(units.getUnit(ids.unitId()).getId()).isEqualTo(ids.unitId());
        assertThat(lessons.getLesson(ids.lessonId()).getId()).isEqualTo(ids.lessonId());
        assertThat(unitIds(units.searchUnits(ids.curriculumId(), FIRST_PAGE).content())).contains(ids.unitId());
        assertThat(lessonIds(lessons.searchLessons(ids.unitId(), FIRST_PAGE).content())).contains(ids.lessonId());
    }

    @Test
    @DisplayName("레슨·유닛·커리큘럼이 모두 PUBLISHED면 학습자가 단건·목록·개념을 조회할 수 있다")
    void fullyPublishedHierarchyIsVisibleToUsers() {
        // given
        Hierarchy ids = createHierarchy(true, true, true);

        // when & then
        assertThat(curriculums.getCurriculumForUser(ids.curriculumId()).getId()).isEqualTo(ids.curriculumId());
        assertThat(publishedCurriculumIds()).contains(ids.curriculumId());
        assertThat(units.getUnitForUser(ids.unitId()).getId()).isEqualTo(ids.unitId());
        assertThat(unitIds(units.searchUnitsForUser(ids.curriculumId(), FIRST_PAGE).content()))
                .containsExactly(ids.unitId());
        assertThat(lessons.getLessonForUser(ids.lessonId()).getId()).isEqualTo(ids.lessonId());
        assertThat(lessonIds(lessons.searchLessonsForUser(ids.unitId(), FIRST_PAGE).content()))
                .containsExactly(ids.lessonId());
        assertThat(lessons.getLessonConceptsForUser(ids.lessonId())).isEmpty();
    }

    @Test
    @DisplayName("상위가 공개여도 유닛이 DRAFT면 유닛은 UNIT_NOT_FOUND이고 목록에서 빠지며, 그 아래 레슨도 숨겨진다")
    void draftUnitUnderPublishedCurriculumIsHidden() {
        // given
        Hierarchy ids = createHierarchy(true, false, true);
        UUID publishedSibling = saveUnit(ids.curriculumId(), 2, true);

        // when & then
        assertError(ErrorCode.UNIT_NOT_FOUND, () -> units.getUnitForUser(ids.unitId()));
        assertThat(unitIds(units.searchUnitsForUser(ids.curriculumId(), FIRST_PAGE).content()))
                .containsExactly(publishedSibling);
        assertError(ErrorCode.UNIT_NOT_FOUND, () -> lessons.getLessonForUser(ids.lessonId()));
        assertError(ErrorCode.UNIT_NOT_FOUND, () -> lessons.searchLessonsForUser(ids.unitId(), FIRST_PAGE));
    }

    @Test
    @DisplayName("상위가 공개여도 레슨이 DRAFT면 레슨은 LESSON_NOT_FOUND이고 목록에서 빠진다")
    void draftLessonUnderPublishedUnitIsHidden() {
        // given
        Hierarchy ids = createHierarchy(true, true, false);
        UUID publishedSibling = saveLesson(ids.unitId(), 2, true);

        // when & then
        assertError(ErrorCode.LESSON_NOT_FOUND, () -> lessons.getLessonForUser(ids.lessonId()));
        assertError(ErrorCode.LESSON_NOT_FOUND, () -> lessons.getLessonConceptsForUser(ids.lessonId()));
        assertThat(lessonIds(lessons.searchLessonsForUser(ids.unitId(), FIRST_PAGE).content()))
                .containsExactly(publishedSibling);
    }

    @Test
    @DisplayName("커리큘럼을 비공개로 바꾸면 하위 상태값은 그대로인 채 학습자에게 숨겨지고, 다시 공개하면 원래대로 보인다")
    void unpublishingCurriculumHidesChildrenWithoutChangingTheirStatus() {
        // given
        Hierarchy ids = createHierarchy(true, true, true);

        // when: 커리큘럼 비공개
        changeStatus(Curriculum.class, ids.curriculumId(), false);

        // then: 하위는 숨겨지지만 상태값은 PUBLISHED 그대로
        assertError(ErrorCode.CURRICULUM_NOT_FOUND, () -> units.getUnitForUser(ids.unitId()));
        assertError(ErrorCode.CURRICULUM_NOT_FOUND, () -> lessons.getLessonForUser(ids.lessonId()));
        assertError(ErrorCode.CURRICULUM_NOT_FOUND, () -> units.searchUnitsForUser(ids.curriculumId(), FIRST_PAGE));
        assertThat(statusOf("p_units", "unit_id", ids.unitId())).isEqualTo(ContentStatus.PUBLISHED.name());
        assertThat(statusOf("p_lessons", "lesson_id", ids.lessonId())).isEqualTo(ContentStatus.PUBLISHED.name());

        // when: 다시 공개
        changeStatus(Curriculum.class, ids.curriculumId(), true);

        // then: 하위가 원래대로 보인다
        assertThat(units.getUnitForUser(ids.unitId()).getId()).isEqualTo(ids.unitId());
        assertThat(lessons.getLessonForUser(ids.lessonId()).getId()).isEqualTo(ids.lessonId());
    }

    @Test
    @DisplayName("유닛을 비공개로 바꾸면 레슨 상태값은 그대로인 채 학습자에게 숨겨지고, 다시 공개하면 원래대로 보인다")
    void unpublishingUnitHidesLessonsWithoutChangingTheirStatus() {
        // given
        Hierarchy ids = createHierarchy(true, true, true);

        // when: 유닛 비공개
        changeStatus(Unit.class, ids.unitId(), false);

        // then
        assertError(ErrorCode.UNIT_NOT_FOUND, () -> lessons.getLessonForUser(ids.lessonId()));
        assertError(ErrorCode.UNIT_NOT_FOUND, () -> lessons.searchLessonsForUser(ids.unitId(), FIRST_PAGE));
        assertThat(unitIds(units.searchUnitsForUser(ids.curriculumId(), FIRST_PAGE).content())).isEmpty();
        assertThat(statusOf("p_lessons", "lesson_id", ids.lessonId())).isEqualTo(ContentStatus.PUBLISHED.name());

        // when: 다시 공개
        changeStatus(Unit.class, ids.unitId(), true);

        // then
        assertThat(lessons.getLessonForUser(ids.lessonId()).getId()).isEqualTo(ids.lessonId());
    }

    @Test
    @DisplayName("공개 상태여도 상위가 삭제되면 학습자 조회는 삭제 정책과 같이 NOT_FOUND다")
    void deletedParentStillBlocksUserReadsEvenIfPublished() {
        // given
        Hierarchy ids = createHierarchy(true, true, true);
        curriculums.deleteCurriculum(ids.curriculumId(), UUID.randomUUID());
        entityManager.flush();
        entityManager.clear();

        // when & then
        assertError(ErrorCode.CURRICULUM_NOT_FOUND, () -> curriculums.getCurriculumForUser(ids.curriculumId()));
        assertThat(publishedCurriculumIds()).doesNotContain(ids.curriculumId());
        assertError(ErrorCode.CURRICULUM_NOT_FOUND, () -> units.getUnitForUser(ids.unitId()));
        assertError(ErrorCode.CURRICULUM_NOT_FOUND, () -> lessons.getLessonForUser(ids.lessonId()));
    }

    private Hierarchy createHierarchy(boolean curriculumPublished, boolean unitPublished, boolean lessonPublished) {
        Curriculum curriculum = Curriculum.create("Java", ProgrammingLanguage.JAVA);
        if (curriculumPublished) {
            curriculum.publish();
        }
        curriculum = curriculumRepository.save(curriculum);
        UUID unitId = saveUnit(curriculum.getId(), 1, unitPublished);
        UUID lessonId = saveLesson(unitId, 1, lessonPublished);
        return new Hierarchy(curriculum.getId(), unitId, lessonId);
    }

    private UUID saveUnit(UUID curriculumId, int displayOrder, boolean published) {
        Unit unit = Unit.create(curriculumId, "유닛" + displayOrder, ProgrammingLanguage.JAVA, displayOrder);
        if (published) {
            unit.publish();
        }
        UUID id = unitRepository.save(unit).getId();
        entityManager.flush();
        entityManager.clear();
        return id;
    }

    private UUID saveLesson(UUID unitId, int displayOrder, boolean published) {
        Lesson lesson = Lesson.create(unitId, "레슨" + displayOrder, "설명", "본문", ProgrammingLanguage.JAVA, displayOrder);
        if (published) {
            lesson.publish();
        }
        UUID id = lessonRepository.save(lesson).getId();
        entityManager.flush();
        entityManager.clear();
        return id;
    }

    private void changeStatus(Class<?> type, UUID id, boolean publish) {
        Object entity = entityManager.find(type, id);
        if (entity instanceof Curriculum curriculum) {
            if (publish) curriculum.publish(); else curriculum.unpublish();
        } else if (entity instanceof Unit unit) {
            if (publish) unit.publish(); else unit.unpublish();
        } else if (entity instanceof Lesson lesson) {
            if (publish) lesson.publish(); else lesson.unpublish();
        } else {
            throw new IllegalArgumentException("지원하지 않는 타입: " + type);
        }
        entityManager.flush();
        entityManager.clear();
    }

    private List<UUID> publishedCurriculumIds() {
        return curriculums.searchCurriculumsForUser(PageQuery.of(0, 50)).content().stream()
                .map(CurriculumResult::getId)
                .toList();
    }

    private static List<UUID> unitIds(List<UnitResponse> responses) {
        return responses.stream().map(UnitResponse::getId).toList();
    }

    private static List<UUID> lessonIds(List<LessonResponse> responses) {
        return responses.stream().map(LessonResponse::getId).toList();
    }

    private String statusOf(String table, String idColumn, UUID id) {
        // 테이블명은 테스트의 고정 문자열만 사용합니다.
        return (String) entityManager.createNativeQuery(
                        "select status from content_schema." + table + " where " + idColumn + " = :id")
                .setParameter("id", id)
                .getSingleResult();
    }

    private static void assertError(ErrorCode expected, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }

    private record Hierarchy(UUID curriculumId, UUID unitId, UUID lessonId) {}
}
